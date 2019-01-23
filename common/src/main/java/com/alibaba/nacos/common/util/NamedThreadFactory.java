package com.alibaba.nacos.common.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义线程工厂
 *
 * @author xiaosuda
 * @date 2019/1/23
 */
public class NamedThreadFactory implements ThreadFactory {
    /**
     * 线程池序号
     */
    private static final AtomicInteger POOL_SEQ = new AtomicInteger(1);

    /**
     * 线程池中线程序号
     */
    private final AtomicInteger THREAD_SEQ = new AtomicInteger(1);

    /**
     * 线程前缀
     */
    private final String threadPrefix;

    /**
     * 是否为守护线程/用户线程
     */
    private final boolean daemon;

    /**
     * 线程组
     */
    private final ThreadGroup threadGroup;

    public NamedThreadFactory() {
        this("pool-" + POOL_SEQ.getAndIncrement(), false);
    }

    public NamedThreadFactory(String prefix) {
        this(prefix, false);
    }

    public NamedThreadFactory(String prefix, boolean daemon) {
        threadPrefix = prefix + "-thread-";
        this.daemon = daemon;
        SecurityManager s = System.getSecurityManager();
        threadGroup = (s == null) ? Thread.currentThread().getThreadGroup() : s.getThreadGroup();
    }

    @Override
    public Thread newThread(Runnable runnable) {
        String name = threadPrefix + THREAD_SEQ.getAndIncrement();
        Thread ret = new Thread(threadGroup, runnable, name, 0);
        ret.setDaemon(daemon);
        return ret;
    }

    public ThreadGroup getThreadGroup() {
        return threadGroup;
    }
}
