/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.utils;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

/**
 * Datasource dialect plugin type policy.
 *
 * @author Nacos
 */
public class DatasourceDialectPluginTypePolicy implements PluginTypePolicy {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DatasourceDialectPluginTypePolicy.class);
    
    private static final String DEFAULT_DIALECT = "derby";
    
    private String selectedPlugin = DEFAULT_DIALECT;
    
    @Override
    public void initialize(PluginTypeConfiguration configuration) {
        selectedPlugin = resolveSelectedPlugin(configuration);
    }
    
    @Override
    public PluginType getPluginType() {
        return PluginType.DATASOURCE_DIALECT;
    }
    
    @Override
    public boolean isActive(PluginTypeConfiguration configuration) {
        return true;
    }
    
    @Override
    public boolean isPluginEnabledByDefault(String pluginName,
        PluginTypeConfiguration configuration) {
        return pluginName.equalsIgnoreCase(selectedPlugin);
    }
    
    @Override
    public Set<String> getRequiredPluginNames(PluginTypeConfiguration configuration) {
        return Collections.singleton(selectedPlugin);
    }
    
    @Override
    public String getSelectionProperty() {
        return PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY;
    }
    
    @Override
    public String getActivationDescription() {
        return "the persistence subsystem always requires a datasource dialect";
    }
    
    private String resolveSelectedPlugin(PluginTypeConfiguration configuration) {
        String selected =
            configuration.getProperty(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY);
        if (StringUtils.isNotBlank(selected)) {
            return selected.trim();
        }
        selected = configuration.getProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY);
        if (StringUtils.isNotBlank(selected)) {
            LOGGER.warn("[DatasourceDialectPluginTypePolicy] Datasource dialect selection '{}' "
                + "is read from legacy property '{}'. Migrate to '{}'.", selected,
                PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY,
                PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY);
            return selected.trim();
        }
        return DEFAULT_DIALECT;
    }
}
