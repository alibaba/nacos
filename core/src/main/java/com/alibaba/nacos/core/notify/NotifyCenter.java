/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.notify;

import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.ShutdownUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.core.notify.listener.SmartSubscribe;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class NotifyCenter {

	private static final Logger LOGGER = LoggerFactory.getLogger(NotifyCenter.class);

	// Internal ArrayBlockingQueue buffer size. For applications with high write throughput,
	// this value needs to be increased appropriately. default value is 16384

	public static int RING_BUFFER_SIZE = 16384;

	private static final AtomicBoolean CLOSED = new AtomicBoolean(false);

	static {
		String ringBufferSizeProperty = "com.alibaba.nacos.core.notify.ringBufferSize";
		String val = System.getProperty(ringBufferSizeProperty, "16384");
		RING_BUFFER_SIZE = Integer.parseInt(val);
	}

	private static final NotifyCenter INSTANCE = new NotifyCenter();

	private final Map<String, Publisher> publisherMap = new ConcurrentHashMap<>(16);

	private final Set<SmartSubscribe> smartSubscribes = new ConcurrentHashSet<>();


	private final Publisher sharePublisher = new Publisher(SlowEvent.class, 1024) {

		@Override
		protected void notifySubscriber(Subscribe subscribe, Event event) {
			// Is to handle a SlowEvent, because the event shares an event
			// queue and requires additional filtering logic
			if (filter(subscribe, event)) {
				return;
			}
			super.notifySubscriber(subscribe, event);
		}

		private boolean filter(final Subscribe subscribe, final Event event) {
			final String sourceName = event.getClass().getCanonicalName();
			final String targetName = subscribe.subscribeType()
					.getCanonicalName();
			return !Objects.equals(sourceName, targetName);
		}

	};

	@JustForTest
	public static Map<String, Publisher> getPublisherMap() {
		return INSTANCE.publisherMap;
	}

	@JustForTest
	public static Publisher getPublisher(Class<? extends Event> topic) {
		if (SlowEvent.class.isAssignableFrom(topic)) {
			return INSTANCE.sharePublisher;
		}
		return INSTANCE.publisherMap.get(topic.getCanonicalName());
	}

	@JustForTest
	public static Set<SmartSubscribe> getSmartSubscribes() {
		return INSTANCE.smartSubscribes;
	}

	@JustForTest
	public static Publisher getSharePublisher() {
		return INSTANCE.sharePublisher;
	}

	private static volatile boolean stopDeferPublish = false;

	static {
		INSTANCE.sharePublisher.start();

		ShutdownUtils.addShutdownHook(new Thread(() -> {
			shutdown();
		}));
	}

	private static final AtomicBoolean closed = new AtomicBoolean(false);

	public static void shutdown() {
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		LOGGER.warn("[NotifyCenter] Start destroying Publisher");
		try {
			INSTANCE.publisherMap.forEach(new BiConsumer<String, Publisher>() {
				@Override
				public void accept(String s, Publisher publisher) {
					publisher.shutdown();
				}
			});

			INSTANCE.sharePublisher.shutdown();
		}
		catch (Throwable e) {
			LOGGER.error("NotifyCenter shutdown has error : {}", e);
		}
		LOGGER.warn("[NotifyCenter] Destruction of the end");
	}

	public static void stopDeferPublish() {
		stopDeferPublish = true;
	}

	/**
	 * Register a Subscriber. If the Publisher concerned by the
	 * Subscriber does not exist, then PublihserMap will preempt
	 * a placeholder Publisher first. not call {@link Publisher#start()}
	 *
	 * @param eventType Types of events that Subscriber cares about
	 * @param consumer  subscriber
	 * @param <T>       event type
	 */
	public static <T> void registerSubscribe(final Subscribe consumer) {
		final Class<? extends Event> cls = consumer.subscribeType();
		// If you want to listen to multiple events, you do it separately,
		// without automatically registering the appropriate publisher
		if (consumer instanceof SmartSubscribe) {
			INSTANCE.smartSubscribes.add((SmartSubscribe) consumer);
			return;
		}
		// If the event does not require additional queue resources,
		// go to share-publisher to reduce resource waste
		if (SlowEvent.class.isAssignableFrom(cls)) {
			INSTANCE.sharePublisher.addSubscribe(consumer);
			return;
		}
		final String topic = consumer.subscribeType().getCanonicalName();
		INSTANCE.publisherMap.computeIfAbsent(topic, s -> new Publisher(cls));
		Publisher publisher = INSTANCE.publisherMap.get(topic);
		publisher.addSubscribe(consumer);
	}

	/**
	 * deregister subscriber
	 *
	 * @param consumer subscriber
	 * @param <T>
	 */
	public static <T> void deregisterSubscribe(final Subscribe consumer) {
		final Class<? extends Event> cls = consumer.subscribeType();
		if (consumer instanceof SmartSubscribe) {
			INSTANCE.smartSubscribes.remove((SmartSubscribe) consumer);
			return;
		}
		if (SlowEvent.class.isAssignableFrom(cls)) {
			INSTANCE.sharePublisher.unSubscribe(consumer);
			return;
		}
		final String topic = consumer.subscribeType().getCanonicalName();
		if (INSTANCE.publisherMap.containsKey(topic)) {
			Publisher publisher = INSTANCE.publisherMap.get(topic);
			publisher.unSubscribe(consumer);
			return;
		}
		throw new NoSuchElementException("The subcriber has no event publisher");
	}

	/**
	 * request publisher publish event
	 * Publishers load lazily, calling publisher. Start () only when the event is actually published
	 *
	 * @param event
	 */
	public static boolean publishEvent(final Event event) {
		return publishEvent(event.getClass(), event);
	}

	/**
	 * request publisher publish event
	 * Publishers load lazily, calling publisher. Start () only when the event is actually published
	 *
	 * @param eventType
	 * @param event
	 */
	private static boolean publishEvent(final Class<? extends Event> eventType,
			final Event event) {
		final String topic = eventType.getCanonicalName();
		if (SlowEvent.class.isAssignableFrom(eventType)) {
			return INSTANCE.sharePublisher.publish(event);
		}

		if (INSTANCE.publisherMap.containsKey(topic)) {
			Publisher publisher = INSTANCE.publisherMap.get(topic);
			if (!publisher.isInitialized()) {
				publisher.start();
			}
			return publisher.publish(event);
		}
		throw new NoSuchElementException(
				"There are no [" + topic + "] publishers for this event, please register");
	}

	/**
	 * register to share-publisher
	 *
	 * @param supplier
	 * @param eventType
	 * @return
	 */
	public static Publisher registerToSharePublisher(
			final Class<? extends SlowEvent> eventType) {
		return INSTANCE.sharePublisher;
	}

	/**
	 * register publisher
	 *
	 * @param supplier
	 * @param eventType
	 * @param queueMaxSize
	 * @return
	 */
	public static Publisher registerToPublisher(final Class<? extends Event> eventType,
			final int queueMaxSize) {

		if (SlowEvent.class.isAssignableFrom(eventType)) {
			return INSTANCE.sharePublisher;
		}

		final String topic = eventType.getCanonicalName();
		INSTANCE.publisherMap.computeIfAbsent(topic, s -> {
			Publisher publisher = new Publisher(eventType, queueMaxSize);
			return publisher;
		});
		Publisher publisher = INSTANCE.publisherMap.get(topic);
		publisher.queueMaxSize = queueMaxSize;
		return publisher;
	}

	/**
	 * deregister publisher
	 *
	 * @param eventType
	 * @return
	 */
	public static void deregisterPublisher(final Class<? extends Event> eventType) {
		final String topic = eventType.getCanonicalName();
		Publisher publisher = INSTANCE.publisherMap.remove(topic);
		publisher.shutdown();
	}

	public static class Publisher extends Thread {

		private volatile boolean initialized = false;
		private volatile boolean canOpen = false;
		private volatile boolean shutdown = false;

		private final Class<? extends Event> eventType;
		private final CopyOnWriteArraySet<Subscribe> subscribes = new CopyOnWriteArraySet<>();
		private int queueMaxSize = -1;
		private BlockingQueue<Event> queue;
		private long lastEventSequence = -1L;

		Publisher(final Class<? extends Event> eventType) {
			this(eventType, RING_BUFFER_SIZE);
		}

		Publisher(final Class<? extends Event> eventType, final int queueMaxSize) {
			this.eventType = eventType;
			this.queueMaxSize = queueMaxSize;
			this.queue = new ArrayBlockingQueue<>(queueMaxSize);
		}

		public CopyOnWriteArraySet<Subscribe> getSubscribes() {
			return subscribes;
		}

		@Override
		public synchronized void start() {
			super.start();
			if (!initialized) {
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
				for (; ; ) {
					if (shutdown || canOpen || stopDeferPublish) {
						break;
					}
					ThreadUtils.sleep(1_000L);
				}

				for (; ; ) {
					if (shutdown) {
						break;
					}
					final Event event = queue.take();
					receiveEvent(event);
					lastEventSequence = Math.max(lastEventSequence, event.sequence());
				}
			}
			catch (Throwable ex) {
				LOGGER.error("Event listener exception : {}", ex);
			}
		}

		void addSubscribe(Subscribe subscribe) {
			subscribes.add(subscribe);
			canOpen = true;
		}

		void unSubscribe(Subscribe subscribe) {
			subscribes.remove(subscribe);
		}

		boolean publish(Event event) {
			checkIsStart();
			try {
				this.queue.put(event);
				return true;
			}
			catch (InterruptedException ignore) {
				Thread.interrupted();
				LOGGER.warn(
						"Unable to plug in due to interruption, synchronize sending time, event : {}",
						event);
				receiveEvent(event);
				return true;
			}
			catch (Throwable ex) {
				LOGGER.error("[NotifyCenter] publish {} has error : {}", event, ex);
				return false;
			}
		}

		void checkIsStart() {
			if (!initialized) {
				throw new IllegalStateException("Publisher does not start");
			}
		}

		void shutdown() {
			this.shutdown = true;
			this.queue.clear();
		}

		public boolean isInitialized() {
			return initialized;
		}

		void receiveEvent(Event event) {
			final long currentEventSequence = event.sequence();

			// Notification single event listener
			for (Subscribe subscribe : subscribes) {
				// Whether to ignore expiration events
				if (subscribe.ignoreExpireEvent()
						&& lastEventSequence > currentEventSequence) {
					LOGGER.debug(
							"[NotifyCenter] the {} is unacceptable to this subscriber, because had expire",
							event.getClass());
					continue;
				}
				notifySubscriber(subscribe, event);
			}

			// Notification multi-event event listener
			for (SmartSubscribe subscribe : INSTANCE.smartSubscribes) {
				// If you are a multi-event listener, you need to make additional logical judgments
				if (!subscribe.canNotify(event)) {
					LOGGER.debug(
								"[NotifyCenter] the {} is unacceptable to this multi-event subscriber",
								event.getClass());
					continue;
				}
				notifySubscriber(subscribe, event);
			}
		}

		protected void notifySubscriber(final Subscribe subscribe, final Event event) {
			LOGGER.debug("[NotifyCenter] the {} will received by {}", event,
					subscribe);

			final Runnable job = () -> subscribe.onEvent(event);
			final Executor executor = subscribe.executor();
			if (Objects.nonNull(executor)) {
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

}
