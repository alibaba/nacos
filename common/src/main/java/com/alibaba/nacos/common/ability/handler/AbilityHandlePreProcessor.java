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

import com.alibaba.nacos.api.ability.entity.AbilityTable;

/**.
 * @author Daydreamer
 * @description This handler will should be invoked before ability table joining current node.
 * @date 2022/7/12 19:24
 **/
public interface AbilityHandlePreProcessor {

    /**
     * Handling before joining current node.
     *
     * @param source source ability handler
     * @return result table
     */
    AbilityTable handle(AbilityTable source);

}
