package com.alibaba.nacos.core.remoting;

public class Connection {

    private ConnectionType connectionType;

    private String connectionId;

    private long lastBeat;

    public Connection(String connectionId, ConnectionType connectionType) {
        this.connectionId = connectionId;
        this.connectionType = connectionType;
        this.lastBeat = System.currentTimeMillis();
    }

    public String getConnectionId() {
        return connectionId;
    }

    public long getLastBeat() {
        return lastBeat;
    }

    public void setLastBeat(long lastBeat) {
        this.lastBeat = lastBeat;
    }

    public ConnectionType getConnectionType() {
        return connectionType;
    }
}
