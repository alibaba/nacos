/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ability.constant;

/**
 * Ability mode.
 *
 * @author Daydreamer
 * @date 2023/9/25 12:32
 **/
public enum AbilityMode {

    /**
     * for server ability.
     */
    SERVER,

    /**
     * for sdk client.
     */
    SDK_CLIENT,

    /**
     * for cluster client.
     */
    CLUSTER_CLIENT;
}
