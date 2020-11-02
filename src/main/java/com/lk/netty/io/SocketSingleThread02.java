package com.lk.netty.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * Reactor单线程模型
 *
 * @author lk
 * @version 1.0
 * @date 2020/11/2 12:10
 */
public class SocketSingleThread02 {

  private ServerSocketChannel server = null;
  private Selector selector = null;

  public void initServer() throws IOException {
    server = ServerSocketChannel.open();
    server.configureBlocking(false);
    selector = Selector.open();
    // 注册到kernel中
    server.register(selector, SelectionKey.OP_ACCEPT);
  }

  public void start() throws IOException {
    initServer();
    System.out.println("Server 启动完成............");
    /** 单线程循环处理fd事件并处理Handler */
    while (true) {
      Set<SelectionKey> keys = selector.keys();
      System.out.println("kernel all fds: " + keys.size());
      /** select(): epoll_wait()阻塞获取已注册的fd */
      while (selector.select() > 0) {
        /** 阻塞获取所有有状态的fd */
        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
          SelectionKey key = iterator.next();
          // 避免死循环一直处理
          iterator.remove();
          if (key.isAcceptable()) {
            acceptHandler(key);
          } else if (key.isReadable()) {
            readHandler(key);
          } else if (key.isWritable()) {
            writeHandler(key);
          }
        }
      }
    }
  }

  /**
   * 处理Write事件
   *
   * @param key
   */
  private void writeHandler(SelectionKey key) {}

  /**
   * 处理Read事件
   *
   * @param key
   */
  private void readHandler(SelectionKey key) throws IOException {
    SocketChannel channel = (SocketChannel) key.channel();
    ByteBuffer buffer = (ByteBuffer) key.attachment();
    buffer.clear();
    int read = 0;
    while (true) {
      read = channel.read(buffer);
      if (read > 0) {
        buffer.flip();
        while (buffer.hasRemaining()) {
          System.out.println(buffer);
          channel.write(buffer);
        }
        buffer.clear();
      } else if (read == 0) {
        break;
      } else if (read < 0) {
        /** 客户端退出 */
        System.out.println("client close: " + channel.getRemoteAddress());
        break;
      }
    }
    // kernel移除当前的fd
    key.cancel();
    channel.close();
  }

  /**
   * 处理Accept事件
   *
   * @param key
   */
  private void acceptHandler(SelectionKey key) {
    try {
      ServerSocketChannel server = (ServerSocketChannel) key.channel();
      SocketChannel client = server.accept();
      client.configureBlocking(false);
      ByteBuffer buffer = ByteBuffer.allocateDirect(8192);

      client.register(selector, SelectionKey.OP_READ, buffer);
      System.out.println("register client: " + client.getRemoteAddress());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
