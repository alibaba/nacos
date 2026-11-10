/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.copilot.connection;

import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads model connections from {@code nacos.copilot.connections.<name>.*}.
 *
 * <p>Each connection is bound separately, so a malformed block only drops that connection
 * instead of hiding the others.
 *
 * @author nacos
 */
@Component
public class DeploymentConfigProviderReader implements ProviderReader {
    
    /**
     * Priority of deployment connections. Legacy connections use 100.
     */
    public static final int ORDER = 10;
    
    static final String PREFIX = "nacos.copilot.connections.";
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DeploymentConfigProviderReader.class);
    
    private final ConfigurableEnvironment environment;
    
    /**
     * Create the deployment reader.
     *
     * @param environment Spring environment holding the deployment configuration
     */
    @Autowired
    public DeploymentConfigProviderReader(ConfigurableEnvironment environment) {
        this.environment = environment;
    }
    
    @Override
    public int order() {
        return ORDER;
    }
    
    @Override
    public List<CopilotModelConnection> read(ProviderReadContext context) {
        List<CopilotModelConnection> result = new ArrayList<>();
        for (String name : connectionNames()) {
            try {
                BindResult<DeploymentConnectionDefinition> bound = Binder.get(environment)
                    .bind(PREFIX + name, Bindable.of(DeploymentConnectionDefinition.class));
                if (bound.isBound()) {
                    result.add(bound.get().toConnection(name));
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to read Copilot connection [{}], skipping it: {}", name,
                    e.getMessage());
            }
        }
        return result;
    }
    
    private Set<String> connectionNames() {
        Set<String> names = new LinkedHashSet<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource)) {
                continue;
            }
            for (String propertyName : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                if (!propertyName.startsWith(PREFIX)) {
                    continue;
                }
                String remainder = propertyName.substring(PREFIX.length());
                int separator = remainder.indexOf('.');
                String name = separator < 0 ? remainder : remainder.substring(0, separator);
                if (StringUtils.isNotBlank(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }
}
