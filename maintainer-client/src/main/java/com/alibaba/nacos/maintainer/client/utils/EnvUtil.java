/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.maintainer.client.utils;

import com.alibaba.nacos.maintainer.client.constants.PropertyKeyConstants;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Its own configuration information manipulation tool class.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class EnvUtil {
    
    public static final String NACOS_HOME_KEY = "nacos.home";
    
    private static String contextPath = null;
    
    private static final String SERVER_PORT_PROPERTY = "server.port";
    
    private static final int DEFAULT_SERVER_PORT = 8848;
    
    private static final String DEFAULT_WEB_CONTEXT_PATH = "/nacos";
    
    private static final String ROOT_WEB_CONTEXT_PATH = "/";
    
    private static final String NACOS_HOME_PROPERTY = "user.home";
    
    private static final String DEFAULT_CONFIG_LOCATION = "application.properties";
    
    private static final String DEFAULT_RESOURCE_PATH = "/application.properties";
    
    private static final String NACOS_HOME_ADDITIONAL_FILEPATH = "nacos";
    
    @JustForTest
    private static String confPath = "";
    
    @JustForTest
    private static String nacosHomePath = null;
    
    private static ConfigurableEnvironment environment;
    
    public static ConfigurableEnvironment getEnvironment() {
        return environment;
    }
    
    public static void setEnvironment(ConfigurableEnvironment environment) {
        EnvUtil.environment = environment;
    }
    
    public static boolean containsProperty(String key) {
        return environment.containsProperty(key);
    }
    
    public static String getProperty(String key) {
        return environment.getProperty(key);
    }
    
    public static String getProperty(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }
    
    public static <T> T getProperty(String key, Class<T> targetType) {
        return environment.getProperty(key, targetType);
    }
    
    public static <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        return environment.getProperty(key, targetType, defaultValue);
    }
    
    public static String getRequiredProperty(String key) throws IllegalStateException {
        return environment.getRequiredProperty(key);
    }
    
    public static <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
        return environment.getRequiredProperty(key, targetType);
    }

    public static Properties getProperties() {
        Properties properties = new Properties();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> enumerablePropertySource = (EnumerablePropertySource<?>) propertySource;
                String[] propertyNames = enumerablePropertySource.getPropertyNames();
                for (String propertyName : propertyNames) {
                    Object propertyValue = enumerablePropertySource.getProperty(propertyName);
                    if (propertyValue != null) {
                        properties.put(propertyName, propertyValue.toString());
                    }
                }
            }
        }
        return properties;
    }
    
    public static List<String> getPropertyList(String key) {
        List<String> valueList = new ArrayList<>();
        
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            String value = environment.getProperty(key + "[" + i + "]");
            if (StringUtils.isBlank(value)) {
                break;
            }
            
            valueList.add(value);
        }
        
        return valueList;
    }
    
    public static String getContextPath() {
        if (Objects.isNull(contextPath)) {
            contextPath = getProperty(PropertyKeyConstants.NACOS_ADMIN_API_CONTEXT_PATH, DEFAULT_WEB_CONTEXT_PATH);
            if (ROOT_WEB_CONTEXT_PATH.equals(contextPath)) {
                contextPath = StringUtils.EMPTY;
            }
        }
        return contextPath;
    }
    
    public static void setContextPath(String contextPath) {
        EnvUtil.contextPath = contextPath;
    }
    
    public static String getNacosHome() {
        if (StringUtils.isBlank(nacosHomePath)) {
            String nacosHome = System.getProperty(NACOS_HOME_KEY);
            if (StringUtils.isBlank(nacosHome)) {
                nacosHome = Paths.get(System.getProperty(NACOS_HOME_PROPERTY), NACOS_HOME_ADDITIONAL_FILEPATH)
                        .toString();
            }
            return nacosHome;
        }
        // test-first
        return nacosHomePath;
    }
    
    @JustForTest
    public static void setNacosHomePath(String nacosHomePath) {
        EnvUtil.nacosHomePath = nacosHomePath;
    }
    
}
