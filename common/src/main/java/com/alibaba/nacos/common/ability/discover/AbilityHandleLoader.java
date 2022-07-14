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

package com.alibaba.nacos.common.ability.discover;

import com.alibaba.nacos.common.ability.handler.AbilityHandlePreProcessor;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;

/**.
 * @author Daydreamer
 * @description It is spi loader to load {@link AbilityHandlePreProcessor}
 * @date 2022/8/25 18:24
 **/
public class AbilityHandleLoader {
    
    private final Collection<AbilityHandlePreProcessor> initializers;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbilityHandleLoader.class);
    
    public AbilityHandleLoader() {
        initializers = new HashSet<>();
        for (AbilityHandlePreProcessor preProcessor : NacosServiceLoader.load(AbilityHandlePreProcessor.class)) {
            initializers.add(preProcessor);
            LOGGER.info("Load {} for AbilityHandlePreProcessor", preProcessor.getClass().getCanonicalName());
        }
    }
    
    public Collection<AbilityHandlePreProcessor> getInitializers() {
        return initializers;
    }
}
