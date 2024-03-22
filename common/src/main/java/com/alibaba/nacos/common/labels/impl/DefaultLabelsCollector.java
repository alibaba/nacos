/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.labels.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.labels.LabelsCollector;
import com.alibaba.nacos.common.utils.ConnLabelsUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.alibaba.nacos.api.common.Constants.DOT;
import static com.alibaba.nacos.api.common.Constants.ENV_KEY;
import static com.alibaba.nacos.api.common.Constants.JVM_KEY;

/**
 * DefaultLabelsCollector.
 *
 * @author rong
 */
public class DefaultLabelsCollector implements LabelsCollector {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.common.labels");
    
    private final String customName = "defaultNacosLabelsCollector";
    
    private static final String UNDERSCORE = "_";
    
    /**
     * init labels.
     *
     * @param properties Properties
     * @date 2024/2/4
     * @description will init from properties, JVM OPTIONS, ENV by order of <tt>properties > JVM OPTIONS > ENV</tt> by
     * default. which will use the next level value when the current level value isn't setup (you also can set the level
     * by env).
     * <p>eg: if the value of "nacos.app.conn.labels"(properties' key) is "k1=v1,k2=v2"(properties' value), the result
     * will be a Map with value{k1=v1,k2=v2}.</p>
     */
    @Override
    public Map<String, String> collectLabels(Properties properties) {
        
        //properties
        Map<String, String> propertiesLabels = ConnLabelsUtils.parseRawLabels(
                properties.getProperty(Constants.APP_CONN_LABELS_KEY));
        if (properties.containsKey(Constants.CONFIG_GRAY_LABEL)) {
            propertiesLabels.put(Constants.CONFIG_GRAY_LABEL, properties.getProperty(Constants.CONFIG_GRAY_LABEL));
        }
        LOGGER.info("default nacos collect properties labels: {}", propertiesLabels);
        
        //jvm
        Map<String, String> jvmLabels = ConnLabelsUtils.parseRawLabels(
                System.getProperty(Constants.APP_CONN_LABELS_KEY));
        if (System.getProperty(Constants.CONFIG_GRAY_LABEL) != null) {
            jvmLabels.put(Constants.CONFIG_GRAY_LABEL, System.getProperty((Constants.CONFIG_GRAY_LABEL)));
        }
        LOGGER.info("default nacos collect jvm labels: {}", jvmLabels);
        
        //env
        Map<String, String> envLabels = ConnLabelsUtils.parseRawLabels(
                System.getenv(Constants.APP_CONN_LABELS_KEY.replaceAll(DOT, UNDERSCORE)));
        if (System.getenv(Constants.CONFIG_GRAY_LABEL.replaceAll(DOT, UNDERSCORE)) != null) {
            envLabels.put(Constants.CONFIG_GRAY_LABEL,
                    System.getenv(Constants.CONFIG_GRAY_LABEL.replaceAll(DOT, UNDERSCORE)));
        }
        LOGGER.info("default nacos collect env labels: {}", envLabels);
        
        Map<String, String> finalLabels = new HashMap<>(4);
        String preferred = System.getenv(Constants.APP_CONN_LABELS_PREFERRED);
        boolean jvmPrefferred = false;
        boolean envPrefferred = false;
        
        if (StringUtils.isNotBlank(preferred)) {
            LOGGER.info("default nacos  labels collector preferred {} labels.", preferred);
            if (JVM_KEY.equals(preferred)) {
                finalLabels.putAll(jvmLabels);
                jvmPrefferred = true;
            } else if (ENV_KEY.equals(preferred)) {
                finalLabels.putAll(envLabels);
                envPrefferred = true;
            }
        }
        finalLabels = ConnLabelsUtils.mergeMapByOrder(finalLabels, propertiesLabels);
        if (!jvmPrefferred) {
            finalLabels = ConnLabelsUtils.mergeMapByOrder(finalLabels, jvmLabels);
        }
        if (!envPrefferred) {
            finalLabels = ConnLabelsUtils.mergeMapByOrder(finalLabels, envLabels);
        }
        
        for (Map.Entry<String, String> entry : finalLabels.entrySet()) {
            LOGGER.info("default nacos init labels: {}={}", entry.getKey(), entry.getValue());
        }
        return finalLabels;
    }
    
    @Override
    public String getName() {
        return customName;
    }
    
    private static final int DEFAULT_INITIAL_ORDER = 100;
    
    @Override
    public int getOrder() {
        return DEFAULT_INITIAL_ORDER;
    }
}
