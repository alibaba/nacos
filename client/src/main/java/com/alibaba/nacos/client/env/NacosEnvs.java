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

package com.alibaba.nacos.client.env;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * environment utils.
 *
 * @author onewe
 */
public class NacosEnvs {
    
    private static final Map<ApplyScope, SearchableEnvironment> ENVIRONMENT_MAP = new HashMap<>(4);
    
    static {
        ENVIRONMENT_MAP.put(ApplyScope.GLOBAL, new SearchableEnvironment());
        ENVIRONMENT_MAP.put(ApplyScope.CONFIG, new SearchableEnvironment() {
            @Override
            protected ApplyScope getScope() {
                return ApplyScope.CONFIG;
            }
        });
        
        ENVIRONMENT_MAP.put(ApplyScope.NAMING, new SearchableEnvironment() {
            @Override
            protected ApplyScope getScope() {
                return ApplyScope.NAMING;
            }
        });
    
        ENVIRONMENT_MAP.put(ApplyScope.NAMING_MAINTAIN, new SearchableEnvironment() {
            @Override
            protected ApplyScope getScope() {
                return ApplyScope.NAMING_MAINTAIN;
            }
        });
        
    }
    
    public static String getProperty(String key, String defaultValue) {
        return apply(ApplyScope.GLOBAL).getProperty(key, defaultValue);
    }
    
    /**
     * get property, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return string value or null.
     */
    public static String getProperty(String key) {
        return apply(ApplyScope.GLOBAL).getProperty(key);
    }
    
    /**
     * get boolean, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return boolean value or null.
     */
    public static Boolean getBoolean(String key) {
        return apply(ApplyScope.GLOBAL).getBoolean(key);
    }
    
    /**
     * get boolean, if the value can not be got by the special key, the default value will be returned.
     *
     * @param key          special key
     * @param defaultValue default value
     * @return boolean value or defaultValue.
     */
    public static Boolean getBoolean(String key, Boolean defaultValue) {
        return apply(ApplyScope.GLOBAL).getBoolean(key, defaultValue);
    }
    
    /**
     * get integer, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return integer value or null
     */
    public static Integer getInteger(String key) {
        return apply(ApplyScope.GLOBAL).getInteger(key);
    }
    
    /**
     * get integer, if the value can not be got by the special key, the default value will be returned.
     *
     * @param key          special key
     * @param defaultValue default value
     * @return integer value or default value
     */
    public static Integer getInteger(String key, Integer defaultValue) {
        return apply(ApplyScope.GLOBAL).getInteger(key, defaultValue);
    }
    
    /**
     * get long, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return long value or null
     */
    public static Long getLong(String key) {
        return apply(ApplyScope.GLOBAL).getLong(key);
    }
    
    /**
     * get long, if the value can not be got by the special key, the default value will be returned.
     *
     * @param key          special key
     * @param defaultValue default value
     * @return long value or default value
     */
    public static Long getLong(String key, Long defaultValue) {
        return apply(ApplyScope.GLOBAL).getLong(key, defaultValue);
    }
    
    public static void setProperty(String key, String value) {
        apply(ApplyScope.GLOBAL).setProperty(key, value);
    }
    
    public static void addProperties(Properties properties) {
        apply(ApplyScope.GLOBAL).addProperties(properties);
    }
    
    public static boolean containsKey(String key) {
        return apply(ApplyScope.GLOBAL).containsKey(key);
    }
    
    public static Properties asProperties() {
        return apply(ApplyScope.GLOBAL).asProperties();
    }
    
    /**
     * apply scope to environment.
     * @param applyScope scope
     * @return NacosEnvironment
     */
    public static NacosEnvironment apply(ApplyScope applyScope) {
        if (applyScope == null) {
            return ENVIRONMENT_MAP.get(ApplyScope.GLOBAL);
        }
        return ENVIRONMENT_MAP.get(applyScope);
    }
    
}
