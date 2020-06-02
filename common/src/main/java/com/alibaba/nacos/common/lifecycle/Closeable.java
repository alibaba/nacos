package com.alibaba.nacos.common.lifecycle;


import com.alibaba.nacos.api.exception.NacosException;

/**
 * An interface is used to define the resource's close and shutdown,
 * such as IO Connection and ThreadPool.
 *
 * @author zongtanghu
 *
 */
public interface Closeable {

    /**
     * Shutdown the Resources, such as Thread Pool.
     *
     * @throws NacosException exception.
     */
    public void shutdown() throws NacosException;

}
