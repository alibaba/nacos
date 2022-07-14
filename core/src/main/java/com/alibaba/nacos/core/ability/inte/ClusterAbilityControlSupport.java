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

package com.alibaba.nacos.core.ability.inte;

import com.alibaba.nacos.common.ability.handler.HandlerMapping;
import com.alibaba.nacos.common.ability.inter.AbilityControlManager;

import java.util.Map;

/**.
 * @author Daydreamer
 * @description It provides the capability to manage the AbilityTable in cluster for the {@link AbilityControlManager}
 * @date 2022/8/10 23:18
 **/
public interface ClusterAbilityControlSupport {
    
    /**.
     * Return the cluster abilities.
     *
     * @return the cluster abilities.
     */
    Map<String, Boolean> getClusterAbility();
    
    /**.
     * Register components for cluster. These will be trigger when its interested ability changes
     *
     * @param abilityKey     ability key
     * @param priority       a positive number, the higher the priority, the faster it will be called
     * @param handlerMapping component
     */
    void registerComponentForCluster(String abilityKey, HandlerMapping handlerMapping, int priority);
    
    /**.
     * Default method to register component
     *
     * @param abilityKey     component key from {@link com.alibaba.nacos.api.ability.constant.AbilityKey}.
     * @param handlerMapping component instance.
     */
    default void registerComponentForCluster(String abilityKey, HandlerMapping handlerMapping) {
        registerComponentForCluster(abilityKey, handlerMapping, 1);
    }
    
    /**
     * Remove the component instance of <p>handlerMappingClazz</p>.
     *
     * @param abilityKey ability key from {@link com.alibaba.nacos.api.ability.constant.AbilityKey}
     * @param handlerMappingClazz implement of {@link HandlerMapping}
     * @return the count of components have removed
     */
    int removeClusterComponent(String abilityKey, Class<? extends HandlerMapping> handlerMappingClazz);
    
    /**
     * Remove all {@link HandlerMapping} interested in the special ability.
     * @param abilityKey abnility key from {@link com.alibaba.nacos.api.ability.constant.AbilityKey}
     * @return the count of components have removed
     */
    int removeAllForCluster(String abilityKey);
    
    /**.
     * Whether current cluster supports ability
     *
     * @param abilityKey ability key
     * @return whether it is turn on
     */
    boolean isClusterEnableAbility(String abilityKey);
}
