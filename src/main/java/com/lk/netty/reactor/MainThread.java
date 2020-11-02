package com.lk.netty.reactor;

/**
 * Reactor多Seletor实现,更高效的利用CPU资源
 * accept接收和fd事件的处理完全分开
 * @author lk
 * @version 1.0
 * @date 2020/11/1 17:36
 */
public class MainThread {

  public static void main(String[] args) {

    /**
     * 创建IO Threads(一个或者多个)
     */
    SelectorThreadGroup selectorThreadGroup = new SelectorThreadGroup(3);

    selectorThreadGroup.bind(9999);
  }
}
