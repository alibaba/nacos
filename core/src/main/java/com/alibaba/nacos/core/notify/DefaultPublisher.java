/*
 *
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
 *
 */

package com.alibaba.nacos.core.notify;

import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.core.notify.listener.SmartSubscribe;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static com.alibaba.nacos.core.notify.NotifyCenter.RING_BUFFER_SIZE;

/**
 * The default event publisher implementation
 *
 * Internally, use {@link ArrayBlockingQueue<Event>} as a message staging queue
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DefaultPublisher extends Thread implements EventPublisher {

	private static final Logger LOGGER = LoggerFactory.getLogger(NotifyCenter.class);

	private volatile boolean initialized = false;
	private volatile boolean canOpen = false;
	private volatile boolean shutdown = false;

	private Class<? extends Event> eventType;
	private final ConcurrentHashSet<Subscribe> subscribes = new ConcurrentHashSet<>();
	private int queueMaxSize = -1;
	private BlockingQueue<Event> queue;
	private volatile Long lastEventSequence = -1L;
	private final AtomicReferenceFieldUpdater<DefaultPublisher, Long> updater = AtomicReferenceFieldUpdater.newUpdater(DefaultPublisher.class, Long.class, "lastEventSequence");

	@Override
	public void init(Class<? extends Event> type, int bufferSize) {
		setDaemon(true);
		setName("nacos.publisher-" + type.getName());
		this.eventType = type;
		this.queueMaxSize = bufferSize;
		this.queue = new ArrayBlockingQueue<>(bufferSize);
		start();
	}

	public ConcurrentHashSet<Subscribe> getSubscribes() {
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
				if (shutdown || canOpen) {
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
				updater.compareAndSet(this, lastEventSequence, Math.max(lastEventSequence, event.sequence()));
			}
		}
		catch (Throwable ex) {
			LOGGER.error("Event listener exception : {}", ex);
		}
	}

	@Override
	public void addSubscribe(Subscribe subscribe) {
		subscribes.add(subscribe);
		canOpen = true;
	}

	@Override
	public void unSubscribe(Subscribe subscribe) {
		subscribes.remove(subscribe);
	}

	@Override
	public boolean publish(Event event) {
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
	public void shutdown() {
		this.shutdown = true;
		this.queue.clear();
	}

	public boolean isInitialized() {
		return initialized;
	}

	void receiveEvent(Event event) {
		final long currentEventSequence = event.sequence();
		final String sourceName = event.getClass().getName();

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

			final String targetName = subscribe.subscribeType().getName();
			if (!Objects.equals(sourceName, targetName)) {
				continue;
			}

			notifySubscriber(subscribe, event);
		}

		// Notification multi-event event listener
		for (SmartSubscribe subscribe : SMART_SUBSCRIBES) {
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

	@Override
	public void notifySubscriber(final Subscribe subscribe, final Event event) {

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
