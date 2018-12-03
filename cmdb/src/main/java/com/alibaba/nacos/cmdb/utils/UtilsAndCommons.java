package com.alibaba.nacos.cmdb.utils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class UtilsAndCommons {

    public static final String NACOS_SERVER_VERSION = "/v1";

    public static final String NACOS_CMDB_CONTEXT = NACOS_SERVER_VERSION + "/cmdb";

    public static final ScheduledExecutorService GLOBAL_EXECUTOR;

    static {

        GLOBAL_EXECUTOR
                = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("nacos.cmdb.global.executor");
                t.setDaemon(true);
                return t;
            }
        });
    }
}
