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
import ch.qos.logback.core.LogbackException;
import ch.qos.logback.core.joran.spi.JoranException;
import com.alibaba.nacos.api.config.ConfigType;

import java.net.URL;

/**
 * nacos NacosContextInitializer for lobgack.
 * @author <a href="mailto:hujun3@xiaomi.com">hujun</a>
 */
public class NacosContextInitializer extends ContextInitializer {

    final LoggerContext loggerContext;

    public NacosContextInitializer(LoggerContext loggerContext) {
        super(loggerContext);
        this.loggerContext = loggerContext;
    }

    @Override
    public void configureByResource(URL url) throws JoranException {
        if (url == null) {
            throw new IllegalArgumentException("URL argument cannot be null");
        }
        final String urlString = url.toString();
        if (urlString.endsWith(ConfigType.XML.getType())) {
            NacosJoranConfigurator configurator = new NacosJoranConfigurator();
            configurator.setContext(loggerContext);
            configurator.doNacosConfigure(url);
        } else {
            throw new LogbackException("Unexpected filename extension of file [" + url.toString() + "]. Should be either .groovy or .xml");
        }
    }
}
