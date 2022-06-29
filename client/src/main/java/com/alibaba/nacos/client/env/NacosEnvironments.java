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

import com.alibaba.nacos.client.constant.Constants;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Properties;

/**
 * nacos env.
 * @author onewe
 */
public class NacosEnvironments {
    
    private static final UserCustomizableEnvironment USER_CUSTOMIZABLE_ENVIRONMENT = UserCustomizableEnvironment.getInstance();
    
    private static final JvmArgumentsEnvironment JVM_ARGUMENTS_ENVIRONMENT = JvmArgumentsEnvironment.getInstance();
    
    private static final SystemEnvironment SYSTEM_ENVIRONMENT = SystemEnvironment.getInstance();
    
    private static final EnvironmentSearch ENVIRONMENT_SEARCH;
    
    static {
        
        String searchPattern = JVM_ARGUMENTS_ENVIRONMENT.getProperty(Constants.SysEnv.NACOS_ENVS_SEARCH);
        if (StringUtils.isBlank(searchPattern)) {
            searchPattern = SYSTEM_ENVIRONMENT.getProperty(Constants.SysEnv.NACOS_ENVS_SEARCH.toUpperCase().replace('.', '_'));
        }
        ENVIRONMENT_SEARCH = EnvironmentSearch
                .Formatter
                .of(searchPattern, USER_CUSTOMIZABLE_ENVIRONMENT, JVM_ARGUMENTS_ENVIRONMENT, SYSTEM_ENVIRONMENT)
                .parse();
    }
    
    /**
     * get property, if the value can not be got by the special key, the default value will be returned.
     * @param key special key
     * @param defaultValue default value
     * @return string value or default value.
     */
    public static String getProperty(String key, String defaultValue) {
        return ENVIRONMENT_SEARCH.search(environment -> environment.getProperty(key), defaultValue);
    }
    
    /**
     * get property, if the value can not be got by the special key, the null will be returned.
     * @param key special key
     * @return string value or null.
     */
    public static String getProperty(String key) {
        return ENVIRONMENT_SEARCH.search(environment -> environment.getProperty(key));
    }
    
    /**
     * get boolean, if the value can not be got by the special key, the null will be returned.
     * @param key special key
     * @return boolean value or null.
     */
    public static Boolean getBoolean(String key) {
        return ENVIRONMENT_SEARCH.search(environment -> environment.getBoolean(key));
    }
    
    /**
     * get boolean, if the value can not be got by the special key, the default value will be returned.
     * @param key special key
     * @param defaultValue default value
     * @return boolean value or defaultValue.
     */
    public static Boolean getBoolean(String key, Boolean defaultValue) {
        return ENVIRONMENT_SEARCH.search(environment -> environment.getBoolean(key), defaultValue);
    }
    
    /**
     * get integer, if the value can not be got by the special key, the null will be returned.
     * @param key special key
     * @return integer value or null
     */
    public static Integer getInteger(String key) {
        return ENVIRONMENT_SEARCH.search(environment -> environment.getInteger(key));
    }
    
    /**
     * get integer, if the value can not be got by the special key, the default value will be returned.
     * @param key special key
     * @param defaultValue default value
     * @return integer value or default value
     */
    public static Integer getInteger(String key, Integer defaultValue) {
        return ENVIRONMENT_SEARCH.search(environment -> environment.getInteger(key), defaultValue);
    }
    
    /**
     * get long, if the value can not be got by the special key, the null will be returned.
     * @param key special key
     * @return long value or null
     */
    public static Long getLong(String key) {
        return ENVIRONMENT_SEARCH.search(environment -> environment.getLong(key));
    }
    
    /**
     * get long, if the value can not be got by the special key, the default value will be returned.
     * @param key special key
     * @param defaultValue default value
     * @return long value or default value
     */
    public static Long getLong(String key, Long defaultValue) {
        return ENVIRONMENT_SEARCH.search(environment -> environment.getLong(key), defaultValue);
    }
    
    /**
     * set value to environment.
     * the value will be overridden if the special key exist.
     * @param key special key
     * @param value string value
     */
    public static void setProperty(String key, String value) {
        USER_CUSTOMIZABLE_ENVIRONMENT.setProperty(key, value);
    }
    
    /**
     * add properties into environment.
     * the value will be overridden if the special key exist.
     * @param properties properties
     */
    public static void addProperties(Properties properties) {
        USER_CUSTOMIZABLE_ENVIRONMENT.addProperties(properties);
    }
    
}
