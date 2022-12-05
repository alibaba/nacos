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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.exception.ConfigPropertiesException;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * ConfigToProperties Util.
 *
 * @author 985492783@qq.com
 */
@SuppressWarnings("unchecked")
public class ConfigToPropertiesUtil {
    public static final String TYPE_YAML = "yaml";
    
    public static final String TYPE_PROPERTIES = "properties";
    
    public static final String TYPE_XML = "xml";
    
    public static final String TYPE_JSON = "json";
    
    public static final String TYPE_TEXT = "text";
    
    public static final String TYPE_HTML = "html";
    
    /**
     * change configInfo to Properties.
     *
     * @param configInfo configInfo
     * @return configInfo to Properties
     */
    public static ConfigProperties configToProperties(ConfigInfo configInfo) throws ConfigPropertiesException {
        switch (configInfo.getType()) {
            case TYPE_YAML:
                return yamlToProperties(configInfo);
            case TYPE_PROPERTIES:
                return propertiesToProperties(configInfo);
            case TYPE_XML:
                return xmlToProperties(configInfo);
            case TYPE_JSON:
                return jsonToProperties(configInfo);
            case TYPE_TEXT:
                return textToProperties(configInfo);
            case TYPE_HTML:
                return htmlToProperties(configInfo);
            default:
                return new ConfigProperties();
        }
    }
    
    private static ConfigProperties htmlToProperties(ConfigInfo configInfo) {
        return textToProperties(configInfo);
    }
    
    private static ConfigProperties textToProperties(ConfigInfo configInfo) {
        class TextProperties extends ConfigProperties {
            private final String key;
            
            public TextProperties(String key) {
                super();
                this.key = key;
            }
            
            @Override
            public boolean containsConfig(String key) {
                return this.key.contains(key);
            }
        }
        
        return new TextProperties(configInfo.getContent());
    }
    
    private static ConfigProperties jsonToProperties(ConfigInfo configInfo) {
        ObjectMapper objectMapper = new ObjectMapper();
        ConfigProperties properties = new ConfigProperties();
        try {
            Object obj = objectMapper.readValue(configInfo.getContent(), Object.class);
            travelRootWithResult(obj, properties);
        } catch (JsonProcessingException e) {
            throw new ConfigPropertiesException("json to properties fail");
        }
        return properties;
    }
    
    private static ConfigProperties xmlToProperties(ConfigInfo configInfo) {
        ConfigProperties properties = new ConfigProperties();
        try (InputStream in = new ByteArrayInputStream(configInfo.getContent().getBytes(StandardCharsets.UTF_8))) {
            properties.loadFromXML(in);
        } catch (IOException e) {
            throw new ConfigPropertiesException("xml to properties fail");
        }
        return properties;
    }
    
    private static ConfigProperties propertiesToProperties(ConfigInfo configInfo) {
        ConfigProperties properties = new ConfigProperties();
        try (InputStream in  = new ByteArrayInputStream(configInfo.getContent().getBytes(StandardCharsets.UTF_8))) {
            properties.load(in);
        } catch (IOException e) {
            throw new ConfigPropertiesException("properties to properties fail");
        }
        return properties;
    }
    
    private static ConfigProperties yamlToProperties(ConfigInfo configInfo) {
        String content = configInfo.getContent();
        Yaml yaml = new Yaml();
        ConfigProperties properties = new ConfigProperties();
        try (InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            Object load = yaml.load(in);
            travelRootWithResult(load, properties);
        } catch (IOException e) {
            throw new ConfigPropertiesException("yaml to properties fail");
        }
        return properties;
    }
    
    private static void travelRootWithResult(Object object, Properties properties) {
        if (object instanceof LinkedHashMap) {
            LinkedHashMap<String, Object> map = (LinkedHashMap<String, Object>) object;
            Set<String> keySet = map.keySet();
            for (String key : keySet) {
                List<String> keyList = new ArrayList<>();
                keyList.add(key);
                travelTreeNode(map.get(key), keyList, properties);
            }
        }
    }
    
    private static void travelTreeNode(Object obj, List<String> keyList, Properties properties) {
        if (obj instanceof LinkedHashMap) {
            LinkedHashMap<String, Object> linkedHashMap = (LinkedHashMap<String, Object>) obj;
            linkedHashMap.forEach((key, value) -> {
                if (value instanceof LinkedHashMap) {
                    keyList.add(key);
                    travelTreeNode(value, keyList, properties);
                    keyList.remove(keyList.size() - 1);
                } else {
                    StringBuilder result = new StringBuilder();
                    for (String strKey : keyList) {
                        result.append(strKey).append(".");
                    }
                    result.append(key);
                    properties.setProperty(result.toString(), value.toString());
                }
            });
        } else {
            properties.setProperty(keyList.get(0), String.valueOf(obj));
        }
    }
    
    public static class ConfigProperties extends Properties {
        
        public boolean containsConfig(String key) {
            return getProperty(key) != null;
        }
    }
}
