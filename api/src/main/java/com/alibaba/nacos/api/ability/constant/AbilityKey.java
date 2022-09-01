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

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**.
 * @author Daydreamer
 * @description Ability key constant.
 * @date 2022/8/31 12:27
 **/
public enum AbilityKey {
    
    /**.
     * just for junit test
     */
    TEST_1("test_1", 1),
    
    /**.
     * just for junit test
     */
    TEST_2("test_2", 2);
    
    /**.
     * the name of a certain ability
     */
    private final String name;
    
    /**.
     * the offset in ability table
     */
    private final int offset;
    
    AbilityKey(String name, int offset) {
        this.name = name;
        this.offset = offset;
    }
    
    public String getName() {
        return name;
    }
    
    public int getOffset() {
        return offset;
    }
    
    private static final Map<AbilityKey, Integer> OFFSET_MAP;
    
    public static Map<AbilityKey, Integer> offset() {
        return OFFSET_MAP;
    }
    
    static {
        OFFSET_MAP = Arrays.stream(AbilityKey.values())
                .collect(Collectors.toMap(Function.identity(), AbilityKey::getOffset));
    }
}
