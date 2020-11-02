package com.lk.netty.reactor;

import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一管理works线程并负责分发fd的注册功能
 *
 * @author lk
 * @version 1.0
 * @date 2020/11/1 17:36
 */
public class SelectorThreadGroup {

  /** 为了轮询取模 */
  AtomicInteger atomicInteger = new AtomicInteger(0);

  private ServerSocketChannel server = null;
  /** 负责创建并维护work线程的工作 */
  private SelectorThread[] selectorThreads = null;

  public SelectorThreadGroup(int workThreadSize) {
    selectorThreads = new SelectorThread[workThreadSize];
    for (int i = 0; i < workThreadSize; i++) {
      selectorThreads[i] = new SelectorThread(this);
      // 启动所有的works
      new Thread(selectorThreads[i]).start();
    }
  }

  /**
   * 绑定server并注册到selector中
   *
   * @param port
   */
  public void bind(int port) {
    try {
      server = ServerSocketChannel.open();
      server.bind(new InetSocketAddress(port));
      // 非阻塞
      server.configureBlocking(false);
      // 选择selctor进行注册
      registerSelector(server);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * 负责接收的Channel分发到不同的Selector注册
   *
   * @param server
   */
  public void registerSelector(Channel server) {
    SelectorThread selectorThread = next();
    selectorThread.queue.add(server);
    /**
     * 打断work中的select()阻塞完成fd的注册
     */
    selectorThread.selector.wakeup();
  }

  /** 轮询获取Selector */
  private SelectorThread next() {
    int index = atomicInteger.incrementAndGet() % selectorThreads.length;
    return selectorThreads[index];
  }
}
