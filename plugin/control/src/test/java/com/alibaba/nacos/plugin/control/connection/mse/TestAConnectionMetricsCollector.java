package com.alibaba.nacos.plugin.control.connection.mse;

import com.alibaba.nacos.plugin.control.connection.ConnectionMetricsCollector;

public class TestAConnectionMetricsCollector implements ConnectionMetricsCollector {
    
    @Override
    public String getName() {
        return "testa";
    }
    
    @Override
    public int getTotalCount() {
        return 20;
    }
    
    @Override
    public int getCountForIp(String ip) {
        return 10;
    }
}
