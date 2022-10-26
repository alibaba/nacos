package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.plugin.control.connection.ConnectionMetricsCollector;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

public class LongConnectionMetricsCollector implements ConnectionMetricsCollector {
    
    @Override
    public String getName() {
        return "long_connection";
    }
    
    @Override
    public int getTotalCount() {
        return ApplicationUtils.getBean(ConnectionManager.class).currentClientsCount();
    }
    
    @Override
    public int getCountForIp(String ip) {
        ConnectionManager connectionManager = ApplicationUtils.getBean(ConnectionManager.class);
        if (connectionManager.getConnectionForClientIp().containsKey(ip)) {
            return connectionManager.getConnectionForClientIp().get(ip).intValue();
        } else {
            return 0;
        }
    }
}