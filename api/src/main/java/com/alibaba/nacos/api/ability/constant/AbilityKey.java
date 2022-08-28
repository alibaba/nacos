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

package com.alibaba.nacos.api.ability.constant;

import com.alibaba.nacos.api.utils.AbilityTableUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**.
 * @author Daydreamer
 * @description Ability table key. It can be a replacement of {@link com.alibaba.nacos.api.ability.ServerAbilities}
 *              and {@link com.alibaba.nacos.api.ability.ClientAbilities}.
 * @date 2022/7/12 19:23
 **/
@SuppressWarnings("unchecked")
public class AbilityKey {

    private static final HashMap<String, Boolean> CURRENT_SERVER_SUPPORT_ABILITY = new HashMap<>();

    private static final HashMap<String, Integer> CURRENT_SERVER_ABILITY_OFFSET = new HashMap<>();

    private static final byte[] ABILITY_BIT_FLAGS;

    private AbilityKey() {
    }
    
    static {
        /*
         * example:
         *   There is a function named "compression".
         *   The key is "compression", the value is the offset of the flag bit of this ability in the ability table. The value should be unique.
         *
         *   You can add a new public static field like:
         *       <code>public static final String COMPRESSION = "compression";</code>
         *   This field can be used outside.
         *
         *   And then you need to declare the offset of the flag bit of this ability in the ability table, you can:
         *       <code>CURRENT_SERVER_ABILITY_OFFSET.put("compression", 1);</code> means that is the first bit from left to right in the table.
         *
         */

        // put ability here, which you want current server supports
        
    }
    
    /**.
     * Return ability table of current node
     * But this ability is static which means that this ability table is all function this node supports if no one to ask it to close some functions.
     * If you want to get what function current node is supporting, you should call AbilityControlManager#getCurrentAbility
     * By the way, AbilityControlManager is singleton, you can get it by static method
     *
     * @return ability table
     */
    public static Map<String, Boolean> getCurrentNodeSupportAbility() {
        return Collections.unmodifiableMap(CURRENT_SERVER_SUPPORT_ABILITY);
    }
    
    /**.
     * Return the static ability bit table
     *
     * @return ability bit table
     */
    public static byte[] getAbilityBitFlags() {
        return ABILITY_BIT_FLAGS.clone();
    }

    /**.
     * Is it a legal key
     *
     * @param key input
     * @return whether a legal key
     */
    public static boolean isLegal(String key) {
        return CURRENT_SERVER_SUPPORT_ABILITY.containsKey(key);
    }
    
    static {
        // init the bits table
        ABILITY_BIT_FLAGS = AbilityTableUtils.getAbilityBitBy(CURRENT_SERVER_ABILITY_OFFSET.values());
        // init the ability table, default all true
        CURRENT_SERVER_ABILITY_OFFSET.forEach((k, v) -> {
            CURRENT_SERVER_SUPPORT_ABILITY.put(k, Boolean.TRUE);
        });
    }

    /**.
     * Return the ability bit offsets
     *
     * @return bit offset
     */
    public static Map<String, Integer> offset() {
        return CURRENT_SERVER_ABILITY_OFFSET;
    }

}
