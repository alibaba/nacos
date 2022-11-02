package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

public interface TpsCheckRequestCreator {
    
    public TpsCheckRequest create();
    
}
