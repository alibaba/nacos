package com.alibaba.nacos.common.notify.listener;

import com.alibaba.nacos.common.notify.AbstractEvent;

import java.util.concurrent.Executor;

/**
 * An abstract subscriber class for subscriber interface.
 *
 * @author zongtanghu
 */
public abstract class AbstractSubscriber<T extends AbstractEvent>{

    /**
     * Event callback
     *
     * @param event {@link AbstractEvent}
     */
    public abstract void onEvent(T event);

    /**
     * Type of this subscriber's subscription
     *
     * @return Class which extends {@link AbstractEvent}
     */
    public abstract Class<? extends AbstractEvent> subscriberType();

    /**
     * It is up to the listener to determine whether the callback is asynchronous or synchronous
     *
     * @return {@link Executor}
     */
    public Executor executor() {
        return null;
    }

    /**
     * Whether to ignore expired events
     *
     * @return default value is {@link Boolean#FALSE}
     */
    public boolean ignoreExpireEvent() {
        return false;
    }
}
