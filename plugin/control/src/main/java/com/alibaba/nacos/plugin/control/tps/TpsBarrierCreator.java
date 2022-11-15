package com.alibaba.nacos.plugin.control.tps;

public interface TpsBarrierCreator {
    
    /**
     * get name.
     *
     * @return
     */
    String getName();
    
    /**
     * create tps barrier.
     *
     * @param pointName pointName.
     * @return
     */
    TpsBarrier createTpsBarrier(String pointName);
}
