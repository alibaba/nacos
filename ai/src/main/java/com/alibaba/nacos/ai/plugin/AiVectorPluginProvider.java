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

import com.alibaba.nacos.ai.service.ard.vector.ArdVectorIndexRouter;
import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.ard.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.beans.BeansException;

import java.util.Collections;
import java.util.Map;

/**
 * Bridges configured ARD vector index plugin to {@link com.alibaba.nacos.core.plugin.PluginManager}.
 *
 * @author nacos
 */
public class AiVectorPluginProvider implements PluginProvider<AiResourceVectorIndex> {
    
    @Override
    public PluginType getPluginType() {
        return PluginType.AI_VECTOR;
    }
    
    @Override
    public Map<String, AiResourceVectorIndex> getAllPlugins() {
        if (ApplicationUtils.getApplicationContext() == null) {
            return Collections.emptyMap();
        }
        try {
            return ApplicationUtils.getBean(ArdVectorIndexRouter.class).selectedIndex();
        } catch (BeansException | IllegalStateException ignored) {
            return Collections.emptyMap();
        }
    }
}
