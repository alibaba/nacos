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
        
        List<TpsControlManager> tpsControlManagers = new ArrayList<>(NacosServiceLoader.load(TpsControlManager.class));
        
        List<ConnectionControlManager> connectionControlManagers = new ArrayList<>(
                NacosServiceLoader.load(ConnectionControlManager.class));
        
        List<CapacityControlManager> capacityControlManagers = new ArrayList<>(
                NacosServiceLoader.load(CapacityControlManager.class));
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
