package com.alibaba.nacos.core.remote;

public abstract class RuntimeConnectionEjector {
    
    /**
     * 4 times of client keep alive.
     */
    public static final long KEEP_ALIVE_TIME = 20000L;
    
    /**
     * current loader adjust count,only effective once,use to re balance.
     */
    private int loadClient = -1;
    
    String redirectAddress = null;
    
    protected ConnectionManager connectionManager;
    
    public RuntimeConnectionEjector() {
    }
    
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }
    
    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    public abstract void doEject();
    
    public int getLoadClient() {
        return loadClient;
    }
    
    public void setLoadClient(int loadClient) {
        this.loadClient = loadClient;
    }
    
    public String getRedirectAddress() {
        return redirectAddress;
    }
    
    public void setRedirectAddress(String redirectAddress) {
        this.redirectAddress = redirectAddress;
    }
    
    public abstract String getName();
}
