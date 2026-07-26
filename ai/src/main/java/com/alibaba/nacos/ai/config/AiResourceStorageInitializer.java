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
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Initializer for {@link AiResourceStorage} implementations.
 *
 * <p>This component bridges SPI-based storage builders with Spring lifecycle.
 * It loads all {@link AiResourceStorageBuilder} SPI implementations when the root
 * {@link org.springframework.context.ApplicationContext} finishes refresh (before
 * {@link org.springframework.boot.context.event.ApplicationReadyEvent}), then
 * registers the built storages via {@link AiResourceStorageRouter#join(AiResourceStorage)}.
 * This ordering ensures {@link com.alibaba.nacos.core.plugin.PluginManager} can discover
 * AI storage plugins on startup.</p>
 *
 * <p><b>Why {@link ContextRefreshedEvent}:</b> the same ordering could be done with
 * {@code ApplicationReadyEvent} plus {@code @Order}, but refresh completes after non-lazy
 * singletons exist, so {@code ApplicationUtils.getBean} in builders (e.g. config services)
 * is safe; it also runs strictly before {@code ApplicationReadyEvent}.</p>
 *
 * <p><b>Repeat refresh / hierarchy:</b> registration runs at most once per initializer bean
 * (typical single root context). Skips non-root contexts ({@code getParent() != null}) to
 * avoid duplicate work in parent/child setups. A second {@code refresh()} on the same root
 * context is ignored after the first successful pass.</p>
 *
 * @author nacos
 * @since 3.2.0
 */
@Component
public class AiResourceStorageInitializer implements ApplicationListener<ContextRefreshedEvent> {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(AiResourceStorageInitializer.class);
    
    private final AtomicBoolean registered = new AtomicBoolean(false);
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        if (!registered.compareAndSet(false, true)) {
            return;
        }
        try {
            Collection<AiResourceStorageBuilder> builders =
                NacosServiceLoader.load(AiResourceStorageBuilder.class);
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
                    LOGGER.warn("Failed to build AiResourceStorage for type: {}", builder.type(),
                        e);
                }
            }
        } catch (Throwable e) {
            registered.set(false);
            LOGGER.error(
                "[AiResourceStorageInitializer] Unexpected failure while registering storages", e);
        }
    }
}
