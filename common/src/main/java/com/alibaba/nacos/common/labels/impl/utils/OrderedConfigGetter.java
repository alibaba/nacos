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

import java.util.Properties;

/**
 * OrderedConfigGetter.
 *
 * @author rong
 */
public interface OrderedConfigGetter {
    
    /**
    * init.
    *
    * @date 2024/3/6
    * @param properties properties.
    */
    void init(Properties properties);
    
    /**
    * get order.
    *
    * @date 2024/3/6
    * @return the order.
    */
    int order();
    
    /**
    * get config by key.
    *
    * @date 2024/3/6
    * @param key key.
    * @return the config.
    */
    String getConfig(String key);
}
