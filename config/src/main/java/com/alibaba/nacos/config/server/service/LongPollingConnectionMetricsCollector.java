package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.plugin.control.connection.ConnectionMetricsCollector;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.stream.Collectors;

public class LongPollingConnectionMetricsCollector implements ConnectionMetricsCollector {
    
    @Override
    public String getName() {
        return "long_polling";
    }
    
    @Override
    public int getTotalCount() {
        return ApplicationUtils.getBean(LongPollingService.class).allSubs.size();
    }
    
    @Override
    public int getCountForIp(String ip) {
        return ApplicationUtils.getBean(LongPollingService.class).allSubs.stream()
                .filter(a -> a.ip.equalsIgnoreCase(ip)).collect(Collectors.toList()).size();
    }
}
