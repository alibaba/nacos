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

package com.alibaba.nacos.common.labels;

import java.util.Map;
import java.util.Properties;

/**
 * LabelsCollector.
 *
 * @author rong
 * @date 2024/2/4
 */
public interface LabelsCollector {
    
    /**
     * getLabels.
     *
     * @param properties properties
     * @return Map labels.
     * @date 2024/2/4
     * @description get all labels
     */
    Map<String, String> collectLabels(Properties properties);
    
    /**
     * getOrder.
     *
     * @return the order value
     * @date 2024/2/4
     * @description get order value of labels in case of multiple labels
     */
    int getOrder();
    
    /**
     * get collector name.
     *
     * @return name of collector
     * @date 2024/2/4
     * @description name of collector
     */
    String getName();
    
}
