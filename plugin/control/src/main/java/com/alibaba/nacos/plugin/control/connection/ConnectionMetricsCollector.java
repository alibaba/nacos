package com.alibaba.nacos.plugin.control.connection;

/**
 * connection count metrics collector.
 *
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
     * get total count.
     *
     * @return
     */
    int getTotalCount();
    
    /**
     * get count for ip.
     *
     * @param ip ip.
     * @return
     */
    int getCountForIp(String ip);
}
