package com.alibaba.nacos.common.notify.listener;


import com.alibaba.nacos.common.notify.AbstractEvent;

/**
 * Subscribers to multiple events can be listened to
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author zongtanghu
 *
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class SmartSubscriber extends AbstractSubscriber {

    /**
     * Determines if the processing message is acceptable
     *
     * @param event {@link AbstractEvent}
     * @return Determines if the processing message is acceptable
     */
    public abstract boolean canNotify(AbstractEvent event);

    @Override
    public final Class<? extends AbstractEvent> subscriberType() {
        return null;
    }

    @Override
    public final boolean ignoreExpireEvent() {
        return false;
    }
}
