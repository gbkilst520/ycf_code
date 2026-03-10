package com.ycf.gateway.core.forward;

import com.ycf.common.enums.ResponseCode;
import com.ycf.gateway.config.model.RouteDefinition;
import com.ycf.gateway.core.access.GatewayContext;
import com.ycf.gateway.core.util.HttpResponseFactory;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.Response;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class NettyHttpClient implements AutoCloseable {

    private final AsyncHttpClient asyncHttpClient;

    public NettyHttpClient() {
        this.asyncHttpClient = new DefaultAsyncHttpClient();
    }

    public CompletableFuture<FullHttpResponse> forward(GatewayContext gatewayContext) {
        RouteDefinition routeDefinition = gatewayContext.getRouteDefinition();
        String targetBaseUrl = gatewayContext.getTargetBaseUrl();
        if (routeDefinition == null || targetBaseUrl == null || targetBaseUrl.isBlank()) {
            return CompletableFuture.completedFuture(
                    HttpResponseFactory.error(ResponseCode.SERVICE_UNAVAILABLE, "target service unavailable")
            );
        }

        String rewrittenPath = routeDefinition.rewritePath(gatewayContext.getPath());
        String targetUrl = buildTargetUrl(targetBaseUrl, rewrittenPath, gatewayContext.getQuery());

        FullHttpRequest request = gatewayContext.getRequest();
        BoundRequestBuilder requestBuilder = asyncHttpClient.prepare(request.method().name(), targetUrl);
        copyHeaders(request, requestBuilder);

        byte[] requestBody = ByteBufUtil.getBytes(request.content());
        if (requestBody.length > 0) {
            requestBuilder.setBody(requestBody);
        }

        CompletableFuture<FullHttpResponse> responseFuture = new CompletableFuture<>();
        requestBuilder.execute(new AsyncCompletionHandler<>() {
            @Override
            public Response onCompleted(Response response) {
                responseFuture.complete(toNettyResponse(response));
                return response;
            }

            @Override
            public void onThrowable(Throwable t) {
                responseFuture.completeExceptionally(t);
            }
        });
        return responseFuture;
    }

    private void copyHeaders(FullHttpRequest request, BoundRequestBuilder requestBuilder) {
        for (Map.Entry<String, String> header : request.headers()) {
            String headerName = header.getKey();
            if (HttpHeaderNames.HOST.contentEqualsIgnoreCase(headerName)
                    || HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(headerName)
                    || HttpHeaderNames.TRANSFER_ENCODING.contentEqualsIgnoreCase(headerName)) {
                continue;
            }
            requestBuilder.addHeader(headerName, header.getValue());
        }
    }

    private FullHttpResponse toNettyResponse(Response response) {
        byte[] responseBody = response.getResponseBodyAsBytes();
        FullHttpResponse nettyResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(response.getStatusCode()),
                Unpooled.wrappedBuffer(responseBody)
        );

        for (Map.Entry<String, String> header : response.getHeaders()) {
            String headerName = header.getKey();
            if (HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(headerName)
                    || HttpHeaderNames.TRANSFER_ENCODING.contentEqualsIgnoreCase(headerName)) {
                continue;
            }
            nettyResponse.headers().add(headerName, header.getValue());
        }
        nettyResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, responseBody.length);
        return nettyResponse;
    }

    private String buildTargetUrl(String baseUrl, String path, String query) {
        StringBuilder builder = new StringBuilder(baseUrl);
        if (path != null && !path.isBlank()) {
            if (!path.startsWith("/")) {
                builder.append('/');
            }
            builder.append(path);
        }
        if (query != null && !query.isBlank()) {
            builder.append('?').append(query);
        }
        return builder.toString();
    }

    @Override
    public void close() throws Exception {
        asyncHttpClient.close();
    }
}
