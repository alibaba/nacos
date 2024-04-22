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

package com.alibaba.nacos.logger.adapter.log4j2;

import com.alibaba.nacos.common.logging.NacosLoggingProperties;
import org.apache.logging.log4j.core.config.plugins.Plugin;

/**
 * Log4j2 nacos logging properties holder.
 *
 * <p>
 * Log4j2 use {@link Plugin} annotation to inject Properties lookup to set properties, this holder is to keep the {@link
 * NacosLoggingProperties} and called in {@link NacosClientPropertiesLookup} which is annotate by {@link Plugin}.
 * </p>
 *
 * @author xiweng.yy
 */
public class Log4j2NacosLoggingPropertiesHolder {
    
    private static final Log4j2NacosLoggingPropertiesHolder INSTANCE = new Log4j2NacosLoggingPropertiesHolder();
    
    private NacosLoggingProperties properties;
    
    public static void setProperties(NacosLoggingProperties properties) {
        INSTANCE.properties = properties;
    }
    
    public static String getValue(String key) {
        return null == INSTANCE.properties ? null : INSTANCE.properties.getValue(key, null);
    }
    
}
