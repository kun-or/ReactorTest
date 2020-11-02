package com.lk.netty.io;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 多线程处理fd的事件 但是对于select还是线性的处理(只有一个selector) 相当于在kernel中只开辟一个单独的空间存储fd
 * Reactor多线程模型,Handler事件处理由多线程处理
 * @author lk
 * @version 1.0
 * @date 2020/11/1 13:33
 */
public class SocketMultithreading2RW {

  /** 创建全局的线程池 */
  private ExecutorService poll;

  private ServerSocketChannel server = null;
  private Selector selector = null;
  private int port = 8081;

  /**
   * 初始化Server
   *
   * @throws Exception
   */
  public void initServer() throws Exception {
    server = ServerSocketChannel.open();
    selector = Selector.open();
    // 非阻塞
    server.configureBlocking(false);
    server.bind(new InetSocketAddress(port));
    server.register(selector, SelectionKey.OP_ACCEPT);
    /** 创建线程池 */
    poll =
        new ThreadPoolExecutor(
            5,
            10,
            0,
            TimeUnit.MINUTES,
            new ArrayBlockingQueue<>(100),
            new ThreadFactory() {
              @Override
              public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "Thread " + r.hashCode());
                return thread;
              }
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  public void startServer() throws Exception {
    initServer();
    System.out.println("服务启动............");
    while (true) {
      Set<SelectionKey> keys = selector.keys();
      System.out.println("All fds: " + keys.size());

      /** 相当于调用 epoll:epoll_wait select/poll: select内核返回所有的fd文件 */
      while (selector.select() > 0) {
        /** 有事件状态的fd */
        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
          SelectionKey key = iterator.next();
          // 避免循环不停止
          iterator.remove();
          if (key.isAcceptable()) {
            accept(key);
          } else if (key.isReadable()) {
//            key.cancel();
            read(key);
          } else if (key.isWritable()) {
            write(key);
          }
        }
      }
    }
  }

  public void write(SelectionKey key) {
    poll.execute(
        () -> {
          System.out.println("write............");
          SocketChannel client = (SocketChannel) key.channel();
          ByteBuffer buffer = (ByteBuffer) key.attachment();
          buffer.flip();
          while (buffer.hasRemaining()) {
            try {
              client.write(buffer);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
          try {
            TimeUnit.SECONDS.sleep(2);
            buffer.clear();
            //            /** 删除kernel中的fd,不然重复调起 */
//            key.cancel();
            //            client.shutdownOutput();
            client.close();
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
  }
  /**
   * 处理读事件,采用创建线程的处理方式,避免整个selector处理线性的处理方式
   *
   * @param key
   */
  public void read(SelectionKey key) {
    poll.execute(
        () -> {
          System.out.println("read............");
          SocketChannel client = (SocketChannel) key.channel();
          ByteBuffer buffer = (ByteBuffer) key.attachment();
          buffer.clear();
          int read = 0;
          try {
            while (true) {
              read = client.read(buffer);
              if (read > 0) {
                //                key.interestOps(SelectionKey.OP_READ);
                client.register(key.selector(), SelectionKey.OP_WRITE, buffer);
              } else if (read == 0) {
                break;
              } else {
                client.close();
                break;
              }
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
  }
  /**
   * 处理客户端新的连接
   *
   * @param key
   */
  public void accept(SelectionKey key) {
    try {
      ServerSocketChannel server = (ServerSocketChannel) key.channel();
      SocketChannel client = server.accept();
      client.configureBlocking(false);
      ByteBuffer buffer = ByteBuffer.allocate(8192);
      /** 注册fd */
      client.register(selector, SelectionKey.OP_READ, buffer);
      System.out.println("接收新的客户端: " + client.getRemoteAddress());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) throws Exception {
    SocketMultithreading2RW multithreading2RW = new SocketMultithreading2RW();
    multithreading2RW.startServer();
  }
}
