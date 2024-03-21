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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * description.
 *
 * @author rong
 * @date 2024-03-07 10:02
 */
public class ConfigGetterManager {
    
    private final List<OrderedConfigGetter> priorityList = new ArrayList<>();
    
    public String getConfig(String key) {
        for (OrderedConfigGetter getter : priorityList) {
            String config = getter.getConfig(key);
            if (config != null) {
                return config;
            }
        }
        return null;
    }
    
    public ConfigGetterManager(Properties properties) {
        Properties temp = new Properties();
        temp.putAll(properties);
        init(temp);
    }
    
    private void init(Properties properties) {
        priorityList.add(new PropertiesConfigGetter());
        priorityList.add(new JvmConfigGetter());
        priorityList.add(new EnvConfigGetter());
        priorityList.stream().filter(Objects::nonNull).forEach(getter -> getter.init(properties));
        priorityList.sort((o1, o2) -> o2.order() - o1.order());
    }
    
}
