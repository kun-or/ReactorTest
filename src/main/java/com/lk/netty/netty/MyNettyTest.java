package com.lk.netty.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.net.InetSocketAddress;

/**
 * @author lk
 * @version 1.0
 * @date 2020/11/1 20:33
 */
public class MyNettyTest {

  /** 不使用Netty原生客户端 */
  @Test
  public void clientMode() throws InterruptedException {
    NioEventLoopGroup group = new NioEventLoopGroup(1);
    NioSocketChannel socketChannel = new NioSocketChannel();
    // 类似于注册
    group.register(socketChannel);
    // 响应式处理
    ChannelPipeline pipeline = socketChannel.pipeline();
    pipeline.addLast(new MyInHandler());

    // reactor异步特种
    ChannelFuture channelFuture = socketChannel.connect(new InetSocketAddress("127.0.0.1", 8080));
    ChannelFuture sync = channelFuture.sync();

    ByteBuf byteBuf = Unpooled.copiedBuffer("hello server......".getBytes());
    ChannelFuture send = socketChannel.writeAndFlush(byteBuf);
    send.sync();

    // 等待关闭
    sync.channel().closeFuture().sync();
  }

  /** 原生server端 */
  @Test
  public void serverModel() throws InterruptedException {
    NioEventLoopGroup loopGroup = new NioEventLoopGroup(1);
    NioServerSocketChannel serverSocketChannel = new NioServerSocketChannel();
    loopGroup.register(serverSocketChannel);
    // 响应式
    ChannelPipeline pipeline = serverSocketChannel.pipeline();
    pipeline.addLast(new MyAcceptHandler(loopGroup, new MyInHandler()));

    ChannelFuture channelFuture =
        serverSocketChannel.bind(new InetSocketAddress("127.0.0.1", 9090));

    channelFuture.sync().channel().closeFuture().sync();
    System.out.println("server close............");
  }
}

/**
 * 服务端实际只需要把接收的客户端和Handler注册到group
 */
class MyAcceptHandler extends ChannelInboundHandlerAdapter {
  private NioEventLoopGroup group;
  private ChannelHandler handler;

  public MyAcceptHandler(NioEventLoopGroup loopGroup, ChannelHandler myInHandler) {
    this.group = loopGroup;
    this.handler = myInHandler;
  }

  @Override
  public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
    System.out.println("server register.............");
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    SocketChannel channel = (SocketChannel) msg;
    ChannelPipeline pipeline = channel.pipeline();
    pipeline.addLast(handler);
    //注册
    group.register(channel);
  }
}

/** 手动创建Handler */
class MyInHandler extends ChannelInboundHandlerAdapter {
  @Override
  public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
    System.out.println("client register........");
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    // 读取服务端的数据
    ByteBuf byteBuf = (ByteBuf) msg;
    CharSequence sequence = byteBuf.getCharSequence(0, byteBuf.readableBytes(), CharsetUtil.UTF_8);
    System.out.print(sequence);
    // 返回给服务端
    ctx.writeAndFlush(byteBuf);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    System.out.println("client active...........");
  }
}
