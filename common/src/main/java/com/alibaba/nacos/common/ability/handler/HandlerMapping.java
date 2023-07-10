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

package com.alibaba.nacos.common.ability.handler;

/**.
 * @author Daydreamer
 * @description This component will be invoked if the ability of current node is turned on/off.
 * @date 2022/7/12 19:21
 **/
public interface HandlerMapping {

    /**.
     * It will be invoked in order to enable this component after update the
     * ability table key to true
     */
    default void enable() {
        // Nothing to do!
    }
    
    /**.
     * It will be invoked in order to disable this component after update the
     * ability table key to false
     */
    default void disable() {
        // Nothing to do!
    }
}
