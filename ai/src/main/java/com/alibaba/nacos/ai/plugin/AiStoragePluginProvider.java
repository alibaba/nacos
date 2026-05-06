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

import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges {@link AiResourceStorage} instances registered on {@link AiResourceStorageRouter}
 * to {@link com.alibaba.nacos.core.plugin.PluginManager}.
 *
 * <p>AI skill/resource persistence <b>requires</b> a storage backend; the default implementation is
 * {@code nacos_config} ({@link com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorageBuilder}),
 * registered when its builder succeeds. Additional providers may appear as SPI.</p>
 *
 * <p>Listing and enable/disable in {@code PluginManager} are not yet wired into {@link AiResourceStorageRouter};
 * disabling here does not affect read/write routing until that integration exists.</p>
 *
 * @author nacos
 */
public class AiStoragePluginProvider implements PluginProvider<AiResourceStorage> {
    
    @Override
    public PluginType getPluginType() {
        return PluginType.AI_STORAGE;
    }
    
    @Override
    public Map<String, AiResourceStorage> getAllPlugins() {
        Map<String, AiResourceStorage> map = new LinkedHashMap<>();
        AiResourceStorageRouter.getInstance().allStorages().forEach((type, storage) -> {
            if (type != null && storage != null) {
                map.put(type, storage);
            }
        });
        return map;
    }
}
