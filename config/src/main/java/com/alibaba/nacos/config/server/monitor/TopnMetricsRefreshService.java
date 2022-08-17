/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.monitor;

import com.alibaba.nacos.common.utils.Pair;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * top n metrics refresh service.
 *
 * @author <a href="mailto:liuyixiao0821@gmail.com">liuyixiao</a>
 */
@Service
public class TopnMetricsRefreshService {
    
    private static final int CONFIG_CHANGE_N = 10;
    
    private CompositeMeterRegistry topnConfigChangeRegistry = new CompositeMeterRegistry();
    
    @Autowired
    public TopnMetricsRefreshService(PrometheusMeterRegistry prometheusMeterRegistry) {
        topnConfigChangeRegistry.add(prometheusMeterRegistry);
    }
    
    /**
     * refresh config change count top n.
     */
    @Scheduled(cron = "0/30 * * * * *")
    public void refreshTopnConfigChangeCount() {
        topnConfigChangeRegistry.clear();
        List<Pair<String, AtomicInteger>> topnConfigChangeCount = MetricsMonitor.getConfigChangeCount()
                .getTopNCounter(CONFIG_CHANGE_N);
        for (Pair<String, AtomicInteger> configChangeCount : topnConfigChangeCount) {
            List<Tag> tags = new ArrayList<>();
            tags.add(new ImmutableTag("config", configChangeCount.getFirst()));
            topnConfigChangeRegistry.gauge("config_change_count", tags, configChangeCount.getSecond());
        }
    }
}
