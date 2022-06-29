/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

import java.util.Properties;

/**
 * nacos env interface.
 *
 * @author onewe
 */
interface NacosEnvironment {
    
    /**
     * get environment type, it must be not null.
     *
     * @return EnvType
     */
    EnvType getType();
    
    /**
     * get property, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return string value or null.
     */
    String getProperty(String key);
    
    /**
     * get boolean, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return boolean value or false.
     */
    Boolean getBoolean(String key);
    
    /**
     * get integer, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return integer value or null
     */
    Integer getInteger(String key);
    
    /**
     * get long, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return long value or null
     */
    Long getLong(String key);
    
    /**
     * set value to environment. the value will be overridden if the special key exist.
     *
     * @param key   special key
     * @param value string value
     */
    void setProperty(String key, String value);
    
    /**
     * add properties into environment. the value will be overridden if the special key exist.
     *
     * @param properties properties
     */
    void addProperties(Properties properties);
    
    /**
     * remove property by special key.
     * @param key special key
     */
    void removeProperty(String key);
    
    /**
     * clean properties.
     */
    void clean();
    
}
