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

package com.alibaba.nacos.common.ability.inter;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.register.AbstractAbilityBitOperate;
import com.alibaba.nacos.common.ability.AbstractAbilityControlManager;
import com.alibaba.nacos.common.ability.handler.HandlerMapping;

/**.
 * @author Daydreamer
 * @description It provides the capability to notify components which interested in one ability for the {@link AbilityControlManager}
 * @date 2022/8/10 23:43
 **/
public interface AbilityHandlerRegistry {
    
    /**.
     * Turn on the ability whose key is <p>abilityKey</p>
     *
     * @param abilityKey ability key
     * @return if turn success
     */
    boolean enableCurrentNodeAbility(AbilityKey abilityKey);
    
    /**.
     * Turn off the ability whose key is <p>abilityKey</p>
     *
     * @param abilityKey ability key
     * @return if turn success
     */
    boolean disableCurrentNodeAbility(AbilityKey abilityKey);
    
    /**.
     * Register the component which is managed by {@link AbstractAbilityControlManager}.
     * if you are hoping that a component will be invoked when turn on/off the ability whose key is <p>abilityKey</p>.
     *
     * @param abilityKey     component key from {@link AbstractAbilityBitOperate}
     * @param priority       a positive number, the higher the priority is, the faster it will be called. `1` is the lowest priority.
     * @param handlerMapping component instance.
     */
    void registerComponent(AbilityKey abilityKey, HandlerMapping handlerMapping, int priority);
    
    /**.
     * Default method to register component with the lowest priority.
     *
     * @param abilityKey     component key from {@link AbstractAbilityBitOperate}
     * @param handlerMapping component instance.
     */
    default void registerComponent(AbilityKey abilityKey, HandlerMapping handlerMapping) {
        registerComponent(abilityKey, handlerMapping, 1);
    }
    
    /**
     * Remove the component instance of <p>handlerMappingClazz</p>.
     *
     * @param abilityKey ability key from {@link AbstractAbilityBitOperate}
     * @param handlerMappingClazz implement of {@link HandlerMapping}
     * @return the count of components have removed
     */
    int removeComponent(AbilityKey abilityKey, Class<? extends HandlerMapping> handlerMappingClazz);
    
    /**
     * Remove all {@link HandlerMapping} interested in the special ability.
     * @param abilityKey abnility key from {@link AbstractAbilityBitOperate}
     * @return the count of components have removed
     */
    int removeAll(AbilityKey abilityKey);
    
}
