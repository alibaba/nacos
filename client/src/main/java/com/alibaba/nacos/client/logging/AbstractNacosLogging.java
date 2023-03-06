/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.logging;

import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Abstract nacos logging.
 *
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @since 0.9.0
 */
public abstract class AbstractNacosLogging {
    
    private static final String NACOS_LOGGING_CONFIG_PROPERTY = "nacos.logging.config";
    
    private static final String NACOS_LOGGING_DEFAULT_CONFIG_ENABLED_PROPERTY = "nacos.logging.default.config.enabled";
    
    protected String getLocation(String defaultLocation) {
        String location = NacosClientProperties.PROTOTYPE.getProperty(NACOS_LOGGING_CONFIG_PROPERTY);
        if (StringUtils.isBlank(location)) {
            if (isDefaultConfigEnabled()) {
                return defaultLocation;
            }
            return null;
        }
        return location;
    }
    
    private boolean isDefaultConfigEnabled() {
        String property = NacosClientProperties.PROTOTYPE.getProperty(NACOS_LOGGING_DEFAULT_CONFIG_ENABLED_PROPERTY);
        // The default value is true.
        return property == null || ConvertUtils.toBoolean(property);
    }
    
    /**
     * Load logging configuration.
     */
    public abstract void loadConfiguration();
}
