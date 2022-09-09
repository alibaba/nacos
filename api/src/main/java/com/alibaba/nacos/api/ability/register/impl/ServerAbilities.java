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

package com.alibaba.nacos.api.ability.register.impl;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.register.AbstractAbilityRegistry;

import java.util.Map;

/**.
 * @author Daydreamer
 * @description It is used to register server abilities.
 * @date 2022/8/31 12:32
 **/
public class ServerAbilities extends AbstractAbilityRegistry {
    
    private static final ServerAbilities INSTANCE = new ServerAbilities();
    
    {
        /*
         * example:
         *   There is a function named "compression".
         *   The key is from <p>AbilityKey</p>, the value is whether turn on.
         *
         *   You can add a new public field in <p>AbilityKey</p> like:
         *       <code>DATA_COMPRESSION("compression", 1)</code>
         *   This field can be used outside, and the offset should be unique.
         *
         *   And then you need to declare the offset of the flag bit of this ability in the ability table, you can:
         *       <code>supportedAbilities.put(AbilityKey.DATA_COMPRESSION, true);</code> means that is the first bit from left to right in the table.
         *
         */
        // put ability here, which you want current server supports
        supportedAbilities.put(AbilityKey.TEST_1, true);
        supportedAbilities.put(AbilityKey.TEST_2, true);
    }
    
    /**.
     * get static ability current server supports
     *
     * @return static ability
     */
    public static Map<AbilityKey, Boolean> getStaticAbilities() {
        return INSTANCE.getSupportedAbilities();
    }
    
}
