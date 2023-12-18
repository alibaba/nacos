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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ability key constant. It is used to constrain the ability key.<br/>
 * <strong>Ensure that return value of {@link AbilityKey#getName()} is unique under one specify {@link AbilityMode}</strong>.
 *
 * @author Daydreamer
 * @date 2022/8/31 12:27
 **/
public enum AbilityKey {

    /**
     * Server support register or deregister persistent instance by grpc.
     */
    SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC("supportPersistentInstanceByGrpc",
            "support persistent instance by grpc", AbilityMode.SERVER),
    
    /**
     * For Test temporarily.
     */
    SERVER_TEST_1("test_1", "just for junit test", AbilityMode.SERVER),
    
    /**
     * For Test temporarily.
     */
    SERVER_TEST_2("test_2", "just for junit test", AbilityMode.SERVER),
    
    /**
     * For Test temporarily.
     */
    SDK_CLIENT_TEST_1("test_1", "just for junit test", AbilityMode.SDK_CLIENT),
    
    /**
     * For Test temporarily.
     */
    CLUSTER_CLIENT_TEST_1("test_1", "just for junit test", AbilityMode.CLUSTER_CLIENT);
    
    /**
     * the name of a certain ability.
     */
    private final String keyName;

    /**
     * description or comment about this ability.
     */
    private final String description;

    /**
     * ability mode, which endpoint hold this ability.
     */
    private final AbilityMode mode;

    AbilityKey(String keyName, String description, AbilityMode mode) {
        this.keyName = keyName;
        this.description = description;
        this.mode = mode;
    }

    public String getName() {
        return keyName;
    }

    public String getDescription() {
        return description;
    }

    public AbilityMode getMode() {
        return mode;
    }

    /**
     * All key set.
     */
    private static final Map<AbilityMode, Map<String, AbilityKey>> ALL_ABILITIES = new HashMap<>();
    
    /**
     * Get all keys.
     *
     * @return all keys
     */
    public static Collection<AbilityKey> getAllValues(AbilityMode mode) {
        return Collections.unmodifiableCollection(ALL_ABILITIES.get(mode).values());
    }
    
    /**
     * Get all names.
     *
     * @return all names
     */
    public static Collection<String> getAllNames(AbilityMode mode) {
        return Collections.unmodifiableCollection(ALL_ABILITIES.get(mode).keySet());
    }
    
    /**
     * Whether contains this name.
     *
     * @param name key name
     * @return whether contains
     */
    public static boolean isLegalKey(AbilityMode mode, String name) {
        return ALL_ABILITIES.get(mode).containsKey(name);
    }
    
    /**
     * Map the string key to enum.
     *
     * @param abilities map
     * @return enum map
     */
    public static Map<AbilityKey, Boolean> mapEnum(AbilityMode mode, Map<String, Boolean> abilities) {
        if (abilities == null || abilities.isEmpty()) {
            return Collections.emptyMap();
        }
        return abilities.entrySet()
                .stream()
                .filter(entry -> isLegalKey(mode, entry.getKey()))
                .collect(Collectors.toMap((entry) -> getEnum(mode, entry.getKey()), Map.Entry::getValue));
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
    public static AbilityKey getEnum(AbilityMode mode, String key) {
        return ALL_ABILITIES.get(mode).get(key);
    }
    
    static {
        // check for developer
        // ensure that name filed is unique under a AbilityMode
        try {
            for (AbilityKey value : AbilityKey.values()) {
                AbilityMode mode = value.getMode();
                Map<String, AbilityKey> map = ALL_ABILITIES.getOrDefault(mode, new HashMap<>());
                AbilityKey previous = map.putIfAbsent(value.getName(), value);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate key name field " + value + " and " + previous + " under mode: " + mode);
                }
                ALL_ABILITIES.put(mode, map);
            }
        } catch (Throwable t) {
            // for developer checking
            t.printStackTrace();
        }
    }
}
