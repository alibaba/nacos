package com.alibaba.nacos.common;

import java.util.Map;
import java.util.concurrent.Executors;

import com.alibaba.nacos.common.executor.ThreadPoolManager;
import org.junit.Test;

public class ThreadPoolManagerTest {

    private static final String biz = "nacos";
    private static final String resourceName = "naming";

    @Test
    public void test_thread_pool_manager() {
        ThreadPoolManager manager = ThreadPoolManager.getInstance();
        manager.register(biz, resourceName, Executors.newFixedThreadPool(1));

        Map<String, Map<String, Map<String, Object>>> stringMapMap = manager.getThreadPoolsInfo();
        System.out.println(stringMapMap);

    }

}