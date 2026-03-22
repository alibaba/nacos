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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Initializer for {@link AiResourceStorage} implementations.
 *
 * <p>This component bridges SPI-based storage builders with Spring lifecycle.
 * It loads all {@link AiResourceStorageBuilder} SPI implementations after Spring
 * context is fully ready, then registers the built storages via
 * {@link AiResourceStorageRouter#join(AiResourceStorage)}.</p>
 *
 * @author nacos
 * @since 3.2.0
 */
@Component
public class AiResourceStorageInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiResourceStorageInitializer.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Collection<AiResourceStorageBuilder> builders = NacosServiceLoader.load(AiResourceStorageBuilder.class);
        for (AiResourceStorageBuilder builder : builders) {
            if (builder == null || StringUtils.isBlank(builder.type())) {
                continue;
            }
            try {
                AiResourceStorage storage = builder.build();
                if (storage != null) {
                    AiResourceStorageRouter.join(storage);
                    LOGGER.info("Registered AiResourceStorage: {}", storage.type());
                }
            } catch (Throwable e) {
                LOGGER.warn("Failed to build AiResourceStorage for type: {}", builder.type(), e);
            }
        }
    }
}
