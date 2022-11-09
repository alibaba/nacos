package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;

public class ControlManagerFactory {
    
    static final ControlManagerFactory INSTANCE = new ControlManagerFactory();
    
    private TpsControlManager tpsControlManager;
    
    private ConnectionControlManager connectionControlManager;
    
    private ControlManagerFactory() {
        
        tpsControlManager = new TpsControlManager();
        
        connectionControlManager = new ConnectionControlManager();
        
    }
    
    public TpsControlManager getTpsControlManager() {
        return tpsControlManager;
    }
    
    public ConnectionControlManager getConnectionControlManager() {
        return connectionControlManager;
    }
    
    public static final ControlManagerFactory getInstance() {
        return INSTANCE;
    }
}
