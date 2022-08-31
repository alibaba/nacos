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

package com.alibaba.nacos.api.utils;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.register.AbilityBitOperate;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**.
 * @author Daydreamer
 * @description It is used to operate ability table.
 * @date 2022/7/12 19:23
 **/
public class AbilityTableUtils {

    private static final int BASE = 128;

    private AbilityTableUtils() {
    }

    /**.
     * get ability bit table from Collection
     *
     * @param bitCollection bit offset
     * @return bit table
     */
    public static byte[] getAbilityBitBy(Collection<Integer> bitCollection) {
        if (bitCollection.size() == 0) {
            return new byte[1];
        }
        Integer max = Collections.max(bitCollection);
        // calculate byte[]
        int mark = max % 8;
        int length = max / 8 + (mark == 0 ? 0 : 1);
        byte[] res = new byte[length];
        bitCollection.forEach(offset -> {
            int index = offset / 8 + (offset % 8 == 0 ? -1 : 0);
            int flag = offset % 8;
            if (flag == 0) {
                flag = 8;
            }
            byte x = (byte) (BASE >>> (flag - 1));
            res[index] = (byte) (res[index] | x);
        });
        return res;
    }
    
    /**.
     * get ability table by bits
     *
     * @param bits      bit flag
     * @param offsetMap offset from {@link AbilityBitOperate}
     * @return Return the Map containing AbilityTableKey and isRunning.
     */
    public static Map<AbilityKey, Boolean> getAbilityTableBy(byte[] bits, Map<AbilityKey, Integer> offsetMap) {
        if (bits == null || offsetMap.size() == 0) {
            return Collections.emptyMap();
        }
        int length = bits.length;
        Set<Map.Entry<AbilityKey, Integer>> entries = offsetMap.entrySet();
        Map<AbilityKey, Boolean> res = new HashMap<>(offsetMap.size());
        for (Map.Entry<AbilityKey, Integer> entry : entries) {
            AbilityKey abilityKey = entry.getKey();
            Integer offset = entry.getValue();
            // if not exists
            int index = offset / 8 + (offset % 8 == 0 ? -1 : 0);
            if (index + 1 > length) {
                res.put(abilityKey, Boolean.FALSE);
                continue;
            }
            // find
            int flag = offset % 8;
            if (flag == 0) {
                flag = 8;
            }
            byte x = (byte) (BASE >>> (flag - 1));
            byte tmp = (byte) (x & bits[index]);
            res.put(abilityKey, x == tmp);
        }
        return res;
    }
    
    /**.
     * get ability bit table by existed ability table and offset map
     *
     * @param offsetMap offset from {@link AbilityBitOperate}
     * @return Return the Map containing AbilityTableKey and isRunning.
     */
    public static byte[] getAbilityBiTableBy(Map<AbilityKey, Integer> offsetMap, Map<AbilityKey, Boolean> abilityTable) {
        // filter the element which <code>abilityTable</code> don't have or value is false
        Map<AbilityKey, Integer> res = offsetMap.entrySet().stream()
                .filter(item -> abilityTable.getOrDefault(item.getKey(), false))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return getAbilityBitBy(res.values());
    }
}
