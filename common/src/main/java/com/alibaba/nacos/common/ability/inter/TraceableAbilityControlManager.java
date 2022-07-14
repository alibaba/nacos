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

import com.alibaba.nacos.api.ability.constant.AbilityStatus;

/**.
 * @author Daydreamer
 * @description It provides the capability to trace the state of AbilityTable for the {@link AbilityControlManager}
 * @date 2022/8/10 23:30
 **/
public interface TraceableAbilityControlManager extends AbilityControlManager {
    
    /**
     * Get the status of the ability table.
     *
     * @param connectionId connection id
     * @return status of ability table {@link AbilityStatus}
     */
    AbilityStatus trace(String connectionId);
    
    /**.
     * Trace the status of connection if {@link AbilityStatus#INITIALIZING}, wake up if {@link AbilityStatus#READY}
     * It will return if status is {@link AbilityStatus#EXPIRED} or {@link AbilityStatus#NOT_EXIST}
     *
     * @param connectionId connection id
     * @return if success to {@link AbilityStatus#READY}
     */
    boolean traceReadySyn(String connectionId);
}
