package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsBarrierCreator;

public class DefaultNacosTpsBarrierCreator implements TpsBarrierCreator {
    
    @Override
    public String getName() {
        return "nacos";
    }
    
    @Override
    public TpsBarrier createTpsBarrier(String pointName) {
        return new NacosTpsBarrier(pointName);
    }
}
