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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cached and rate-limited readiness observation for all Search Core consumers.
 *
 * <p>Readiness describes snapshot completeness. It never blocks a client Search request.</p>
 *
 * @author Nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class DefaultAiResourceSearchReadinessObserver
    implements AiResourceSearchReadinessObserver {
    
    static final long NOT_READY_RECHECK_INTERVAL_MILLIS = 5000L;
    
    static final long WARNING_INTERVAL_MILLIS = 60000L;
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DefaultAiResourceSearchReadinessObserver.class);
    
    private final AiResourceSearchTypeHandlerRegistry typeHandlerRegistry;
    
    private final AiResourceSearchReadinessService readinessService;
    
    private final Clock clock;
    
    private final Logger logger;
    
    private final Map<String, ReadinessObservation> observations =
        new ConcurrentHashMap<>();
    
    private final Map<String, Long> warningTimes = new ConcurrentHashMap<>();
    
    @Autowired
    public DefaultAiResourceSearchReadinessObserver(
        AiResourceSearchTypeHandlerRegistry typeHandlerRegistry,
        AiResourceSearchReadinessService readinessService) {
        this(typeHandlerRegistry, readinessService, Clock.systemUTC(), LOGGER);
    }
    
    DefaultAiResourceSearchReadinessObserver(
        AiResourceSearchTypeHandlerRegistry typeHandlerRegistry,
        AiResourceSearchReadinessService readinessService, Clock clock, Logger logger) {
        this.typeHandlerRegistry = typeHandlerRegistry;
        this.readinessService = readinessService;
        this.clock = clock;
        this.logger = logger;
    }
    
    @Override
    public void observe(Collection<String> resourceTypes) {
        long now = clock.millis();
        List<Projection> unready = new ArrayList<>();
        for (String resourceType : requestedTypes(resourceTypes)) {
            AiResourceSearchTypeHandler handler = typeHandlerRegistry.get(resourceType);
            if (handler == null || handler.projectionVersion() <= 0) {
                continue;
            }
            Projection projection = new Projection(resourceType, handler.projectionVersion());
            if (!isReady(projection, now)) {
                unready.add(projection);
            }
        }
        if (unready.isEmpty() || !warningDue(unready, now)) {
            return;
        }
        logger.warn("AI resource Search is serving the current index snapshot while projections "
            + "are not ready; results may be incomplete: {}", unready);
    }
    
    private Collection<String> requestedTypes(Collection<String> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return typeHandlerRegistry.resourceTypes();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String resourceType : resourceTypes) {
            if (StringUtils.isNotBlank(resourceType)) {
                result.add(resourceType);
            }
        }
        return result;
    }
    
    private boolean isReady(Projection projection, long now) {
        String key = projection.toString();
        ReadinessObservation current = observations.get(key);
        if (current != null && (current.ready
            || now - current.checkedAt < NOT_READY_RECHECK_INTERVAL_MILLIS)) {
            return current.ready;
        }
        boolean ready;
        try {
            ready = readinessService.isReady(projection.resourceType,
                projection.projectionVersion);
        } catch (RuntimeException ignored) {
            ready = false;
        }
        observations.put(key, new ReadinessObservation(ready, now));
        if (ready) {
            warningTimes.remove(key);
        }
        return ready;
    }
    
    private boolean warningDue(List<Projection> projections, long now) {
        AtomicBoolean due = new AtomicBoolean(false);
        for (Projection projection : projections) {
            String key = projection.toString();
            warningTimes.compute(key, (ignored, previous) -> {
                if (previous == null || now - previous >= WARNING_INTERVAL_MILLIS) {
                    due.set(true);
                    return now;
                }
                return previous;
            });
        }
        return due.get();
    }
    
    private static final class ReadinessObservation {
        
        private final boolean ready;
        
        private final long checkedAt;
        
        private ReadinessObservation(boolean ready, long checkedAt) {
            this.ready = ready;
            this.checkedAt = checkedAt;
        }
    }
    
    private static final class Projection {
        
        private final String resourceType;
        
        private final int projectionVersion;
        
        private Projection(String resourceType, int projectionVersion) {
            this.resourceType = resourceType;
            this.projectionVersion = projectionVersion;
        }
        
        @Override
        public String toString() {
            return resourceType + "@v" + projectionVersion;
        }
    }
}
