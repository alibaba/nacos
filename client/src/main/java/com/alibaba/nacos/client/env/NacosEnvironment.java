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

/**
 * nacos env interface.
 *
 * @author onewe
 */
public interface NacosEnvironment {
    
    /**
     * get property, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return string value or null.
     */
    String getProperty(String key);
    
    /**
     * get property, if the value can not be got by the special key, the default value will be returned.
     * @param key special key
     * @param defaultValue default value
     * @return string value or default value.
     */
    String getProperty(String key, String defaultValue);
    
    /**
     * get boolean, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return boolean value or null.
     */
    Boolean getBoolean(String key);
    
    /**
     * get boolean, if the value can not be got by the special key, the default value will be returned.
     * @param key special key
     * @param defaultValue default value
     * @return boolean value or defaultValue.
     */
    Boolean getBoolean(String key, Boolean defaultValue);
    
    /**
     * get integer, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return integer value or null
     */
    Integer getInteger(String key);
    
    /**
     * get integer, if the value can not be got by the special key, the default value will be returned.
     * @param key special key
     * @param defaultValue default value
     * @return integer value or default value
     */
    Integer getInteger(String key, Integer defaultValue);
    
    /**
     * get long, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return long value or null
     */
    Long getLong(String key);
    
    /**
     * get long, if the value can not be got by the special key, the default value will be returned.
     * @param key special key
     * @param defaultValue default value
     * @return long value or default value
     */
    Long getLong(String key, Long defaultValue);
    
}
