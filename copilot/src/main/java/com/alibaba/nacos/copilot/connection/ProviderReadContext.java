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

import com.alibaba.nacos.copilot.config.CopilotProperties;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Captured deployment input and effective legacy settings shared by all readers in a refresh.
 *
 * @author nacos
 */
public final class ProviderReadContext {
    
    public static final String ENV_API_KEY = "COPILOT_API_KEY";
    
    private static final String[] NON_ENUMERABLE_KEYS = {
        "nacos.copilot.enabled", "nacos.copilot.default-namespace",
        "nacos.copilot.defaultNamespace", "nacos.copilot.api-key", "nacos.copilot.apiKey",
        "nacos.copilot.model", "nacos.copilot.studio-url", "nacos.copilot.studioUrl",
        "nacos.copilot.studio-project", "nacos.copilot.studioProject",
        "nacos.copilot.selected-connection", "nacos.copilot.selectedConnection", ENV_API_KEY
    };
    
    private final ConfigurableEnvironment deployment;
    
    private final CopilotProperties legacyConfig;
    
    private final boolean legacyDeclared;
    
    private final CredentialSource legacyCredentialSource;
    
    /**
     * Create a context from the inputs already captured and resolved by the manager.
     *
     * @param deployment captured deployment property sources, not the live environment
     * @param legacyConfig effective Copilot settings
     * @param legacyDeclared whether raw legacy fields or an environment credential exist
     * @param legacyCredentialSource origin of the resolved legacy credential, or null
     */
    public ProviderReadContext(ConfigurableEnvironment deployment, CopilotProperties legacyConfig,
        boolean legacyDeclared, CredentialSource legacyCredentialSource) {
        this.deployment = deployment;
        this.legacyConfig = legacyConfig;
        this.legacyDeclared = legacyDeclared;
        this.legacyCredentialSource = legacyCredentialSource;
    }
    
    /**
     * Detach the current inputs once, retaining source order, relaxed environment names and
     * placeholder inputs. Null declarations become empty declarations so Spring binding
     * cannot skip a clear and inherit a lower source. The attached Spring aggregate is
     * excluded because it delegates to the live sources already being copied.
     *
     * @param environment live deployment environment
     * @return detached environment for this refresh
     */
    public static ConfigurableEnvironment captureDeployment(ConfigurableEnvironment environment) {
        StandardEnvironment captured = new StandardEnvironment();
        captured.getPropertySources().remove(
            StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        captured.getPropertySources().remove(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        captured.setConversionService(environment.getConversionService());
        Map<PropertySource<?>, Map<String, Object>> externalSources = new LinkedHashMap<>();
        Set<String> knownNames = new LinkedHashSet<>(Arrays.asList(NON_ENUMERABLE_KEYS));
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (ConfigurationPropertySources.isAttachedConfigurationPropertySource(source)) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            if (source instanceof EnumerablePropertySource) {
                for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                    Object value = source.getProperty(name);
                    values.put(name, value == null ? "" : value);
                    if (name.startsWith(DeploymentConfigProviderReader.PREFIX)) {
                        knownNames.add(name);
                    }
                }
            } else {
                // Query overrides after all enumerable connection names have been discovered.
                externalSources.put(source, values);
            }
            Map<String, Object> detached = Collections.unmodifiableMap(values);
            captured.getPropertySources().addLast(source instanceof SystemEnvironmentPropertySource
                ? new SystemEnvironmentPropertySource(source.getName(), detached)
                : new MapPropertySource(source.getName(), detached));
        }
        for (Map.Entry<PropertySource<?>, Map<String, Object>> entry : externalSources.entrySet()) {
            for (String name : knownNames) {
                Object value = entry.getKey().getProperty(name);
                if (value != null) {
                    entry.getValue().put(name, value);
                }
            }
        }
        capturePlaceholderInputs(captured, externalSources, knownNames);
        return captured;
    }
    
    private static void capturePlaceholderInputs(ConfigurableEnvironment captured,
        Map<PropertySource<?>, Map<String, Object>> externalSources, Set<String> knownNames) {
        if (externalSources.isEmpty()) {
            return;
        }
        Map<PropertySource<?>, Set<String>> queried = new LinkedHashMap<>();
        for (PropertySource<?> source : externalSources.keySet()) {
            queried.put(source, new HashSet<>(knownNames));
        }
        List<String> inputs = new ArrayList<>();
        for (PropertySource<?> source : captured.getPropertySources()) {
            for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                if (name.startsWith("nacos.copilot.") || name.startsWith("NACOS_COPILOT_")
                    || ENV_API_KEY.equals(name)) {
                    inputs.add(source.getProperty(name).toString());
                }
            }
        }
        PropertyPlaceholderHelper placeholders =
            new PropertyPlaceholderHelper("${", "}", ":", '\\', true);
        for (String input : inputs) {
            try {
                placeholders.replacePlaceholders(input, name -> {
                    for (Map.Entry<PropertySource<?>, Map<String, Object>> entry : externalSources
                        .entrySet()) {
                        if (queried.get(entry.getKey()).add(name)) {
                            Object value = entry.getKey().getProperty(name);
                            if (value != null) {
                                entry.getValue().put(name, value);
                            }
                        }
                    }
                    for (PropertySource<?> source : captured.getPropertySources()) {
                        Object value = source.getProperty(name);
                        if (value != null) {
                            return value.toString();
                        }
                    }
                    return null;
                });
            } catch (IllegalArgumentException ignored) {
                // Binding reports malformed placeholders at their owning field/connection.
            }
        }
    }
    
    /**
     * Get the captured deployment input.
     *
     * @return detached environment
     */
    public ConfigurableEnvironment getDeployment() {
        return deployment;
    }
    
    /**
     * Get the effective legacy configuration.
     *
     * @return configuration
     */
    public CopilotProperties getLegacyConfig() {
        return legacyConfig;
    }
    
    /**
     * Whether a legacy connection is actually declared, rather than inferred from defaults.
     *
     * @return true when legacy configuration exists
     */
    public boolean isLegacyDeclared() {
        return legacyDeclared;
    }
    
    /**
     * Get the effective credential origin.
     *
     * @return credential source, or null when cleared or absent
     */
    public CredentialSource getLegacyCredentialSource() {
        return legacyCredentialSource;
    }
}
