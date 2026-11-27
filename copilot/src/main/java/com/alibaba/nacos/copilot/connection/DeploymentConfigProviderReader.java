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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads model connections from {@code nacos.copilot.connections.<name>.*}.
 *
 * <p>Property sources are scoped to that prefix through Spring's configuration-property view
 * instead of filtering raw property names, so relaxed environment-variable and property-file
 * spellings and their precedence keep working. Each connection is bound separately, so a
 * malformed block only drops that connection instead of hiding the others.
 *
 * <p>Connection names follow Spring's key conventions: declarations and environment-variable
 * spellings are resolved relaxed, so {@code Prod-Qwen} and {@code prod-qwen} name the same
 * connection. The environment-variable form replaces dots and hyphens with underscores, for
 * example {@code NACOS_COPILOT_CONNECTIONS_PROD_QWEN_MODEL} or
 * {@code NACOS_COPILOT_CONNECTIONS_PROD_QWEN_API_KEY}. Connection values are used verbatim.
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
    
    private static final ConfigurationPropertyName PREFIX_NAME =
        ConfigurationPropertyName.of("nacos.copilot.connections");
    
    private static final int PREFIX_ELEMENTS = PREFIX_NAME.getNumberOfElements();
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DeploymentConfigProviderReader.class);
    
    @Override
    public int order() {
        return ORDER;
    }
    
    @Override
    public List<CopilotModelConnection> read(ProviderReadContext context) {
        ConfigurableEnvironment environment = context.getDeployment();
        List<ConfigurationPropertySource> sources = scopedSources(environment);
        Binder binder = binder(sources, environment);
        List<CopilotModelConnection> result = new ArrayList<>();
        for (Map.Entry<String, ConfigurationPropertyName> entry : connectionNames(sources)
            .entrySet()) {
            try {
                BindResult<DeploymentConnectionDefinition> bound =
                    binder.bind(entry.getValue(),
                        Bindable.of(DeploymentConnectionDefinition.class));
                if (bound.isBound()) {
                    result.add(bound.get().toConnection(entry.getKey()));
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to read Copilot connection [{}], skipping it: {}",
                    entry.getKey(), e.getMessage());
            }
        }
        return result;
    }
    
    /**
     * Restrict each captured source to the connection prefix, keeping source order so Spring
     * still decides precedence within the deployment configuration.
     */
    private List<ConfigurationPropertySource> scopedSources(ConfigurableEnvironment environment) {
        List<ConfigurationPropertySource> scoped = new ArrayList<>();
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            scoped.add(source.filter(PREFIX_NAME::isAncestorOf));
        }
        return scoped;
    }
    
    /**
     * The binder only looks inside the scoped sources, while placeholders may reference any
     * captured deployment property.
     */
    private Binder binder(List<ConfigurationPropertySource> sources,
        ConfigurableEnvironment environment) {
        PlaceholdersResolver placeholders = value -> value instanceof String
            ? environment.resolveRequiredPlaceholders((String) value) : value;
        return new Binder(sources, placeholders);
    }
    
    /**
     * Map normalized connection names to the prefix each block is bound from.
     */
    private Map<String, ConfigurationPropertyName> connectionNames(
        List<ConfigurationPropertySource> sources) {
        Map<String, ConfigurationPropertyName> names = new LinkedHashMap<>();
        for (ConfigurationPropertySource source : sources) {
            if (!(source instanceof IterableConfigurationPropertySource)) {
                continue;
            }
            for (ConfigurationPropertyName name : (IterableConfigurationPropertySource) source) {
                if (!PREFIX_NAME.isAncestorOf(name)
                    || name.getNumberOfElements() <= PREFIX_ELEMENTS) {
                    continue;
                }
                String element = name.getElement(PREFIX_ELEMENTS,
                    ConfigurationPropertyName.Form.DASHED);
                names.putIfAbsent(ConnectionTable.normalizeName(element),
                    PREFIX_NAME.append(element));
            }
        }
        return names;
    }
}
