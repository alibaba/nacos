package com.alibaba.nacos.plugin.control.connection;

public class TestAConnectionMetricsCollector implements ConnectionMetricsCollector{
    
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
