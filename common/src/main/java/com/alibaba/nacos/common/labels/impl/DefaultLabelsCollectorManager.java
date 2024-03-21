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
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLabelsCollectorManager.class);
    
    private ArrayList<LabelsCollector> labelsCollectorsList = new ArrayList<>();
    
    private Map<String, String> labels = new HashMap<>();
    
    private static final int MAX_TRY_COUNT = 3;
    
    private volatile boolean isLabelsInit = false;
    
    public DefaultLabelsCollectorManager(Properties properties) {
        init(properties);
    }
    
    private void init(Properties properties) {
        if (isLabelsInit) {
            return;
        }
        synchronized (this) {
            if (isLabelsInit) {
                return;
            }
            isLabelsInit = true;
            LOGGER.info("DefaultLabelsCollectorManager init labels.....");
            initLabels(properties);
            LOGGER.info("DefaultLabelsCollectorManager init labels finished, labels:{}", labels);
            
        }
    }
    
    public Map<String, String> getAllLabels() {
        for (int tryTimes = 0; tryTimes < MAX_TRY_COUNT && !isLabelsInit; tryTimes++) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                //do nothing
            }
        }
        return labels;
    }
    
    @Override
    public Map<String, String> refreshAllLabels(Properties properties) {
        for (int tryTimes = 0; tryTimes < MAX_TRY_COUNT && !isLabelsInit; tryTimes++) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                //do nothing
            }
        }
        if (!isLabelsInit) {
            return new HashMap<>(2);
        }
        LOGGER.info("DefaultLabelsCollectorManager refresh labels.....");
        initLabels(properties);
        LOGGER.info("DefaultLabelsCollectorManager refresh labels finished,labels :{}", labels);
        return labels;
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
    
    private void initLabels(Properties properties) {
        labelsCollectorsList = loadLabelsCollectors();
        this.labels = getLabels(labelsCollectorsList, properties);
    }
    
    Map<String, String> getLabels(ArrayList<LabelsCollector> labelsCollectorsList, Properties properties) {
        Map<String, String> labels = new HashMap<>(8);
        for (LabelsCollector labelsCollector : labelsCollectorsList) {
            try {
                LOGGER.info("LabelsCollector with name [{}] initializing......", labelsCollector.getName());
                labelsCollector.init(properties);
                LOGGER.info("LabelsCollector with name [{}] initialize finished......", labelsCollector.getName());
            } catch (Exception e) {
                LOGGER.error("init LabelsCollector with [name:{}] failed", labelsCollector.getName(), e);
                continue;
            }
            LOGGER.info("Process LabelsCollector with [name:{}]", labelsCollector.getName());
            for (Map.Entry<String, String> entry : labelsCollector.getLabels().entrySet()) {
                if (innerAddLabel(labels, entry.getKey(), entry.getValue())) {
                    LOGGER.info("pick label with [key:{}, value:{}] of collector [name:{}]", entry.getKey(),
                            entry.getValue(), labelsCollector.getName());
                } else {
                    LOGGER.info(
                            " ignore label with [key:{}, value:{}] of collector [name:{}],"
                                    + "already existed in LabelsCollectorManager with previous [value:{}]，",
                            entry.getKey(), entry.getValue(), labelsCollector.getName(), labels.get(entry.getKey()));
                }
            }
        }
        return labels;
    }
    
    private boolean innerAddLabel(Map<String, String> labels, String key, String value) {
        return null == labels.putIfAbsent(key, value);
    }
}
