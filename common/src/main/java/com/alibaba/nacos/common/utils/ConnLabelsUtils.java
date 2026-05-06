/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * ConnLabelsUtils.
 *
 * @author rong
 */
public class ConnLabelsUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnLabelsUtils.class);
    
    public static final String LABEL_EQUALS_OPERATOR = "=";
    
    public static final String LABEL_SPLIT_OPERATOR = ",";
    
    public static final int TAG_V2_LABEL_KEY_VALUE_SPLIT_LENGTH = 2;
    
    /**
     * parse property value to map.
     *
     * @param properties   Properties
     * @param propertyName which key to get
     * @return (String)key-(String)value map
     * @date 2024/1/29
     * @description will get a key-value map from properties, JVM OPTIONS, ENV by order of <tt>properties > JVM OPTIONS
     * > ENV</tt> which will use the next level value when the current level value isn't setup.
     * <p>eg: if the value of "nacos.app.conn.labels"(properties' key) is "k1=v1,k2=v2"(properties' value), the result
     * will be
     * a Map with value{k1=v1,k2=v2}.</p>
     */
    public static Map<String, String> parsePropertyValue2Map(Properties properties, String propertyName) {
        String rawLabels = properties.getProperty(propertyName,
                System.getProperty(propertyName, System.getenv(propertyName)));
        if (StringUtils.isBlank(rawLabels)) {
            LOGGER.info("no value found for property key: {}", propertyName);
            return new HashMap<>(2);
        }
        return parseRawLabels(rawLabels);
    }
    
    /**
     * parse raw json labels into a key-value map.
     *
     * @param rawLabels rawLabels to parse
     * @return map parsed from rawLabels
     * @date 2024/1/29
     * @description
     */
    public static Map<String, String> parseRawLabels(String rawLabels) {
        if (StringUtils.isBlank(rawLabels)) {
            return new HashMap<>(2);
        }
        HashMap<String, String> resultMap = new HashMap<>(2);
        try {
            Arrays.stream(rawLabels.split(LABEL_SPLIT_OPERATOR)).filter(Objects::nonNull).map(String::trim)
                    .filter(StringUtils::isNotBlank).forEach(label -> {
                        String[] kv = label.split(LABEL_EQUALS_OPERATOR);
                        if (kv.length == TAG_V2_LABEL_KEY_VALUE_SPLIT_LENGTH) {
                            resultMap.put(kv[0].trim(), kv[1].trim());
                        } else {
                            LOGGER.error("unknown label format: {}", label);
                        }
                    });
        } catch (Exception e) {
            LOGGER.error("unknown label format: {}", rawLabels);
        }
        return resultMap;
    }
    
    /**
     * merge two map into one by using the former value when key is duplicated.
     *
     * @param preferredMap preferredMap
     * @param backwardMap  backwardMap
     * @date 2024/1/29
     * @description merge two map into one preferring using the first one when key is duplicated
     */
    public static <T, R> Map<T, R> mergeMapByOrder(Map<T, R> preferredMap, Map<T, R> backwardMap) {
        if (preferredMap == null || preferredMap.isEmpty()) {
            return new HashMap<T, R>(8) {
                {
                    putAll(backwardMap);
                }
            };
        }
        if (backwardMap == null || backwardMap.isEmpty()) {
            return new HashMap<T, R>(8) {
                {
                    putAll(preferredMap);
                }
            };
        }
        HashMap<T, R> resultMap = new HashMap<T, R>(8) {
            {
                putAll(preferredMap);
            } };
        backwardMap.forEach((key, value) -> {
            if (!resultMap.containsKey(key)) {
                resultMap.put(key, value);
            }
        });
        return resultMap;
    }
    
    /**
     * add prefix for each key in map.
     *
     * @param map    map to add prefix
     * @param prefix prefix
     * @date 2024/1/29
     * @description add prefix for each key in map
     */
    public static <T> Map<String, T> addPrefixForEachKey(Map<String, T> map, String prefix) {
        if (map == null || map.isEmpty()) {
            return map;
        }
        return map.entrySet().stream().filter(Objects::nonNull).filter(elem -> !elem.getKey().trim().isEmpty())
                .collect(Collectors.toMap(elem -> prefix + elem.getKey(), Map.Entry::getValue));
    }
}
