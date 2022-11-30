package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsBarrierCreator;

public class MseTpsBarrierCreator implements TpsBarrierCreator {
    
    @Override
    public String getName() {
        return "mse";
    }
    
    @Override
    public TpsBarrier createTpsBarrier(String pointName) {
        return new MseTpsBarrier(pointName);
    }
}
