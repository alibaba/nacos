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
package com.alibaba.nacos.client.logging.log4j2;

import com.alibaba.nacos.client.logging.AbstractNacosLogging;
import com.alibaba.nacos.client.utils.StringUtils;
import com.alibaba.nacos.common.util.ClassUtils;
import com.alibaba.nacos.common.util.ResourceUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.composite.CompositeConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Support for Log4j version 2.7 or higher
 *
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @since 0.9.0
 */
public class Log4J2NacosLogging extends AbstractNacosLogging {

    private static final String NACOS_LOG4J2_LOCATION = "classpath:nacos-log4j2.xml";

    private static final String FILE_PROTOCOL = "file";

    private static final String YAML_PARSER_CLASS_NAME = "com.fasterxml.jackson.dataformat.yaml.YAMLParser";

    private static final String JSON_PARSER_CLASS_NAME = "com.fasterxml.jackson.databind.ObjectMapper";

    private Set<String> locationList = new HashSet<String>();

    public Log4J2NacosLogging() {
        String location = getLocation(NACOS_LOG4J2_LOCATION);
        if (!StringUtils.isBlank(location)) {
            locationList.add(location);
        }
    }

    @Override
    public void loadConfiguration() {
        String config = findConfig(getCurrentlySupportedConfigLocations());
        if (config != null) {
            locationList.add(config);
        }

        final List<AbstractConfiguration> configurations = new ArrayList<AbstractConfiguration>();

        LoggerContext loggerContext = (LoggerContext)LogManager.getContext(false);
        for (String location : locationList) {
            try {
                Configuration configuration = loadConfiguration(loggerContext, location);
                if (configuration instanceof AbstractConfiguration) {
                    configurations.add((AbstractConfiguration)configuration);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Could not initialize Log4J2 Nacos logging from " + location, e);
            }
        }

        // since log4j 2.6
        CompositeConfiguration compositeConfiguration = new CompositeConfiguration(configurations);
        loggerContext.start(compositeConfiguration);
    }

    private Configuration loadConfiguration(LoggerContext loggerContext, String location) {
        try {
            URL url = ResourceUtils.getResourceURL(location);
            ConfigurationSource source = getConfigurationSource(url);
            // since log4j 2.7 getConfiguration(LoggerContext loggerContext, ConfigurationSource source)
            return ConfigurationFactory.getInstance().getConfiguration(loggerContext, source);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Could not initialize Log4J2 logging from " + location, e);
        }
    }

    private ConfigurationSource getConfigurationSource(URL url) throws IOException {
        InputStream stream = url.openStream();
        if (FILE_PROTOCOL.equals(url.getProtocol())) {
            return new ConfigurationSource(stream, ResourceUtils.getResourceAsFile(url));
        }
        return new ConfigurationSource(stream, url);
    }

    private String[] getCurrentlySupportedConfigLocations() {
        List<String> supportedConfigLocations = new ArrayList<String>();

        if (ClassUtils.isPresent(YAML_PARSER_CLASS_NAME)) {
            Collections.addAll(supportedConfigLocations, "log4j2.yaml", "log4j2.yml");
        }

        if (ClassUtils.isPresent(JSON_PARSER_CLASS_NAME)) {
            Collections.addAll(supportedConfigLocations, "log4j2.json", "log4j2.jsn");
        }
        supportedConfigLocations.add("log4j2.xml");

        return supportedConfigLocations.toArray(new String[supportedConfigLocations.size()]);
    }

    private String findConfig(String[] locations) {
        for (String location : locations) {
            ClassLoader defaultClassLoader = ClassUtils.getDefaultClassLoader();
            if (defaultClassLoader != null && defaultClassLoader.getResource(location) != null) {
                return "classpath:" + location;
            }
        }
        return null;
    }

}
