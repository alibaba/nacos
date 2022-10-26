package com.alibaba.nacos.plugin.control.connection;

public class TestBConnectionMetricsCollector implements ConnectionMetricsCollector{
    
    @Override
    public String getName() {
        return "testb";
    }
    
    @Override
    public int getTotalCount() {
        return 10;
    }
    
    @Override
    public int getCountForIp(String ip) {
        return 5;
    }
}
