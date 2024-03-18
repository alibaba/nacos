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

import com.alibaba.nacos.common.labels.LabelsCollector;

import java.util.HashMap;
import java.util.Map;

/**
 * AbstractLabelsCollector.
 *
 * @author rong
 */
public abstract class AbstractLabelsCollector implements LabelsCollector {
    
    protected Map<String, String> labels = new HashMap<>(2);
    
    private static final int DEFAULT_INITIAL_ORDER = 100;
    
    @Override
    public Map<String, String> getLabels() {
        return labels;
    }
    
    @Override
    public int getOrder() {
        return DEFAULT_INITIAL_ORDER;
    }
}
