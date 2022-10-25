package com.alibaba.nacos.plugin.control.connection;

public interface ConnectionMetricsCollector {
    
    /**
     *
     * @return
     */
    int getTotalCount();
    
    /**
     *
     * @param ip
     * @return
     */
    int getCountForIp(String ip);
}
