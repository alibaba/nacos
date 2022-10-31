/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.environment.spi;

import java.util.Map;
import java.util.Set;

/**
 * CustomEnvironment Plugin Service.
 *
 * @author : huangtianhui
 */
public interface CustomEnvironmentPluginService {
    /**
     * customValue interface.
     *
     * @param property property key value
     * @return custom key value
     */
    Map<String, Object> customValue(Map<String, Object> property);

    /**
     * propertyKey interface.
     *
     * @return propertyKey property Key
     */
    Set<String> propertyKey();

    /**
     * order  The larger the priority, the higher the priority.
     *
     * @return order
     */
    Integer order();

    /**
     * pluginName.
     *
     * @return
     */
    String pluginName();
}
