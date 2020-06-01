package com.alibaba.nacos.common.lifecycle;

import java.io.IOException;

/**
 * An interface is used to define the resource's close and shutdown,
 * such as IO Connection and ThreadPool.
 *
 * @author zongtanghu
 *
 */
public interface Closeable {

    /**
     * Close the Resources, such as IO Connection resourcese.
     *
     * @throws IOException
     */
    public void close() throws IOException;

    /**
     * Shutdown the Resources, such as Thread Pool.
     *
     * @throws InterruptedException
     */
    public void shutdown() throws InterruptedException;

}
