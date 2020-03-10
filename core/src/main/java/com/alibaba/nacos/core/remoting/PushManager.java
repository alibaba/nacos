package com.alibaba.nacos.core.remoting;


import com.alibaba.nacos.core.remoting.grpc.impl.StreamServiceGrpcImpl;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Component
public class PushManager {

    @Autowired
    private ConnectionManager connectionManager;

    @Autowired
    private StreamServiceGrpcImpl connectionServiceGrpc;

    private static ScheduledExecutorService pushExecutor =
        new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);

                t.setDaemon(true);
                t.setName("com.alibaba.nacos.core.pusher");

                return t;
            }
        });

    @PostConstruct
    public void init() {

        pushExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                pushChange("nacos-grpc-naming-client-id", "test1", "xxxxxxxxx".getBytes());
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    public boolean pushChange(String clientId, String dataId, byte[] data) {
        try {
            getPushService(clientId).push(clientId, dataId, data);
        } catch (Exception e) {
            Loggers.MAIN.error("[PUSH] push failed, client: {}, dataId: {}", clientId, dataId, e);
            return false;
        }
        return true;
    }

    public PushService getPushService(String clientId) {
        Connection connection = connectionManager.getConnection(clientId);
        switch (connection.getConnectionType()) {
            case GRPC:
            default:
                return connectionServiceGrpc;
        }
    }
}
