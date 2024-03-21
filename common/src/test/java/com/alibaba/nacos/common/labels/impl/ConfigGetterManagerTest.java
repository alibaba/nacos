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
import com.alibaba.nacos.common.labels.impl.utils.ConfigGetterManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

/**
 * description.
 *
 * @author rong
 * @date 2024-03-07 11:10
 */
public class ConfigGetterManagerTest {
    
    @Test
    public void testGetByOrder() {
        Properties init = new Properties();
        init.put(Constants.APP_CONN_LABELS_PROPERTIES_WEIGHT_KEY, "1");
        init.put(Constants.APP_CONN_LABELS_ENV_WEIGHT_KEY, "2");
        init.put(Constants.APP_CONN_LABELS_JVM_WEIGHT_KEY, "3");
        System.setProperty(Constants.APP_CONN_LABELS_PREFIX, "gray=jvm_pre");
        init.put(Constants.APP_CONN_LABELS_PREFIX, "gray=properties_pre");
        
        String result = new ConfigGetterManager(init).getConfig(Constants.APP_CONN_LABELS_PREFIX);
        Assert.assertEquals("gray=jvm_pre", result);
        
        init.put(Constants.APP_CONN_LABELS_PROPERTIES_WEIGHT_KEY, "3");
        init.put(Constants.APP_CONN_LABELS_ENV_WEIGHT_KEY, "2");
        init.put(Constants.APP_CONN_LABELS_JVM_WEIGHT_KEY, "1");
        
        result = new ConfigGetterManager(init).getConfig(Constants.APP_CONN_LABELS_PREFIX);
        Assert.assertEquals("gray=properties_pre", result);
        
        init.remove(Constants.APP_CONN_LABELS_PREFIX);
        result = new ConfigGetterManager(init).getConfig(Constants.APP_CONN_LABELS_PREFIX);
        Assert.assertEquals("gray=jvm_pre", result);
    }
}
