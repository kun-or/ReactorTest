package com.lk.netty.io;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * R/W事件区分开处理,但是Write事件受到send-queue的影响，为0时会重复被调起
 *
 * @author lk
 * @version 1.0
 * @date 2020/11/1 0:58
 */
public class SocketSingleThreadWrite {

  private ServerSocketChannel serverSocketChannel = null;
  private Selector selector = null;
  private int port = 8080;

  public void initServer() {
    try {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.configureBlocking(false);
      serverSocketChannel.bind(new InetSocketAddress(port));
      selector = Selector.open();
      serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void start() {
    try {
      initServer();
      System.out.println("Server服务启动.....................");
      while (selector.select() > 0) {
        // 返回有状态的fd
        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
          SelectionKey key = iterator.next();
          // 避免死循环
          iterator.remove();
          if (key.isAcceptable()) {
            acceptHandle(key);
          } else if (key.isReadable()) {
            readHandle(key);
          } else if (key.isWritable()) {
            writeHandle(key);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * 处理写的事件
   *
   * @param key
   */
  public void writeHandle(SelectionKey key) {
    System.out.println("write Handle..............");
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
      /** epoll: epoll_ctl(fd3,del,fd7,EPOLLIN)删除此个的wirte FD文件描述符 write受到send-queue值得影响,会重复调起 */
      key.cancel();
      client.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * 处理读事件并注册写的事件
   *
   * @param key
   */
  public void readHandle(SelectionKey key) {
    System.out.println("read Handle.................");
    SocketChannel client = (SocketChannel) key.channel();
    ByteBuffer buffer = (ByteBuffer) key.attachment();
    buffer.clear();
    int read = 0;
    try {
      while (true) {
        read = client.read(buffer);
        if (read > 0) {
          client.register(key.selector(), SelectionKey.OP_WRITE, buffer);
        } else if (read == 0) {
          break;
        } else {
          client.close();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * 接收新的连接并注册到内核中
   *
   * @param key
   */
  public void acceptHandle(SelectionKey key) {
    System.out.println("accept Handle.................");
    try {
      ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
      SocketChannel client = serverSocketChannel.accept();
      client.configureBlocking(false);
      ByteBuffer byteBuffer = ByteBuffer.allocate(8192);

      // 注册fd
      client.register(selector, SelectionKey.OP_READ, byteBuffer);
      System.out.println("新的连接客户端: " + client.getRemoteAddress());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    SocketSingleThreadWrite socketSingleThreadWrite = new SocketSingleThreadWrite();
    socketSingleThreadWrite.start();
  }
}
