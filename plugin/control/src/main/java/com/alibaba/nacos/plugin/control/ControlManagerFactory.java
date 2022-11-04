package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControlManagerFactory {
    
    
    static ControlManagerFactory controlManagerFactory = new ControlManagerFactory();
    
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
        return controlManagerFactory;
    }
}
