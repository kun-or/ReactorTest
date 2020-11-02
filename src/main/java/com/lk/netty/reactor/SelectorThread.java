package com.lk.netty.reactor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 作为Worker处理fd事件的线程
 *
 * @author lk
 * @version 1.0
 * @date 2020/11/1 17:37
 */
public class SelectorThread implements Runnable {

  Selector selector = null;
  /** 为了获取到group中的works线程,进行fd分发注册 */
  private SelectorThreadGroup selectorThreadGroup;

  /**
   * group中获取到的Channel会add到进去,在线程运行的时间,因为while(true){select()}方法会阻塞
   * 在group调用selector的weakup使select立刻返回,在每次处理完有状态的时间后,把队列中新增加的
   */
  LinkedBlockingQueue<Channel> queue = new LinkedBlockingQueue<Channel>();

  public SelectorThread(SelectorThreadGroup group) {
    try {
      selector = Selector.open();
      this.selectorThreadGroup = group;
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void run() {
    while (true) {
      try {
        // 获取所有的fd,select()方法会阻塞
        int numFds = selector.select();
        while (numFds > 0) {
          Set<SelectionKey> keys = selector.selectedKeys();
          Iterator<SelectionKey> iterator = keys.iterator();
          while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            // 删除,避免死循环
            iterator.remove();
            if (key.isAcceptable()) {
              acceptHandle(key);
            } else if (key.isReadable()) {
              readHandle(key);
            } else if (key.isWritable()) {

            }
          }
        }
        // 处理其他的task，比如Server刚启动时,select()会一直处于阻塞状态,group调用weakup()使select()立刻返回
        if (!queue.isEmpty()) {
          Channel channel = queue.take();
          if (channel instanceof ServerSocketChannel) {
            ((ServerSocketChannel) channel).register(selector, SelectionKey.OP_ACCEPT);
            System.out.println(Thread.currentThread().getName() + ": register listen........");
          } else if (channel instanceof SocketChannel) {
            SocketChannel socketChannel = (SocketChannel) channel;
            ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
            socketChannel.register(selector, SelectionKey.OP_READ, buffer);
            System.out.println(
                Thread.currentThread().getName()
                    + "  :register client:"
                    + socketChannel.getRemoteAddress());
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * 处理read事件
   *
   * @param key
   */
  private void readHandle(SelectionKey key) {
    System.out.println("readHandler.........");
    try {
      SocketChannel channel = (SocketChannel) key.channel();
      ByteBuffer buffer = (ByteBuffer) key.attachment();
      buffer.clear();
      while (true) {
        int read = channel.read(buffer);
        if (read > 0) {
          buffer.flip();
          while (buffer.hasRemaining()) {
            channel.write(buffer);
          }
          buffer.clear();
        } else if (read == 0) {
          break;
        } else if (read < 0) {
          /** read<0时可能时clinet断开连接 */
          System.out.println("客户端: " + channel.getRemoteAddress() + ":断开连接!");
          // kernel注册表中删除此fd
          key.cancel();
          break;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * 处理接收事件
   *
   * @param key
   */
  private void acceptHandle(SelectionKey key) throws IOException {
    System.out.println("acceptHandle.................");
    ServerSocketChannel client = (ServerSocketChannel) key.channel();
    SocketChannel socketChannel = client.accept();
    // 非阻塞
    socketChannel.configureBlocking(false);
    /** 需要选择出哪一个selector进行注册 */
    selectorThreadGroup.registerSelector(socketChannel);
  }
}
