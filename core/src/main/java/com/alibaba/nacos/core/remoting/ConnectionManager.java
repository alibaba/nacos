package com.alibaba.nacos.core.remoting;


import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionManager {

    private Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

    private Map<String, List<ConnectionEventListener>> listenerMap = new ConcurrentHashMap<>();

    public void refreshConnection(String connectionId) {
        if (!connectionMap.containsKey(connectionId)) {
            return;
        }
        connectionMap.get(connectionId).setLastBeat(System.currentTimeMillis());
    }

    public boolean hasConnection(String connectionId) {
        return connectionMap.containsKey(connectionId);
    }

    public boolean putIfAbsent(Connection connection) {
        connectionMap.putIfAbsent(connection.getConnectionId(), connection);
        return true;
    }

    public void listen(String connectionId, ConnectionEventListener listener) {
        if (!listenerMap.containsKey(connectionId)) {
            listenerMap.putIfAbsent(connectionId, new ArrayList<>());
        }
        listenerMap.get(connectionId).add(listener);
    }

    public Connection getConnection(String connectionId) {
        return connectionMap.get(connectionId);
    }

}
