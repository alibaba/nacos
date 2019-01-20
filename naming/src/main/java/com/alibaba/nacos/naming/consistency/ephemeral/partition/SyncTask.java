package com.alibaba.nacos.naming.consistency.ephemeral.partition;

import java.util.List;

/**
 * @author nkorange
 * @since 1.0.0
 */
public class SyncTask {

    private List<String> keys;

    private int retryCount;

    private long lastExecuteTime;

    private String targetServer;

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getLastExecuteTime() {
        return lastExecuteTime;
    }

    public void setLastExecuteTime(long lastExecuteTime) {
        this.lastExecuteTime = lastExecuteTime;
    }

    public String getTargetServer() {
        return targetServer;
    }

    public void setTargetServer(String targetServer) {
        this.targetServer = targetServer;
    }
}
