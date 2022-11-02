package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.capacity.CapacityControlManager;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ControlManagerFactory {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ControlManagerFactory.class);
    
    static ControlManagerFactory controlManagerFactory = new ControlManagerFactory();
    
    private TpsControlManager tpsControlManager;
    
    private ConnectionControlManager connectionControlManager;
    
    private CapacityControlManager capacityControlManager;
    
    private ControlManagerFactory() {
        
        tpsControlManager = new TpsControlManager();
        
        connectionControlManager = new ConnectionControlManager();
        
        capacityControlManager = new CapacityControlManager();
    }
    
    public TpsControlManager getTpsControlManager() {
        return tpsControlManager;
    }
    
    public ConnectionControlManager getConnectionControlManager() {
        return connectionControlManager;
    }
    
    private CapacityControlManager getCapacityControlManager() {
        return capacityControlManager;
    }
    
    public static final ControlManagerFactory getInstance() {
        return controlManagerFactory;
    }
}
