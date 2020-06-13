package com.alibaba.nacos.common.notify;

import java.io.Serializable;

/**
 * An abstract class for event.
 *
 * @author zongtanghu
 */
@SuppressWarnings("all")
public abstract class Event implements Serializable {

    /**
     * Event sequence number, which can be used to handle the sequence of events
     *
     * @return sequence num, It's best to make sure it's monotone
     */
    public abstract long sequence();
}
