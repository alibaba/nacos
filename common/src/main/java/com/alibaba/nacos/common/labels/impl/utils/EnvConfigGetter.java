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

package com.alibaba.nacos.common.labels.impl.utils;

import com.alibaba.nacos.api.common.Constants;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * EnvConfigGetter.
 *
 * @author rong
 * @date 2024-03-06 17:42
 */
public class EnvConfigGetter extends AbstractConfigGetter implements OrderedConfigGetter {
    
    @Override
    public void init(Properties properties) {
        order = Constants.APP_CONN_LABELS_ENV_DEFAULT_WEIGHT;
        logger = LoggerFactory.getLogger(EnvConfigGetter.class);
        super.init(properties);
    }
    
    @Override
    protected String getWeightKey() {
        return Constants.APP_CONN_LABELS_ENV_WEIGHT_KEY;
    }
    
    @Override
    public int order() {
        return order;
    }
    
    @Override
    public String getConfig(String key) {
        return System.getenv(key);
    }
}
