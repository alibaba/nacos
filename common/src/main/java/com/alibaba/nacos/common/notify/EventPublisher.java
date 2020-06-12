package com.alibaba.nacos.common.notify;

import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.listener.AbstractSubscriber;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;

import java.util.Set;

/**
 * Event publisher
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface EventPublisher extends Closeable {

    /**
     * Multi-event listener collection list
     */
    Set<SmartSubscriber> SMART_SUBSCRIBERS = new ConcurrentHashSet<SmartSubscriber>();

    /**
     * Initializes the event publisher
     *
     * @param type {@link Class<? extends AbstractEvent>}
     * @param bufferSize Message staging queue size
     */
    void init(Class<? extends AbstractEvent> type, int bufferSize);

    /**
     * The number of currently staged events
     *
     * @return event size
     */
    long currentEventSize();

    /**
     * Add listener
     *
     * @param subscribe {@link AbstractSubscriber}
     */
    void addSubscriber(AbstractSubscriber subscribe);

    /**
     * Remove listener
     *
     * @param subscriber {@link AbstractSubscriber}
     */
    void unSubscriber(AbstractSubscriber subscriber);

    /**
     * publish event
     *
     * @param event {@link AbstractEvent}
     * @return publish event is success
     */
    boolean publish(AbstractEvent event);

    /**
     * Notify listener
     *
     * @param subscriber {@link AbstractSubscriber}
     * @param event {@link AbstractEvent}
     */
    void notifySubscriber(AbstractSubscriber subscriber, AbstractEvent event);
}
