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

package com.alibaba.nacos.common.labels.impl;

import com.alibaba.nacos.common.labels.LabelsCollector;
import com.alibaba.nacos.common.labels.LabelsCollectorManager;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * DefaultLabelsCollectorManager.
 *
 * @author rong
 */
public class DefaultLabelsCollectorManager implements LabelsCollectorManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.common.labels");
    
    private ArrayList<LabelsCollector> labelsCollectorsList = new ArrayList<>();
    
    public DefaultLabelsCollectorManager() {
        labelsCollectorsList = loadLabelsCollectors();
    }
    
    @Override
    public Map<String, String> getLabels(Properties properties) {
        LOGGER.info("DefaultLabelsCollectorManager get labels.....");
        Map<String, String> labels = getLabels(labelsCollectorsList, properties);
        LOGGER.info("DefaultLabelsCollectorManager get labels finished,labels :{}", labels);
        return labels;
    }
    
    Map<String, String> getLabels(ArrayList<LabelsCollector> labelsCollectorsList, Properties properties) {
        
        if (properties == null) {
            properties = new Properties();
        }
        Map<String, String> labels = new HashMap<>(8);
        for (LabelsCollector labelsCollector : labelsCollectorsList) {
            
            LOGGER.info("Process LabelsCollector with [name:{}]", labelsCollector.getName());
            for (Map.Entry<String, String> entry : labelsCollector.collectLabels(properties).entrySet()) {
                if (!checkValidLabel(entry.getKey(), entry.getValue())) {
                    LOGGER.info(" ignore invalid label with [key:{}, value:{}] of collector [name:{}]", entry.getKey(),
                            entry.getValue(), labelsCollector.getName());
                    continue;
                }
                if (innerAddLabel(labels, entry.getKey(), entry.getValue())) {
                    LOGGER.info("pick label with [key:{}, value:{}] of collector [name:{}]", entry.getKey(),
                            entry.getValue(), labelsCollector.getName());
                } else {
                    LOGGER.info(" ignore label with [key:{}, value:{}] of collector [name:{}],"
                                    + "already existed in LabelsCollectorManager with previous [value:{}]，", entry.getKey(),
                            entry.getValue(), labelsCollector.getName(), labels.get(entry.getKey()));
                }
            }
        }
        return labels;
    }
    
    private boolean checkValidLabel(String key, String value) {
        return isValid(key) && isValid(value);
    }
    
    private static boolean isValid(String param) {
        if (StringUtils.isBlank(param)) {
            return false;
        }
        int length = param.length();
        if (length > maxLength) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char ch = param.charAt(i);
            if (!Character.isLetterOrDigit(ch) && !isValidChar(ch)) {
                return false;
            }
        }
        return true;
    }
    
    private static char[] validChars = new char[] {'_', '-', '.'};
    
    private static int maxLength = 128;
    
    private static boolean isValidChar(char ch) {
        for (char c : validChars) {
            if (c == ch) {
                return true;
            }
        }
        return false;
    }
    
    private ArrayList<LabelsCollector> loadLabelsCollectors() {
        ServiceLoader<LabelsCollector> labelsCollectors = ServiceLoader.load(LabelsCollector.class);
        ArrayList<LabelsCollector> labelsCollectorsList = new ArrayList<>();
        for (LabelsCollector labelsCollector : labelsCollectors) {
            labelsCollectorsList.add(labelsCollector);
        }
        labelsCollectorsList.sort((o1, o2) -> o2.getOrder() - o1.getOrder());
        return labelsCollectorsList;
    }
    
    private boolean innerAddLabel(Map<String, String> labels, String key, String value) {
        return null == labels.putIfAbsent(key, value);
    }
}
