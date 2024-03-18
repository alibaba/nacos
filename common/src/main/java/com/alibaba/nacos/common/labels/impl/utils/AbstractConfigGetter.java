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

import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Properties;

/**
 * AbstractConfigGetter.
 *
 * @author rong
 * @date 2024-03-06 17:42
 */
public abstract class AbstractConfigGetter implements OrderedConfigGetter {
    
    protected Logger logger;
    
    protected int order;
    
    @Override
    public void init(Properties properties) {
        String weightKey = getWeightKey();
        String weight = properties.getProperty(weightKey, System.getProperty(weightKey, System.getenv(weightKey)));
        if (StringUtils.isBlank(weight)) {
            return;
        }
        try {
            order = Integer.parseInt(weight);
        } catch (NumberFormatException e) {
            logger.error("parse weight error, weight={}", weight, e);
        }
    }
    
    /**
    * get weight key which will be used to get weight from properties/env/jvm.
    *
    * @date 2024/3/7
    * @return String weight key.
    */
    protected abstract String getWeightKey();
}
