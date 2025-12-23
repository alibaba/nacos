/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.spi.server;

import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginType;

import java.util.Map;

/**
 * Auth plugin provider implementation.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public class AuthPluginProvider implements PluginProvider<AuthPluginService> {

    @Override
    public PluginType getPluginType() {
        return PluginType.AUTH;
    }

    @Override
    public Map<String, AuthPluginService> getAllPlugins() {
        return AuthPluginManager.getInstance().getAllPlugins();
    }
}
