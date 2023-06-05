/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.config.spi;

import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeExecuteTypes;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.model.ConfigChangeResponse;

/**
 * ConfigChangePluginService.
 *
 * @author liyunfei
 */
public interface ConfigChangePluginService {

    /**
     * execute config change plugin service.
     *
     * @param configChangeRequest  ConfigChangeRequest
     * @param configChangeResponse ConfigChangeResponse
     */
    void execute(ConfigChangeRequest configChangeRequest, ConfigChangeResponse configChangeResponse);

    /**
     * execute type {@link ConfigChangeExecuteTypes}.
     *
     * @return type
     */
    ConfigChangeExecuteTypes executeType();


    /**
     * what kind of plugin service,such as webhook,whiteList and other,need keep a way with the constants config of you
     * enum in {@link ConfigChangeConstants}.
     *
     * @return service type
     */
    String getServiceType();

    /**
     * when pointcut the same method,according to order to load plugin service. order is lower,prior is higher.
     *
     * @return order
     */
    int getOrder();

    /**
     * the ConfigChangeTypes {@link ConfigChangePointCutTypes} of need to pointcut.
     *
     * <p>
     * ConfigChangeTypes mean the relevant pointcut method.
     * </p>
     *
     * @return array of pointcut the methods
     */
    ConfigChangePointCutTypes[] pointcutMethodNames();

}
