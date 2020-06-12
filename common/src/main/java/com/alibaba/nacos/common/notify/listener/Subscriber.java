package com.alibaba.nacos.common.notify.listener;

import com.alibaba.nacos.common.notify.Event;

import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author zongtanghu
 *
 */
public interface Subscriber<T extends Event> {

    /**
     * Event callback
     *
     * @param event {@link Event}
     */
    void onEvent(T event);

    /**
     * Type of this subscriber's subscription
     *
     * @return Class which extends {@link Event}
     */
    Class<? extends Event> subscribeType();

    /**
     * It is up to the listener to determine whether the callback is asynchronous or synchronous
     *
     * @return {@link Executor}
     */
    default Executor executor() {
        return null;
    }

    /**
     * Whether to ignore expired events
     *
     * @return default value is {@link Boolean#FALSE}
     */
    default boolean ignoreExpireEvent() {
        return false;
    }

}
