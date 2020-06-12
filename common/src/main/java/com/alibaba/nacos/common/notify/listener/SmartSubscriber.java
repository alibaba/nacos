package com.alibaba.nacos.common.notify.listener;


import com.alibaba.nacos.common.notify.Event;

/**
 * Subscribers to multiple events can be listened to
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author zongtanghu
 *
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class SmartSubscriber implements Subscriber<Event> {

    /**
     * Determines if the processing message is acceptable
     *
     * @param event {@link Event}
     * @return Determines if the processing message is acceptable
     */
    public abstract boolean canNotify(Event event);

    @Override
    public final Class<? extends Event> subscribeType() {
        return null;
    }

    @Override
    public final boolean ignoreExpireEvent() {
        return false;
    }
}
