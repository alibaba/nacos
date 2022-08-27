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

import java.lang.reflect.Proxy;
import java.util.Properties;

/**
 * environment utils.
 * @author onewe
 */
public class NacosEnvs {
    
    private static final NacosEnvironment ENVIRONMENT = NacosEnvironmentFactory.createEnvironment();
    
    /**
     * init environment.
     * @param properties properties
     */
    public static void init(Properties properties) {
        NacosEnvironmentFactory.NacosEnvironmentDelegate warrper = (NacosEnvironmentFactory.NacosEnvironmentDelegate) Proxy.getInvocationHandler(
                ENVIRONMENT);
        warrper.init(properties);
    }
    
    public static String getProperty(String key, String defaultValue) {
        return ENVIRONMENT.getProperty(key, defaultValue);
    }
    
    /**
     * get property, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return string value or null.
     */
    public static String getProperty(String key) {
        return ENVIRONMENT.getProperty(key);
    }
    
    /**
     * get boolean, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return boolean value or null.
     */
    public static Boolean getBoolean(String key) {
        return ENVIRONMENT.getBoolean(key);
    }
    
    /**
     * get boolean, if the value can not be got by the special key, the default value will be returned.
     *
     * @param key          special key
     * @param defaultValue default value
     * @return boolean value or defaultValue.
     */
    public static Boolean getBoolean(String key, Boolean defaultValue) {
        return ENVIRONMENT.getBoolean(key, defaultValue);
    }
    
    /**
     * get integer, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return integer value or null
     */
    public static Integer getInteger(String key) {
        return ENVIRONMENT.getInteger(key);
    }
    
    /**
     * get integer, if the value can not be got by the special key, the default value will be returned.
     *
     * @param key          special key
     * @param defaultValue default value
     * @return integer value or default value
     */
    public static Integer getInteger(String key, Integer defaultValue) {
        return ENVIRONMENT.getInteger(key, defaultValue);
    }
    
    /**
     * get long, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return long value or null
     */
    public static Long getLong(String key) {
        return ENVIRONMENT.getLong(key);
    }
    
    /**
     * get long, if the value can not be got by the special key, the default value will be returned.
     *
     * @param key          special key
     * @param defaultValue default value
     * @return long value or default value
     */
    public static Long getLong(String key, Long defaultValue) {
        return ENVIRONMENT.getLong(key, defaultValue);
    }
}
