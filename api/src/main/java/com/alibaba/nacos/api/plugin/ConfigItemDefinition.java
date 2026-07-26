/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.plugin;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Plugin configuration item definition.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public class ConfigItemDefinition implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Configuration key.
     */
    private String key;
    
    /**
     * Display name.
     */
    private String name;
    
    /**
     * Description.
     */
    private String description;
    
    /**
     * Default value.
     */
    private String defaultValue;
    
    /**
     * Configuration item type.
     */
    private ConfigItemType type;
    
    /**
     * Whether this item is required.
     */
    private boolean required;
    
    /**
     * Enum values (when type is ENUM).
     */
    private List<String> enumValues;
    
    /**
     * Historical static configuration keys for compatibility.
     */
    private List<String> aliases = new ArrayList<>();
    
    /**
     * Whether this item contains sensitive data.
     */
    private boolean sensitive;
    
    /**
     * Configuration item effect mode.
     */
    private ConfigItemEffectMode effectMode = ConfigItemEffectMode.RESTART;
    
    public ConfigItemDefinition() {
    }
    
    public ConfigItemDefinition(String key, String name, ConfigItemType type) {
        this.key = key;
        this.name = name;
        this.type = type;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getDefaultValue() {
        return defaultValue;
    }
    
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
    
    public ConfigItemType getType() {
        return type;
    }
    
    public void setType(ConfigItemType type) {
        this.type = type;
    }
    
    public boolean isRequired() {
        return required;
    }
    
    public void setRequired(boolean required) {
        this.required = required;
    }
    
    public List<String> getEnumValues() {
        return enumValues;
    }
    
    public void setEnumValues(List<String> enumValues) {
        this.enumValues = enumValues;
    }
    
    public List<String> getAliases() {
        return aliases;
    }
    
    public void setAliases(List<String> aliases) {
        this.aliases = aliases;
    }
    
    public boolean isSensitive() {
        return sensitive;
    }
    
    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }
    
    public ConfigItemEffectMode getEffectMode() {
        return effectMode;
    }
    
    public void setEffectMode(ConfigItemEffectMode effectMode) {
        this.effectMode = effectMode;
    }
    
    /**
     * Builder for ConfigItemDefinition.
     */
    public static class Builder {
        
        private final ConfigItemDefinition definition;
        
        public Builder(String key, String name, ConfigItemType type) {
            this.definition = new ConfigItemDefinition(key, name, type);
        }
        
        public Builder description(String description) {
            definition.setDescription(description);
            return this;
        }
        
        public Builder defaultValue(String defaultValue) {
            definition.setDefaultValue(defaultValue);
            return this;
        }
        
        public Builder required(boolean required) {
            definition.setRequired(required);
            return this;
        }
        
        public Builder enumValues(List<String> enumValues) {
            definition.setEnumValues(enumValues);
            return this;
        }
        
        public Builder aliases(List<String> aliases) {
            definition.setAliases(aliases);
            return this;
        }
        
        public Builder sensitive(boolean sensitive) {
            definition.setSensitive(sensitive);
            return this;
        }
        
        public Builder effectMode(ConfigItemEffectMode effectMode) {
            definition.setEffectMode(effectMode);
            return this;
        }
        
        public ConfigItemDefinition build() {
            return definition;
        }
    }
}
