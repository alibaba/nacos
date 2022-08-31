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
import com.alibaba.nacos.api.ability.register.AbilityBitOperate;

import java.util.Map;

/**.
 * @author Daydreamer
 * @description It is used to register client abilities.
 * @date 2022/8/31 12:32
 **/
public class ClientAbilities extends AbilityBitOperate {
    
    private static final ClientAbilities INSTANCE = new ClientAbilities();
    
    {
        /*
         * example:
         *   There is a function named "compression".
         *   The key is "compression", the value is the offset of the flag bit of this ability in the ability table. The value should be unique.
         *
         *   You can add a new public static field in <p>AbilityKeyConstant</p> like:
         *       <code>public static final String COMPRESSION = "compression";</code>
         *   This field can be used outside.
         *
         *   And then you need to declare the offset of the flag bit of this ability in the ability table, you can:
         *       <code>abilityOffset.put("compression", 1);</code> means that is the first bit from left to right in the table.
         *
         */
        // put ability here, which you want current server supports
    }
    
    private ClientAbilities() {
        // put key to bit offset
        init();
    }
    
    /**
     * get the ability offset for server
     *
     * @return  ability offset
     */
    public static byte[] getBitFlags() {
        return INSTANCE.getAbilityBitFlags();
    }
    
    /**
     * get the ability offset for server
     *
     * @return  ability offset
     */
    public static Map<AbilityKey, Integer> getOffset() {
        return INSTANCE.offset();
    }
}
