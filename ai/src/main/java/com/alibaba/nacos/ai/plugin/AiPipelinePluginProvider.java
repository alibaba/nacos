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

package com.alibaba.nacos.ai.plugin;

import com.alibaba.nacos.ai.pipeline.PublishPipelineManager;
import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.beans.BeansException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges {@link PublishPipelineService} instances to {@link com.alibaba.nacos.core.plugin.PluginManager}.
 *
 * <p>Publish pipeline nodes are <b>optional</b>: if none are configured or all fail to load, publishing
 * still proceeds without pipeline interception.</p>
 *
 * <p>Listing and enable/disable in {@code PluginManager} are not yet wired into pipeline execution;
 * disabling here does not stop {@link com.alibaba.nacos.ai.pipeline.PublishPipelineManager} until
 * that integration exists.</p>
 *
 * @author nacos
 */
public class AiPipelinePluginProvider implements PluginProvider<PublishPipelineService> {
    
    @Override
    public PluginType getPluginType() {
        return PluginType.AI_PIPELINE;
    }
    
    @Override
    public Map<String, PublishPipelineService> getAllPlugins() {
        if (ApplicationUtils.getApplicationContext() == null) {
            return Collections.emptyMap();
        }
        try {
            PublishPipelineManager manager = ApplicationUtils.getBean(PublishPipelineManager.class);
            Map<String, PublishPipelineService> map = new LinkedHashMap<>();
            for (PublishPipelineService service : manager.getAllServices()) {
                if (service != null && service.pipelineId() != null) {
                    map.putIfAbsent(service.pipelineId(), service);
                }
            }
            return map;
        } catch (BeansException | IllegalStateException ignored) {
            return Collections.emptyMap();
        }
    }
}
