package com.lk.netty.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * 多路复用单线程
 * 单线程Reactor模型
 * @author lk
 * @version 1.0
 * @date 2020/10/31 23:18
 */
public class SocketSingleThread {
  private ServerSocketChannel server = null;
  private Selector selector = null;
  private int port = 9090;

  /** 初始化并注册当前的服务到kernel */
  public void initServer() {
    try {
      server = ServerSocketChannel.open();
      // 非阻塞
      server.configureBlocking(false);
      server.bind(new InetSocketAddress(port));
      /** epoll: epoll_create 开辟空间 */
      selector = Selector.open();
      /** 注册事件 epoll:相当于epoll_ctl(fd3,ADD,fd4,EPOLLIN) */
      server.register(selector, SelectionKey.OP_ACCEPT);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /** 开始运行 */
  public void start() throws Exception {
    initServer();
    System.out.println("Server 服务开始启动................");

    try {
      while (true) {
        Set<SelectionKey> keys = selector.keys();
        System.out.println("所有的注册fd:" + keys.size());
        while (selector.select() > 0) {
          // 返回有状态的fd
          Set<SelectionKey> selectionKeys = selector.selectedKeys();
          Iterator<SelectionKey> iterator = selectionKeys.iterator();
          while (iterator.hasNext()) {
            SelectionKey next = iterator.next();
            // 获取到有状态的fd处理后就要remove,不然一直循环
            iterator.remove();

            // 接收一个新的连接
            if (next.isAcceptable()) {
              // 把新的连接注册到内核空间里 类似epoll_ctl
              acceptHandle(next);
            } else if (next.isReadable()) {
              /** 读状态 如果是这种单线程模型的话,单线程处理请求时,当读时阻塞住了 那么其他的fd事件到达时怎么处理 */
              //              readHandle(next);
              readHandler(next);
            } else if (next.isWritable()) {
              /*写状态   只要netstat -tlnp 中send-queue不为空
               * 每个连接都会有写的事件
               */
            }
          }
        }
      }
    } catch (Exception e) {
      throw e;
    }
  }

  /**
   * 处理新的连接
   *
   * @param selectionKey
   */
  public void acceptHandle(SelectionKey selectionKey) {
    try {
      ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
      /** accept接收客户端 */
      SocketChannel socketChannel = serverSocketChannel.accept();
      socketChannel.configureBlocking(false);

      ByteBuffer byteBuffer = ByteBuffer.allocate(8192);
      /**
       * 把新的连接fd(假设是fd7)放入到kernel空间中 select/poll：jvm里开辟一个数组 epoll: epoll_ctl(fd3,ADD,fd7,EPOLLIN)
       */
      socketChannel.register(selector, SelectionKey.OP_READ, byteBuffer);
      System.out.println("新的客户端: " + socketChannel.getRemoteAddress());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void readHandler(SelectionKey key) {
    SocketChannel client = (SocketChannel) key.channel();
    ByteBuffer buffer = (ByteBuffer) key.attachment();
    buffer.clear();
    int read = 0;
    try {
      while (true) {
        read = client.read(buffer);
        if (read > 0) {
          buffer.flip();
          while (buffer.hasRemaining()) {
            client.write(buffer);
          }
          buffer.clear();
        } else if (read == 0) {
          break;
        } else {
          client.close();
          break;
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * 处理新的连接为read的
   *
   * @param selectionKey
   */
  public void readHandle(SelectionKey selectionKey) throws IOException {
    SocketChannel channel = (SocketChannel) selectionKey.channel();
    ByteBuffer buffer = (ByteBuffer) selectionKey.attachment();
    buffer.clear();
    int read = 0;
    while (true) {
      read = channel.read(buffer);
      if (read > 0) {
        buffer.flip();
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        buffer.clear();
      } else if (read == 0) {
        break;
      } else {
        channel.close();
      }
    }
  }

  public static void main(String[] args) throws Exception {
    SocketSingleThread singleThread = new SocketSingleThread();
    singleThread.start();
  }
}
