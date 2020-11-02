package com.lk.netty.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.Test;

import java.net.InetSocketAddress;

/**
 * @author lk
 * @version 1.0
 * @date 2020/11/1 21:19
 */
public class MyNetty {

  /**
   * netty客户端
   *
   * @throws Exception
   */
  @Test
  public void nettyClient() throws Exception {
    NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
    Bootstrap bootstrap = new Bootstrap();
    ChannelFuture connect =
        bootstrap
            .group(eventLoopGroup)
            .channel(NioSocketChannel.class)
            .handler(
                new ChannelInitializer<SocketChannel>() {
                  @Override
                  protected void initChannel(SocketChannel socketChannel) throws Exception {
                    ChannelPipeline pipeline = socketChannel.pipeline();
                    pipeline.addLast(new MyInHandler());
                  }
                })
            .connect(new InetSocketAddress("111.231.107.218", 9090));

    Channel client = connect.sync().channel();
    ByteBuf byteBuf = Unpooled.copiedBuffer("hello Server".getBytes());
    ChannelFuture channelFuture = client.writeAndFlush(byteBuf);
    channelFuture.sync();

    client.closeFuture().sync();
  }

  /**
   * netty客户端
   * @throws Exception
   */
  @Test
  public void nettyServer() throws Exception {
    NioEventLoopGroup group = new NioEventLoopGroup(1);
    ServerBootstrap serverBootstrap = new ServerBootstrap();
    ChannelFuture bind =
        serverBootstrap
            .group(group)
            .channel(NioServerSocketChannel.class)
            .childHandler(
                new ChannelInitializer<NioSocketChannel>() {
                  @Override
                  protected void initChannel(NioSocketChannel socketChannel)
                      throws Exception {
                    ChannelPipeline pipeline = socketChannel.pipeline();
                    pipeline.addLast(new MyInHandler());
                  }
                })
            .bind(new InetSocketAddress("127.0.0.1", 9090));

    bind.sync().channel().closeFuture().sync();
  }
}
