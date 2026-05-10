/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.ai.event.PromptDownloadEvent;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Accumulates prompt download events in memory and flushes to DB periodically. Each node maintains its own counter
 * and uses atomic SQL ({@code download_count = download_count + N}) for cluster safety.
 *
 * <p>This class subscribes to {@link PromptDownloadEvent} via {@link NotifyCenter}.</p>
 *
 * @author nacos
 * @since 3.2.0
 */
@Component
public class PromptDownloadCountManager extends SmartSubscriber {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PromptDownloadCountManager.class);
    
    private static final String RESOURCE_TYPE_PROMPT = "prompt";
    
    /**
     * Flush interval in seconds.
     */
    private static final long FLUSH_INTERVAL_SECONDS = 10;
    
    /**
     * In-memory counter. Key = composite key (ns + name + version), Value = accumulated count.
     */
    private final ConcurrentHashMap<DownloadCountKey, AtomicLong> counterMap =
        new ConcurrentHashMap<>();
    
    private final AiResourcePersistService aiResourcePersistService;
    
    private final AiResourceVersionPersistService aiResourceVersionPersistService;
    
    private final ScheduledExecutorService scheduler;
    
    public PromptDownloadCountManager(AiResourcePersistService aiResourcePersistService,
        AiResourceVersionPersistService aiResourceVersionPersistService) {
        this.aiResourcePersistService = aiResourcePersistService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
        this.scheduler = ExecutorFactory.newSingleScheduledExecutorService(
            r -> {
                Thread t = new Thread(r, "com.alibaba.nacos.ai.prompt-download-count-flusher");
                t.setDaemon(true);
                return t;
            });
        this.scheduler.scheduleWithFixedDelay(this::flush, FLUSH_INTERVAL_SECONDS,
            FLUSH_INTERVAL_SECONDS,
            TimeUnit.SECONDS);
        NotifyCenter.registerSubscriber(this);
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        return Collections.singletonList(PromptDownloadEvent.class);
    }
    
    @Override
    public void onEvent(Event event) {
        if (event instanceof PromptDownloadEvent downloadEvent) {
            DownloadCountKey key =
                new DownloadCountKey(downloadEvent.getNamespaceId(), downloadEvent.getName(),
                    downloadEvent.getVersion());
            counterMap.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        }
    }
    
    /**
     * Swap and flush all accumulated counts to DB.
     */
    private void flush() {
        if (counterMap.isEmpty()) {
            return;
        }
        for (Map.Entry<DownloadCountKey, AtomicLong> entry : counterMap.entrySet()) {
            long count = entry.getValue().getAndSet(0);
            if (count == 0) {
                counterMap.remove(entry.getKey());
                continue;
            }
            DownloadCountKey key = entry.getKey();
            try {
                // Increment version-level count
                aiResourceVersionPersistService.incrementDownloadCount(key.namespaceId, key.name,
                    RESOURCE_TYPE_PROMPT, key.version, count);
                // Increment total count on resource
                aiResourcePersistService.incrementDownloadCount(key.namespaceId, key.name,
                    RESOURCE_TYPE_PROMPT,
                    count);
            } catch (Exception e) {
                LOGGER.warn("Failed to flush prompt download count for {}@{}: {}", key.name,
                    key.version,
                    e.getMessage());
                // Put the count back for retry on next flush
                counterMap.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(count);
            }
        }
    }
    
    /**
     * Flush remaining counts and shut down the scheduler.
     */
    @PreDestroy
    public void shutdown() {
        try {
            flush();
        } catch (Exception e) {
            LOGGER.warn("Failed to flush prompt download counts on shutdown: {}", e.getMessage());
        }
        scheduler.shutdown();
        NotifyCenter.deregisterSubscriber(this);
    }
    
    /**
     * Composite key for download counter.
     */
    private static final class DownloadCountKey {
        
        private final String namespaceId;
        
        private final String name;
        
        private final String version;
        
        DownloadCountKey(String namespaceId, String name, String version) {
            this.namespaceId = namespaceId;
            this.name = name;
            this.version = version;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DownloadCountKey that)) {
                return false;
            }
            return Objects.equals(namespaceId, that.namespaceId) && Objects.equals(name, that.name)
                && Objects.equals(version, that.version);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(namespaceId, name, version);
        }
    }
}
