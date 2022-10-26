package com.alibaba.nacos.plugin.control.connection;

/**
 * connection count metrics collector.
 * @author shiyiyue
 */
public interface ConnectionMetricsCollector {
    
    /**
     * get collector name.
     *
     * @return
     */
    String getName();
    
    /**
     * @return
     */
    int getTotalCount();
    
    /**
     * @param ip
     * @return
     */
    int getCountForIp(String ip);
}
