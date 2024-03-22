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
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.Properties;

/**
 * description.
 *
 * @author rong
 * @date 2024-02-29 20:13
 */
public class DefaultLabelsCollectorManagerTest {
    
    @Test
    public void tagV2LabelsCollectorTest() {
        Properties properties = new Properties();
        properties.put(Constants.APP_CONN_LABELS_KEY, "k1=v1,gray=properties_pre");
        properties.put(Constants.CONFIG_GRAY_LABEL, "properties_after");
        DefaultLabelsCollectorManager defaultLabelsCollectorManager = new DefaultLabelsCollectorManager();
        Map<String, String> labels = defaultLabelsCollectorManager.getLabels(properties);
        Assert.assertEquals("properties_after", labels.get(Constants.CONFIG_GRAY_LABEL));
        Assert.assertEquals("v1", labels.get("k1"));
    }
    
    @Test
    public void tagV2LabelsCollectorOrderTest() {
        Properties properties = new Properties();
        DefaultLabelsCollectorManager defaultLabelsCollectorManager = new DefaultLabelsCollectorManager();
        Map<String, String> labels = defaultLabelsCollectorManager.getLabels(properties);
        String test = labels.get("test");
        Assert.assertEquals("test2", test);
    }
    
}
