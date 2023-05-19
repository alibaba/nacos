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

package com.alibaba.nacos.client.logging.log4j2;

import com.alibaba.nacos.client.env.NacosClientProperties;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.AbstractLookup;
import org.apache.logging.log4j.core.lookup.StrLookup;

/**
 *  support log4j2 read properties from NacosClientProperties.
 *  for example:
 *    <SizeBasedTriggeringPolicy size="${nacosClientProperty:JM.LOG.FILE.SIZE:-10MB}"/>
 * @author onewe
 */
@Plugin(name = "nacosClientProperty", category = StrLookup.CATEGORY)
public class NacosClientPropertiesLookup  extends AbstractLookup {
    
    @Override
    public String lookup(LogEvent event, String key) {
        return NacosClientProperties.PROTOTYPE.getProperty(key);
    }
}