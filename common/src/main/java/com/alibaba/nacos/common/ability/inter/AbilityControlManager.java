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
import com.alibaba.nacos.api.ability.entity.AbilityTable;
import com.alibaba.nacos.common.ability.handler.AbilityHandlePreProcessor;

import java.util.Map;

/**.
 * @author Daydreamer
 * @description This is a base interface to manage ability table
 * @date 2022/8/10 23:18
 **/
public interface AbilityControlManager {
    
    /**
     * Whether the ability is supported for Connection. If the ability of current node is closed, it will return false.
     *
     * @param connectionId the connection range of ability table.
     * @param abilityKey   key name which comes from {@link AbilityKey}.
     * @return whether the ability is supported in certain connection.
     */
    boolean isSupport(String connectionId, String abilityKey);
    
    /**
     * Whether the ability current node supporting is running. Return false if current node doesn't support.
     *
     * @param abilityKey ability key
     * @return is running
     */
    boolean isCurrentNodeAbilityRunning(String abilityKey);
    
    /**
     * Register a new ability table.
     *
     * @param table the ability table.
     */
    void addNewTable(AbilityTable table);
    
    /**.
     * Remove a ability table
     *
     * @param connectionId the ability table which is removing.
     */
    void removeTable(String connectionId);
    
    /**.
     * whether contains this ability table
     *
     * @param connectionId connection id
     * @return whether contains
     */
    boolean contains(String connectionId);
    
    /**.
     * Return ability table of current node
     *
     * @return ability table
     */
    Map<String, Boolean> getCurrentRunningAbility();
    
    /**.
     * They will be invoked before updating ability table, but the order in which
     * they are called cannot be guaranteed
     *
     * @param postProcessor PostProcessor instance
     */
    void addPostProcessor(AbilityHandlePreProcessor postProcessor);
    
    /**.
     * Initialize the manager
     */
    void init();
    
    /**.
     * It should be invoked before destroy
     */
    void destroy();
}
