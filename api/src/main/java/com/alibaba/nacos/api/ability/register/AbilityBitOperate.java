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

package com.alibaba.nacos.api.ability.register;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.utils.AbilityTableUtils;

import java.util.HashMap;
import java.util.Map;

/**.
 * @author Daydreamer
 * @description Operation for bit table.
 * @date 2022/7/12 19:23
 **/
public abstract class AbilityBitOperate {
    
    protected final HashMap<AbilityKey, Integer> abilityOffset = new HashMap<>();

    private byte[] abilityBitFlag;
    
    /**.
     * Return the static ability bit table
     *
     * @return ability bit table
     */
    public byte[] getAbilityBitFlags() {
        return abilityBitFlag.clone();
    }

    /**.
     * Return the ability bit offsets
     *
     * @return bit offset
     */
    public Map<AbilityKey, Integer> offset() {
        return abilityOffset;
    }
    
    /**
     * put the bit offset to {@link AbilityBitOperate#abilityBitFlag}
     */
    protected void init() {
        // init the bits table
        abilityBitFlag = AbilityTableUtils.getAbilityBitBy(abilityOffset.values());
    }
}
