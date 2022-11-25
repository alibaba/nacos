package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.TpsMetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MseTpsMetrics extends TpsMetrics {
    
    
    private Map<String, Counter> protoKeyCounter = new HashMap<>();

    public MseTpsMetrics(String pointName, String type, long timeStamp, TimeUnit period) {
        super(pointName, type, timeStamp, period);
    }
    
    public Map<String, Counter> getProtoKeyCounter() {
        return protoKeyCounter;
    }
    
    public void setProtoKeyCounter(Map<String, Counter> protoKeyCounter) {
        this.protoKeyCounter = protoKeyCounter;
    }
}
