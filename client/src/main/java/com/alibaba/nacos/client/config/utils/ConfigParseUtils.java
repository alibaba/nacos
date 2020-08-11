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

package com.alibaba.nacos.client.config.utils;

import com.alibaba.nacos.client.config.utils.parse.DefaultJsonConfigParse;
import com.alibaba.nacos.client.config.utils.parse.DefaultPropertiesConfigParse;
import com.alibaba.nacos.client.config.utils.parse.DefaultXmlConfigParse;
import com.alibaba.nacos.client.config.utils.parse.DefaultYamlConfigParse;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * ConfigParseUtils.
 *
 * @author Nacos
 */
public final class ConfigParseUtils {
    
    private static final String LINK_CHAR = "#@#";
    
    @SuppressWarnings("checkstyle:StaticVariableName")
    private static Map<String, ConfigParse> DEFAULT_CONFIG_PARSE_MAP = new HashMap(8);
    
    @SuppressWarnings("checkstyle:StaticVariableName")
    private static Map<String, Map<String, ConfigParse>> CUSTOMER_CONFIG_PARSE_MAP = new HashMap(8);
    
    static {
        
        DefaultJsonConfigParse jsonConfigParse = new DefaultJsonConfigParse();
        DefaultPropertiesConfigParse propertiesConfigParse = new DefaultPropertiesConfigParse();
        DefaultYamlConfigParse yamlConfigParse = new DefaultYamlConfigParse();
        DefaultXmlConfigParse xmlConfigParse = new DefaultXmlConfigParse();
        
        // register nacos default ConfigParse
        DEFAULT_CONFIG_PARSE_MAP.put(jsonConfigParse.processType().toLowerCase(), jsonConfigParse);
        DEFAULT_CONFIG_PARSE_MAP.put(propertiesConfigParse.processType().toLowerCase(), propertiesConfigParse);
        DEFAULT_CONFIG_PARSE_MAP.put(yamlConfigParse.processType().toLowerCase(), yamlConfigParse);
        DEFAULT_CONFIG_PARSE_MAP.put(xmlConfigParse.processType().toLowerCase(), xmlConfigParse);
        
        // register customer ConfigParse
        ServiceLoader<ConfigParse> configParses = ServiceLoader.load(ConfigParse.class);
        StringBuilder sb = new StringBuilder();
        for (ConfigParse configParse : configParses) {
            String type = configParse.processType().toLowerCase();
            if (!CUSTOMER_CONFIG_PARSE_MAP.containsKey(type)) {
                CUSTOMER_CONFIG_PARSE_MAP.put(type, new HashMap<String, ConfigParse>(1));
            }
            sb.setLength(0);
            sb.append(configParse.dataId()).append(LINK_CHAR).append(configParse.group());
            if (LINK_CHAR.equals(sb.toString())) {
                // If the user does not set the data id and group processed by config
                // parse,
                // this type of config is resolved globally by default
                DEFAULT_CONFIG_PARSE_MAP.put(type, configParse);
            } else {
                CUSTOMER_CONFIG_PARSE_MAP.get(type).put(sb.toString(), configParse);
            }
        }
        
        DEFAULT_CONFIG_PARSE_MAP = Collections.unmodifiableMap(DEFAULT_CONFIG_PARSE_MAP);
        CUSTOMER_CONFIG_PARSE_MAP = Collections.unmodifiableMap(CUSTOMER_CONFIG_PARSE_MAP);
    }
    
    /**
     * XML configuration parsing to support different schemas.
     *
     * @param context config context
     * @param type    config type
     * @return {@link Properties}
     *
     */
    
    public static Properties toProperties(final String context, String type) {
        
        if (context == null) {
            return new Properties();
        }
        // Again the type lowercase, ensure the search
        type = type.toLowerCase();
        
        Properties properties = new Properties();
        if (DEFAULT_CONFIG_PARSE_MAP.containsKey(type)) {
            ConfigParse configParse = DEFAULT_CONFIG_PARSE_MAP.get(type);
            properties.putAll(configParse.parse(context));
            return properties;
        } else {
            throw new UnsupportedOperationException("Parsing is not yet supported for this type profile : " + type);
        }
    }
    
    /**
     * XML configuration parsing to support different schemas.
     *
     * @param dataId  config dataId
     * @param group   config group
     * @param context config context
     * @param type    config type
     * @return {@link Properties}
     */
    public static Properties toProperties(final String dataId, final String group, final String context, String type) {
        
        if (context == null) {
            return new Properties();
        }
        // Again the type lowercase, ensure the search
        type = type.toLowerCase();
        
        String configParseKey = dataId + LINK_CHAR + group;
        Properties properties = new Properties();
        
        if (CUSTOMER_CONFIG_PARSE_MAP.isEmpty() || LINK_CHAR.equals(configParseKey)) {
            return toProperties(context, type);
        }
        if (CUSTOMER_CONFIG_PARSE_MAP.get(type) == null || CUSTOMER_CONFIG_PARSE_MAP.get(type).isEmpty()) {
            return toProperties(context, type);
        }
        if (CUSTOMER_CONFIG_PARSE_MAP.get(type).get(configParseKey) == null) {
            return toProperties(context, type);
        } else {
            if (CUSTOMER_CONFIG_PARSE_MAP.containsKey(type)) {
                ConfigParse configParse = CUSTOMER_CONFIG_PARSE_MAP.get(type).get(configParseKey);
                if (configParse == null) {
                    throw new NoSuchElementException("This config can't find ConfigParse to parse");
                }
                properties.putAll(configParse.parse(context));
                return properties;
            } else {
                throw new UnsupportedOperationException("Parsing is not yet supported for this type profile : " + type);
            }
        }
    }
    
}