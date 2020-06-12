package com.alibaba.nacos.common.notify;

import java.io.Serializable;

/**
 * An abstract class for event.
 *
 * @author zongtanghu
 */
public abstract class AbstractEvent implements Serializable {

    /**
     * Event sequence number, which can be used to handle the sequence of events
     *
     * @return sequence num, It's best to make sure it's monotone
     */
    public long sequence() {
        return System.currentTimeMillis();
    }
}
