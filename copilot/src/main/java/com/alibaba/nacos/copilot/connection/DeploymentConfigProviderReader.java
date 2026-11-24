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
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads model connections from {@code nacos.copilot.connections.<name>.*}.
 *
 * <p>Each connection is bound separately, so a malformed block only drops that connection
 * instead of hiding the others.
 *
 * <p>Connection names are case-insensitive. Each block is bound through a synthetic source
 * so the declared spelling does not reach Spring, which rejects property names containing
 * upper-case characters. When one connection is declared under several spellings, the
 * lower-case spelling wins if present, otherwise the first spelling in lexicographic order;
 * the other spellings are reported and ignored.
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
    
    private static final String BIND_PREFIX = "connection";
    
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9-]+");
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DeploymentConfigProviderReader.class);
    
    @Override
    public int order() {
        return ORDER;
    }
    
    @Override
    public List<CopilotModelConnection> read(ProviderReadContext context) {
        ConfigurableEnvironment environment = context.getDeployment();
        List<CopilotModelConnection> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : connectionNames(environment).entrySet()) {
            try {
                BindResult<DeploymentConnectionDefinition> bound =
                    bind(environment, entry.getValue());
                if (bound.isBound()) {
                    result.add(bound.get().toConnection(entry.getKey()));
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to read Copilot connection [{}], skipping it: {}",
                    entry.getValue(), e.getMessage());
            }
        }
        return result;
    }
    
    /**
     * Bind one declared block through a synthetic source, so properties of a spelling that
     * was not selected can not leak into the connection.
     */
    private BindResult<DeploymentConnectionDefinition> bind(ConfigurableEnvironment environment,
        String declaredName) {
        List<PropertySource<?>> blocks = new ArrayList<>();
        String blockPrefix = PREFIX + declaredName + ".";
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource)) {
                continue;
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            for (String propertyName : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                if (!propertyName.startsWith(blockPrefix)) {
                    continue;
                }
                String suffix = propertyName.substring(blockPrefix.length());
                Object value = source.getProperty(propertyName);
                if (StringUtils.isNotBlank(suffix) && value != null) {
                    properties.put(BIND_PREFIX + "." + suffix, value);
                }
            }
            blocks.add(new MapPropertySource(source.getName(), properties));
        }
        // Binder selects the winning relaxed property before resolving its placeholders.
        return new Binder(ConfigurationPropertySources.from(blocks),
            value -> value instanceof String
                ? environment.resolveRequiredPlaceholders((String) value) : value)
            .bind(BIND_PREFIX, Bindable.of(DeploymentConnectionDefinition.class));
    }
    
    /**
     * Map normalized connection names to the declared spelling used to bind each block.
     */
    private Map<String, String> connectionNames(ConfigurableEnvironment environment) {
        Map<String, String> names = new LinkedHashMap<>();
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
                String declaredName = separator < 0 ? remainder : remainder.substring(0, separator);
                if (StringUtils.isBlank(declaredName)) {
                    continue;
                }
                if (!VALID_NAME.matcher(declaredName).matches()) {
                    LOGGER.warn("Copilot connection name [{}] is not valid: names use ASCII "
                        + "letters, digits, and '-'; skipping it", declaredName);
                    continue;
                }
                String normalized = ConnectionTable.normalizeName(declaredName);
                String existing = names.get(normalized);
                if (existing == null) {
                    names.put(normalized, declaredName);
                } else if (!existing.equals(declaredName)) {
                    String kept = preferredSpelling(normalized, existing, declaredName);
                    String ignored = kept.equals(existing) ? declaredName : existing;
                    LOGGER.warn("Copilot connection [{}] is declared under multiple spellings, "
                        + "using [{}] and ignoring [{}]", normalized, kept, ignored);
                    names.put(normalized, kept);
                }
            }
        }
        return names;
    }
    
    private String preferredSpelling(String normalized, String first, String second) {
        if (first.equals(normalized)) {
            return first;
        }
        if (second.equals(normalized)) {
            return second;
        }
        return first.compareTo(second) <= 0 ? first : second;
    }
}
