/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.storage;

import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorageBuilder;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * SPI builder for {@link NacosConfigAiResourceStorage}.
 */
public class NacosConfigAiResourceStorageBuilder implements AiResourceStorageBuilder {
    
    @Override
    public String type() {
        return NacosConfigAiResourceStorage.TYPE;
    }
    
    @Override
    public AiResourceStorage build() {
        ConfigQueryChainService queryChainService =
            ApplicationUtils.getBean(ConfigQueryChainService.class);
        ConfigOperationService operationService =
            ApplicationUtils.getBean(ConfigOperationService.class);
        SyncEffectService[] syncRef = new SyncEffectService[1];
        ApplicationUtils.getBeanIfExist(SyncEffectService.class, bean -> syncRef[0] = bean);
        return new NacosConfigAiResourceStorage(queryChainService, operationService, syncRef[0]);
    }
}
