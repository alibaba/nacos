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

import java.util.Locale;

/**
 * AbstractFileFormatPluginService.
 *
 * @author liyunfei
 */
public abstract class AbstractFileFormatPluginService implements ConfigChangeService, Comparable<ConfigChangeService> {
    
    @Override
    public int compareTo(ConfigChangeService o) {
        return getOrder() - o.getOrder();
    }
    
    @Override
    public int getOrder() {
        return 0;
    }
    
    @Override
    public String getServiceType() {
        return ConfigChangeConstants.FileFormatCheck.class.getSimpleName().toLowerCase(Locale.ROOT);
    }
    
    @Override
    public String executeType() {
        return "sync";
    }
}
