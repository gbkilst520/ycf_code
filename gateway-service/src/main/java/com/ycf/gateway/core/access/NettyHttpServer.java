package com.ycf.gateway.core.access;

import com.ycf.common.constants.GatewayConstants;
import com.ycf.common.enums.ResponseCode;
import com.ycf.gateway.core.filter.GatewayFilterChain;
import com.ycf.gateway.core.ops.GatewayControlPlane;
import com.ycf.gateway.core.ops.GatewayMetricsCollector;
import com.ycf.gateway.core.util.HttpResponseFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NettyHttpServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NettyHttpServer.class);

    private final int port;
    private final GatewayFilterChain filterChain;
    private final GatewayControlPlane controlPlane;
    private final GatewayMetricsCollector metricsCollector;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyHttpServer(int port,
                           GatewayFilterChain filterChain,
                           GatewayControlPlane controlPlane,
                           GatewayMetricsCollector metricsCollector) {
        this.port = port;
        this.filterChain = filterChain;
        this.controlPlane = controlPlane;
        this.metricsCollector = metricsCollector;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(10 * 1024 * 1024))
                                .addLast(new GatewayRequestHandler(filterChain, controlPlane, metricsCollector));
                    }
                });

        ChannelFuture bindFuture = bootstrap.bind(port).sync();
        serverChannel = bindFuture.channel();
        log.info("gateway netty server started at port {}", port);
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

    @Override
    public void close() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    private static class GatewayRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private final GatewayFilterChain filterChain;
        private final GatewayControlPlane controlPlane;
        private final GatewayMetricsCollector metricsCollector;

        GatewayRequestHandler(GatewayFilterChain filterChain,
                              GatewayControlPlane controlPlane,
                              GatewayMetricsCollector metricsCollector) {
            this.filterChain = filterChain;
            this.controlPlane = controlPlane;
            this.metricsCollector = metricsCollector;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            boolean keepAlive = HttpUtil.isKeepAlive(request);
            FullHttpRequest retainedRequest = request.retainedDuplicate();

            String clientIp = resolveClientIp(ctx, request);
            String requestId = resolveRequestId(retainedRequest);
            retainedRequest.headers().set(GatewayConstants.HEADER_X_REQUEST_ID, requestId);
            GatewayContext gatewayContext = new GatewayContext(retainedRequest, clientIp, requestId);

            boolean internalEndpoint = controlPlane != null && controlPlane.isControlPath(gatewayContext.getPath());
            if (internalEndpoint) {
                try {
                    FullHttpResponse response = controlPlane.handle(gatewayContext);
                    completeRequest(ctx, keepAlive, gatewayContext, response, null, true);
                } catch (Exception ex) {
                    completeRequest(ctx, keepAlive, gatewayContext, null, ex, true);
                } finally {
                    retainedRequest.release();
                }
                return;
            }

            CompletableFuture<FullHttpResponse> responseFuture;
            try {
                responseFuture = filterChain.filter(gatewayContext);
            } catch (Exception ex) {
                completeRequest(ctx, keepAlive, gatewayContext, null, ex, false);
                retainedRequest.release();
                return;
            }

            responseFuture.whenComplete((response, throwable) -> {
                try {
                    completeRequest(ctx, keepAlive, gatewayContext, response, throwable, false);
                } finally {
                    retainedRequest.release();
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            writeResponse(ctx, false, HttpResponseFactory.error(ResponseCode.BAD_REQUEST, "malformed request"));
        }

        private void completeRequest(ChannelHandlerContext ctx,
                                     boolean keepAlive,
                                     GatewayContext context,
                                     FullHttpResponse response,
                                     Throwable throwable,
                                     boolean internalEndpoint) {
            FullHttpResponse finalResponse = response;
            if (throwable != null || finalResponse == null) {
                finalResponse = HttpResponseFactory.error(ResponseCode.INTERNAL_ERROR, "request process failed");
            }
            finalResponse.headers().set(GatewayConstants.HEADER_X_REQUEST_ID, context.getRequestId());
            if (!HttpUtil.isContentLengthSet(finalResponse)) {
                HttpUtil.setContentLength(finalResponse, finalResponse.content().readableBytes());
            }

            if (metricsCollector != null) {
                long durationNanos = System.nanoTime() - context.getRequestStartNanos();
                metricsCollector.record(context, finalResponse.status().code(), durationNanos, internalEndpoint);
            }
            writeResponse(ctx, keepAlive, finalResponse);
        }

        private void writeResponse(ChannelHandlerContext ctx, boolean keepAlive, FullHttpResponse response) {
            if (keepAlive) {
                HttpUtil.setKeepAlive(response, true);
            }
            ChannelFuture writeFuture = ctx.writeAndFlush(response);
            if (!keepAlive) {
                writeFuture.addListener(ChannelFutureListener.CLOSE);
            }
        }

        private String resolveClientIp(ChannelHandlerContext ctx, FullHttpRequest request) {
            String xff = request.headers().get(GatewayConstants.HEADER_X_FORWARDED_FOR);
            if (xff != null && !xff.isBlank()) {
                int commaIndex = xff.indexOf(',');
                return (commaIndex > 0 ? xff.substring(0, commaIndex) : xff).trim();
            }

            SocketAddress remoteAddress = ctx.channel().remoteAddress();
            if (remoteAddress instanceof InetSocketAddress inetSocketAddress) {
                if (inetSocketAddress.getAddress() != null) {
                    return inetSocketAddress.getAddress().getHostAddress();
                }
                return inetSocketAddress.getHostString();
            }
            return "unknown";
        }

        private String resolveRequestId(FullHttpRequest request) {
            String requestId = request.headers().get(GatewayConstants.HEADER_X_REQUEST_ID);
            if (requestId != null && !requestId.isBlank()) {
                return requestId;
            }
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
