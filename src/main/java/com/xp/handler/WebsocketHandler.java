package com.xp.handler;

import com.xp.config.Global;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.Map;

public class WebsocketHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handshaker;

    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {

        if (o instanceof FullHttpRequest) {
            handleHttpRequest(channelHandlerContext, (FullHttpRequest) o);
        } else if (o instanceof WebSocketFrame) {
            handlerWebsocketFrame(channelHandlerContext, (WebSocketFrame) o);
        }


    }

    public void handlerWebsocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), ((CloseWebSocketFrame) frame).retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        if (!(frame instanceof TextWebSocketFrame)) {
            return;
        }
        String text = ((TextWebSocketFrame) frame).text();

        TextWebSocketFrame tws = new TextWebSocketFrame(text);

        Global.group.writeAndFlush(tws);

    }

    public void sendHttpResponse(ChannelHandlerContext channelHandlerContext, FullHttpRequest req, FullHttpResponse res) {
        if (res.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        ChannelFuture channelFuture = channelHandlerContext.channel().writeAndFlush(res);
        if (!HttpHeaders.isKeepAlive(req) || res.getStatus().code() != 200) {
            channelFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    public void handleHttpRequest(ChannelHandlerContext channelHandlerContext, FullHttpRequest req) {
        if (!req.getDecoderResult().isSuccess() || !"websocket".equals(req.headers().get("Upgrade"))) {
            sendHttpResponse(channelHandlerContext, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        HttpMethod method = req.method();
        String uri = req.uri();
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
        Map<String, List<String>> parameters = queryStringDecoder.parameters();

        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory("ws://" + req.headers().get(HttpHeaders.Names.HOST) + uri, null, false);
        handshaker = factory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(channelHandlerContext.channel());
        } else {
            handshaker.handshake(channelHandlerContext.channel(), req);
        }

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Global.group.add(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Global.group.remove(ctx.channel());
    }

    /**
     * channel 通道 Read 读取 Complete 完成 在通道读取完成后会在这个方法里通知，对应可以做刷新操作
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
