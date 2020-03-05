package com.alibaba.nacos.core.remoting;


import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionManager {

    private Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

    public void refreshConnection(String connectionId) {
        if (!connectionMap.containsKey(connectionId)) {
            return;
        }
        connectionMap.get(connectionId).setLastBeat(System.currentTimeMillis());
    }

}
