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

import java.util.Properties;

/**
 * NacosClientProperties interface. include all the properties from jvm args, system environment, default setting. more
 * details you can see https://github.com/alibaba/nacos/issues/8622
 *
 * @author onewe
 */
public interface NacosClientProperties {
    
    /**
     * all the NacosClientProperties object must be created by PROTOTYPE, so child NacosClientProperties can read
     * properties from the PROTOTYPE. it looks like this: |-PROTOTYPE----------------> ip=127.0.0.1
     * |---|-child1---------------> port=6379 if you search key called "port" from child1, certainly you will get 6379
     * if you search key called "ip" from child1, you will get 127.0.0.1. because the child can read properties from
     * parent NacosClientProperties
     */
    NacosClientProperties PROTOTYPE = SearchableProperties.INSTANCE;
    
    /**
     * get property, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return string value or null.
     */
    String getProperty(String key);
    
    /**
     * get property, if the value can not be got by the special key, the default value will be returned.
     *
     * @param key          special key
     * @param defaultValue default value
     * @return string value or default value.
     */
    String getProperty(String key, String defaultValue);
    
    /**
     * get property from special property source.
     *
     * @param source source type
     * @param key    special key
     * @return string value or null.
     * @see SourceType
     */
    String getPropertyFrom(SourceType source, String key);
    
    /**
     * get property from special property source.
     *
     * @param source source type
     * @return string value or null.
     * @see SourceType
     */
    Properties getProperties(SourceType source);
    
    /**
     * get boolean, if the value can not be got by the special key, the null will be returned.
     *
     * @param key special key
     * @return boolean value or null.
     */
    Boolean getBoolean(String key);
    
    /**
     * get boolean, if the value can not be got by the special key, the default value will be returned.
     *
     * @param key          special key
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
     *
     * @param key          special key
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
     *
     * @param key          special key
     * @param defaultValue default value
     * @return long value or default value
     */
    Long getLong(String key, Long defaultValue);
    
    /**
     * set property.
     *
     * @param key   key
     * @param value value
     */
    void setProperty(String key, String value);
    
    /**
     * add properties.
     *
     * @param properties properties
     */
    void addProperties(Properties properties);
    
    /**
     * Tests if the specified object is a key in this NacosClientProperties.
     *
     * @param key key – possible key
     * @return true if and only if the specified object is a key in this NacosClientProperties, false otherwise.
     */
    boolean containsKey(String key);
    
    /**
     * get properties from NacosClientProperties.
     *
     * @return properties
     */
    Properties asProperties();
    
    /**
     * create a new NacosClientProperties which scope is itself.
     *
     * @return NacosClientProperties
     */
    NacosClientProperties derive();
    
    /**
     * create a new NacosClientProperties from NacosClientProperties#PROTOTYPE and init.
     *
     * @param properties properties
     * @return NacosClientProperties
     */
    NacosClientProperties derive(Properties properties);
}
