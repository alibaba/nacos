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

import com.alibaba.nacos.common.ability.AbstractAbilityControlManager;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is used to discover {@link AbstractAbilityControlManager} implements. All the ability operation will be
 * finish in this singleton.
 *
 * @author Daydreamer
 * @date 2022/7/14 19:58
 **/
public class NacosAbilityManagerHolder {
    
    /**
     * . private constructor
     */
    private NacosAbilityManagerHolder() {
    }
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosAbilityManagerHolder.class);
    
    /**
     * . singleton
     */
    private static AbstractAbilityControlManager abstractAbilityControlManager;
    
    /**
     * . get nacos ability control manager
     *
     * @return BaseAbilityControlManager
     */
    public static synchronized AbstractAbilityControlManager getInstance() {
        if (null == abstractAbilityControlManager) {
            initAbilityControlManager();
        }
        return abstractAbilityControlManager;
    }
    
    /**
     * . Return the target type of ability manager
     *
     * @param clazz clazz
     * @param <T>   target type
     * @return AbilityControlManager
     */
    public static <T extends AbstractAbilityControlManager> T getInstance(Class<T> clazz) {
        return clazz.cast(abstractAbilityControlManager);
    }
    
    private static void initAbilityControlManager() {
        // spi discover implement
        Collection<AbstractAbilityControlManager> load = null;
        load = NacosServiceLoader.load(AbstractAbilityControlManager.class);
        // the priority of the server is higher
        List<AbstractAbilityControlManager> collect = load.stream()
                .sorted(Comparator.comparingInt(AbstractAbilityControlManager::getPriority))
                .collect(Collectors.toList());
        // get the highest priority one
        if (load.size() > 0) {
            abstractAbilityControlManager = collect.get(collect.size() - 1);
            LOGGER.info("[AbilityControlManager] Successfully initialize AbilityControlManager");
        }
    }
}
