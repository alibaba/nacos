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

package com.alibaba.nacos.plugin.trace.ai;

import com.alibaba.nacos.common.trace.event.TraceEvent;
import com.alibaba.nacos.common.trace.event.ai.AiResourceTraceEvent;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.trace.spi.NacosTraceSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default AI resource trace subscriber that keeps the existing file log output.
 *
 * @author nacos
 */
public class AiResourceTraceLogSubscriber implements NacosTraceSubscriber {
    
    public static final String NAME = "ai-resource-trace-log";
    
    private static final Logger TRACE_LOG =
        LoggerFactory.getLogger("com.alibaba.nacos.ai.resource.trace");
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public void onEvent(TraceEvent event) {
        if (!(event instanceof AiResourceTraceEvent) || !TRACE_LOG.isInfoEnabled()) {
            return;
        }
        TRACE_LOG.info(JacksonUtils.toJson(buildLogEntry((AiResourceTraceEvent) event)));
    }
    
    @Override
    public List<Class<? extends TraceEvent>> subscribeTypes() {
        return Collections.singletonList(AiResourceTraceEvent.class);
    }
    
    static Map<String, Object> buildLogEntry(AiResourceTraceEvent event) {
        Map<String, Object> logEntry = new LinkedHashMap<>(10);
        logEntry.put("timestamp",
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(event.getEventTime())));
        logEntry.put("operator", StringUtils.defaultIfBlank(event.getOperator(), "-"));
        logEntry.put("resource_type", StringUtils.defaultIfBlank(event.getResourceType(), "-"));
        logEntry.put("resource_id", StringUtils.defaultIfBlank(event.getResourceId(), "-"));
        if (StringUtils.isNotBlank(event.getVersion())) {
            logEntry.put("version", event.getVersion());
        }
        logEntry.put("operation", StringUtils.defaultIfBlank(event.getOperation(), "-"));
        logEntry.put("status", StringUtils.defaultIfBlank(event.getStatus(), "-"));
        logEntry.put("ip", StringUtils.defaultIfBlank(event.getClientIp(), "-"));
        if (StringUtils.isNotBlank(event.getExt())) {
            logEntry.put("ext", event.getExt());
        }
        return logEntry;
    }
}
