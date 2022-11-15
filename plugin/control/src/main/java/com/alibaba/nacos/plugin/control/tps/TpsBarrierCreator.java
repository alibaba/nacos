package com.alibaba.nacos.plugin.control.tps;

public interface TpsBarrierCreator {
    
    /**
     * @return
     */
    String getName();
    
    /**
     * create tps barrier.
     *
     * @param pointName
     * @return
     */
    TpsBarrier createTpsBarrier(String pointName);
}
