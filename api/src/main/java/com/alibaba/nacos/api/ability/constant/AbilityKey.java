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
import java.util.Collection;
import java.util.Collections;
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
    TEST_1("test_1"),
    
    /**.
     * just for junit test
     */
    TEST_2("test_2");
    
    /**.
     * the name of a certain ability
     */
    private final String keyName;
    
    AbilityKey(String name) {
        this.keyName = name;
    }
    
    public String getName() {
        return keyName;
    }
    
    /**.
     * All key set
     */
    private static final Map<String, AbilityKey> ALL_ABILITIES;
    
    /**.
     * Get all keys
     *
     * @return all keys
     */
    public static Collection<AbilityKey> getAllValues() {
        return Collections.unmodifiableCollection(ALL_ABILITIES.values());
    }
    
    /**.
     * Get all names
     *
     * @return all names
     */
    public static Collection<String> getAllNames() {
        return Collections.unmodifiableCollection(ALL_ABILITIES.keySet());
    }
    
    /**.
     * Whether contains this name
     *
     * @param name key name
     * @return whether contains
     */
    public static boolean isLegalKey(String name) {
        return ALL_ABILITIES.containsKey(name);
    }
    
    /**.
     * Map the string key to enum
     *
     * @param abilities map
     * @return enum map
     */
    public static Map<AbilityKey, Boolean> mapEnum(Map<String, Boolean> abilities) {
        if (abilities == null || abilities.isEmpty()) {
            return Collections.emptyMap();
        }
        return abilities.entrySet()
                .stream()
                .filter(entry -> isLegalKey(entry.getKey()))
                .collect(Collectors.toMap((entry) -> getEnum(entry.getKey()), Map.Entry::getValue));
    }
    
    /**.
     * Map the string key to enum
     *
     * @param abilities map
     * @return enum map
     */
    public static Map<String, Boolean> mapStr(Map<AbilityKey, Boolean> abilities) {
        if (abilities == null || abilities.isEmpty()) {
            return Collections.emptyMap();
        }
        return abilities.entrySet()
                .stream()
                .collect(Collectors.toMap((entry) -> entry.getKey().getName(), Map.Entry::getValue));
    }
    
    /**.
     * getter to obtain enum
     *
     * @param key string key
     * @return enum
     */
    public static AbilityKey getEnum(String key) {
        return ALL_ABILITIES.get(key);
    }
    
    static {
        ALL_ABILITIES = Arrays.stream(AbilityKey.values()).collect(Collectors.toMap(AbilityKey::getName, Function.identity()));
    }
}
