package com.alibaba.nacos.common.notify;

import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.listener.Subscriber;
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
     * @param type {@link Class<? extends  Event >}
     * @param bufferSize Message staging queue size
     */
    void init(Class<? extends Event> type, int bufferSize);

    /**
     * The number of currently staged events
     *
     * @return event size
     */
    long currentEventSize();

    /**
     * Add listener
     *
     * @param subscribe {@link Subscriber}
     */
    void addSubscriber(Subscriber subscribe);

    /**
     * Remove listener
     *
     * @param subscriber {@link Subscriber}
     */
    void unSubscriber(Subscriber subscriber);

    /**
     * publish event
     *
     * @param event {@link Event}
     * @return publish event is success
     */
    boolean publish(Event event);

    /**
     * Notify listener
     *
     * @param subscriber {@link Subscriber}
     * @param event {@link Event}
     */
    void notifySubscriber(Subscriber subscriber, Event event);
}
