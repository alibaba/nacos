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

package com.alibaba.nacos.ai.service.agent;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.search.AgentSearchIndexProjector;
import com.alibaba.nacos.ai.service.search.AiResourceSearchReadinessService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Resolves the complete RAD Search read path for one request.
 *
 * <p>AUTO moves from SCAN to INDEX only after the Agent projection is ready. The transition is
 * sticky for this process so ordinary lifecycle convergence never moves a ready process back to
 * the legacy scan.</p>
 *
 * @author Nacos
 */
@Component
public class AgentSearchModeResolver {
    
    private static final int SERVICE_UNAVAILABLE_STATUS = 503;
    
    private final AiResourceSearchReadinessService readinessService;
    
    private final Supplier<String> configuredModeSupplier;
    
    private final AtomicBoolean autoIndex = new AtomicBoolean(false);
    
    @Autowired
    public AgentSearchModeResolver(
        ObjectProvider<AiResourceSearchReadinessService> readinessServiceProvider) {
        this(readinessServiceProvider.getIfAvailable(
            () -> AiResourceSearchReadinessService.NOOP),
            () -> EnvUtil.getProperty(Constants.Agent.RAD_SEARCH_MODE_CONFIG_KEY,
                AgentSearchMode.AUTO.name()));
    }
    
    AgentSearchModeResolver(AiResourceSearchReadinessService readinessService,
        Supplier<String> configuredModeSupplier) {
        this.readinessService = readinessService;
        this.configuredModeSupplier = configuredModeSupplier;
    }
    
    /**
     * Resolve AUTO to one physical read path and enforce INDEX readiness.
     *
     * @return {@link AgentSearchMode#SCAN} or {@link AgentSearchMode#INDEX}
     * @throws NacosException when INDEX is explicitly requested before readiness
     */
    public AgentSearchMode resolve() throws NacosException {
        AgentSearchMode configured = AgentSearchMode.parse(configuredModeSupplier.get());
        if (AgentSearchMode.SCAN == configured) {
            return AgentSearchMode.SCAN;
        }
        if (AgentSearchMode.AUTO == configured && autoIndex.get()) {
            return AgentSearchMode.INDEX;
        }
        boolean ready = readinessService.isReady(Constants.Agent.RESOURCE_TYPE_AGENT,
            AgentSearchIndexProjector.PROJECTION_VERSION);
        if (AgentSearchMode.INDEX == configured) {
            if (!ready) {
                throw unavailable();
            }
            return AgentSearchMode.INDEX;
        }
        if (ready) {
            autoIndex.compareAndSet(false, true);
        }
        return autoIndex.get() ? AgentSearchMode.INDEX : AgentSearchMode.SCAN;
    }
    
    private NacosException unavailable() {
        return new NacosException(SERVICE_UNAVAILABLE_STATUS,
            "Agent Search index projection is not ready.");
    }
}
