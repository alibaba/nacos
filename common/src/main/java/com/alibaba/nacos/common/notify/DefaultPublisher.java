package com.alibaba.nacos.common.notify;

import com.alibaba.nacos.common.notify.listener.AbstractSubscriber;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.Objects;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static com.alibaba.nacos.common.notify.NotifyCenter.RING_BUFFER_SIZE;

/**
 * The default event publisher implementation
 *
 * Internally, use {@link ArrayBlockingQueue <Event>} as a message staging queue
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author zongtanghu
 *
 */
public class DefaultPublisher extends Thread implements EventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotifyCenter.class);

    private volatile boolean initialized = false;
    private volatile boolean canOpen = false;
    private volatile boolean shutdown = false;

    private Class<? extends AbstractEvent> eventType;
    private final ConcurrentHashSet<AbstractSubscriber> subscribers = new ConcurrentHashSet<AbstractSubscriber>();
    private int queueMaxSize = -1;
    private BlockingQueue<AbstractEvent> queue;
    private volatile Long lastEventSequence = -1L;
    private final AtomicReferenceFieldUpdater<DefaultPublisher, Long> updater = AtomicReferenceFieldUpdater.newUpdater(DefaultPublisher.class, Long.class, "lastEventSequence");

    @Override
    public void init(Class<? extends AbstractEvent> type, int bufferSize) {
        setDaemon(true);
        setName("nacos.publisher-" + type.getName());
        this.eventType = type;
        this.queueMaxSize = bufferSize;
        this.queue = new ArrayBlockingQueue<AbstractEvent>(bufferSize);
        start();
    }

    public ConcurrentHashSet<AbstractSubscriber> getSubscribers() {
        return subscribers;
    }

    @Override
    public synchronized void start() {
        if (!initialized) {
            // start just called once
            super.start();
            if (queueMaxSize == -1) {
                queueMaxSize = RING_BUFFER_SIZE;
            }
            initialized = true;
        }
    }

    public long currentEventSize() {
        return queue.size();
    }

    @Override
    public void run() {
        openEventHandler();
    }

    void openEventHandler() {
        try {
            // To ensure that messages are not lost, enable EventHandler when
            // waiting for the first Subscriber to register
            for (;;) {
                if (shutdown || canOpen) {
                    break;
                }
                ThreadUtils.sleep(1000L);
            }

            for (;;) {
                if (shutdown) {
                    break;
                }
                final AbstractEvent event = queue.take();
                receiveEvent(event);
                updater.compareAndSet(this, lastEventSequence, Math.max(lastEventSequence, event.sequence()));
            }
        }
        catch (Throwable ex) {
            LOGGER.error("Event listener exception : {}", ex);
        }
    }

    @Override
    public void addSubscriber(AbstractSubscriber subscriber) {
        subscribers.add(subscriber);
        canOpen = true;
    }

    @Override
    public void unSubscriber(AbstractSubscriber subscriber) {
        subscribers.remove(subscriber);
    }

    @Override
    public boolean publish(AbstractEvent event) {
        checkIsStart();
        boolean success = this.queue.offer(event);
        if (!success) {
            LOGGER.warn(
                "Unable to plug in due to interruption, synchronize sending time, event : {}",
                event);
            receiveEvent(event);
            return true;
        }
        return true;
    }

    void checkIsStart() {
        if (!initialized) {
            throw new IllegalStateException("Publisher does not start");
        }
    }

    @Override
    public void shutdown(){
        this.shutdown = true;
        this.queue.clear();
    }

    public boolean isInitialized() {
        return initialized;
    }

    void receiveEvent(AbstractEvent event) {
        final long currentEventSequence = event.sequence();
        final String sourceName = event.getClass().getName();

        // Notification single event listener
        for (AbstractSubscriber subscriber : subscribers) {
            // Whether to ignore expiration events
            if (subscriber.ignoreExpireEvent()
                && lastEventSequence > currentEventSequence) {
                LOGGER.debug(
                    "[NotifyCenter] the {} is unacceptable to this subscriber, because had expire",
                    event.getClass());
                continue;
            }

            final String targetName = subscriber.subscriberType().getName();

            if (!Objects.equals(sourceName, targetName)) {
                continue;
            }

            notifySubscriber(subscriber, event);
        }

        // Notification multi-event event listener
        for (SmartSubscriber subscriber : SMART_SUBSCRIBERS) {
            // If you are a multi-event listener, you need to make additional logical judgments
            if (!subscriber.canNotify(event)) {
                LOGGER.debug(
                    "[NotifyCenter] the {} is unacceptable to this multi-event subscriber",
                    event.getClass());
                continue;
            }
            notifySubscriber(subscriber, event);
        }
    }

    @Override
    public void notifySubscriber(final AbstractSubscriber subscriber, final AbstractEvent event) {

        LOGGER.debug("[NotifyCenter] the {} will received by {}", event,
            subscriber);

        final Runnable job = new Runnable() {
            @Override
            public void run() {
                subscriber.onEvent(event);
            }
        };

        final Executor executor = subscriber.executor();

        if (executor != null) {
            executor.execute(job);
        }
        else {
            try {
                job.run();
            }
            catch (Throwable e) {
                LOGGER.error("Event callback exception : {}", e);
            }
        }
    }
}
