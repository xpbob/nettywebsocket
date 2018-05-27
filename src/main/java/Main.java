import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

public class Main {

    public static void main(String[] args){
        EventLoopGroup boss =new NioEventLoopGroup();
        EventLoopGroup worker = new NioEventLoopGroup();

        ServerBootstrap server = new ServerBootstrap();

        server.group(boss,worker).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            protected void initChannel(SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                // HttpServerCodec：将请求和应答消息解码为HTTP消息
                pipeline.addLast("http-codec",new HttpServerCodec());
                // HttpObjectAggregator：将HTTP消息的多个部分合成一条完整的HTTP消息
                pipeline.addLast("aggregator",new HttpObjectAggregator(65536));
                // ChunkedWriteHandler：向客户端发送HTML5文件
                pipeline.addLast("http-chunked",new ChunkedWriteHandler());

            }
        });

        try {
            Channel channel = server.bind(6161).sync().channel();
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }


    }

}
