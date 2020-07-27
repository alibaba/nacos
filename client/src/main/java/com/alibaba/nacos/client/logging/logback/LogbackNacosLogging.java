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

package com.alibaba.nacos.client.logging.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import com.alibaba.nacos.client.logging.AbstractNacosLogging;
import com.alibaba.nacos.common.utils.ResourceUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.impl.StaticLoggerBinder;

/**
 * Support for Logback version 1.0.8 or higher
 *
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @since 0.9.0
 */
public class LogbackNacosLogging extends AbstractNacosLogging {
    
    private static final String NACOS_LOGBACK_LOCATION = "classpath:nacos-logback.xml";
    
    @Override
    public void loadConfiguration() {
        String location = getLocation(NACOS_LOGBACK_LOCATION);
        if (StringUtils.isBlank(location)) {
            return;
        }
        
        try {
            LoggerContext loggerContext = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
            new ContextInitializer(loggerContext).configureByResource(ResourceUtils.getResourceUrl(location));
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize Logback Nacos logging from " + location, e);
        }
    }
    
}
