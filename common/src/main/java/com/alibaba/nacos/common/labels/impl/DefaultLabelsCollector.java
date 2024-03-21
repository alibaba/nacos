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
import com.alibaba.nacos.common.labels.impl.utils.ConfigGetterManager;
import com.alibaba.nacos.common.utils.ConnLabelsUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;

/**
 * DefaultLabelsCollector.
 *
 * @author rong
 */
public class DefaultLabelsCollector extends AbstractLabelsCollector implements LabelsCollector {
    
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultLabelsCollector.class);
    
    private final String customName = "defaultLabelsCollector";
    
    /**
     * init labels.
     *
     * @date 2024/2/4
     *@description will init from properties, JVM OPTIONS, ENV by order of <tt>properties > JVM OPTIONS > ENV</tt> by default.
     * which will use the next level value when the current level value isn't setup (you also can set the level by env).
     * <p>eg: if the value of "nacos.app.conn.labels"(properties' key) is "k1=v1,k2=v2"(properties' value), the result will be
     * a Map with value{k1=v1,k2=v2}.</p>
     * @param  properties Properties
     */
    @Override
    public void init(Properties properties) {
        ConfigGetterManager configGetterManager = new ConfigGetterManager(properties);
        labels.putAll(ConnLabelsUtils.parseRawLabels(configGetterManager.getConfig(Constants.APP_CONN_LABELS_PREFIX)));
        
        String grayLabelValue = configGetterManager.getConfig(Constants.CONFIG_GRAY);
        if (StringUtils.isNotBlank(grayLabelValue)) {
            labels.put(Constants.GRAY, grayLabelValue);
        }
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            LOGGER.info("init labels: {}={}", entry.getKey(), entry.getValue());
        }
    }
    
    @Override
    public String getName() {
        return customName;
    }
}
