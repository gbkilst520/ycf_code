package com.ycf.gateway.core.util;

import com.ycf.common.enums.ResponseCode;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

public final class HttpResponseFactory {

    private HttpResponseFactory() {
    }

    public static FullHttpResponse text(HttpResponseStatus status, String body) {
        byte[] content = body == null ? new byte[0] : body.getBytes(CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(content)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.length);
        return response;
    }

    public static FullHttpResponse error(ResponseCode responseCode, String detailMessage) {
        HttpResponseStatus status = toHttpStatus(responseCode);
        String body = responseCode.getCode() + ": " + (detailMessage == null ? responseCode.getMessage() : detailMessage);
        return text(status, body);
    }

    private static HttpResponseStatus toHttpStatus(ResponseCode responseCode) {
        int code = responseCode.getCode();
        if (code >= 100 && code < 600) {
            return HttpResponseStatus.valueOf(code);
        }
        return HttpResponseStatus.INTERNAL_SERVER_ERROR;
    }
}
