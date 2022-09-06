package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.capacity.CapacityControlManager;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.sun.org.slf4j.internal.Logger;
import com.sun.org.slf4j.internal.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ControlManagerLoader {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ControlManagerLoader.class);
    
    List<TpsControlManager> tpsControlManagers;
    
    List<ConnectionControlManager> connectionControlManagers;
    
    List<CapacityControlManager> capacityControlManagers;
    
    private ControlManagerLoader() {
        
        tpsControlManagers = new ArrayList<>(NacosServiceLoader.load(TpsControlManager.class));
        
        connectionControlManagers = new ArrayList<>(NacosServiceLoader.load(ConnectionControlManager.class));
        
        capacityControlManagers = new ArrayList<>(NacosServiceLoader.load(CapacityControlManager.class));
    }
    
    public static final ControlManagerLoader getInstance() {
        return new ControlManagerLoader();
        
    }
}
