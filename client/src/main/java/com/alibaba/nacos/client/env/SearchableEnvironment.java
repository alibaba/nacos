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

package com.alibaba.nacos.client.env;

import com.alibaba.nacos.client.constant.Constants;
import com.alibaba.nacos.client.env.convert.CompositeConverter;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Searchable environment.
 *
 * @author onewe
 */
class SearchableEnvironment implements NacosEnvironment {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchableEnvironment.class);
    
    private static final PropertiesPropertySource PROPERTIES_PROPERTY_SOURCE = new PropertiesPropertySource();
    
    private static final JvmArgsPropertySource JVM_ARGS_PROPERTY_SOURCE = new JvmArgsPropertySource();
    
    private static final SystemEnvPropertySource SYSTEM_ENV_PROPERTY_SOURCE = new SystemEnvPropertySource();
    
    private static final List<SourceType> DEFAULT_ORDER = Arrays.asList(SourceType.PROPERTIES, SourceType.JVM,
            SourceType.SYS);
    
    private static final List<AbstractPropertySource> PROPERTY_SOURCES;
    
    private static final CompositeConverter CONVERTER = new CompositeConverter();
    
    static {
        String searchPattern = JVM_ARGS_PROPERTY_SOURCE.getProperty(Constants.SysEnv.NACOS_ENV_FIRST);
        if (StringUtils.isBlank(searchPattern)) {
            searchPattern = SYSTEM_ENV_PROPERTY_SOURCE.getProperty(Constants.SysEnv.NACOS_ENV_FIRST);
        }
        final List<AbstractPropertySource> sourceList = resolvePattern(searchPattern, PROPERTIES_PROPERTY_SOURCE,
                JVM_ARGS_PROPERTY_SOURCE, SYSTEM_ENV_PROPERTY_SOURCE);
        sourceList.add(new DefaultSettingPropertySource());
        
        PROPERTY_SOURCES = sourceList;
    }
    
    private static List<AbstractPropertySource> resolvePattern(String pattern,
            AbstractPropertySource... propertySources) {
        
        if (StringUtils.isBlank(pattern)) {
            return sortPropertySourceDefaultOrder(propertySources);
        }
        
        try {
            final SourceType sourceType = SourceType.valueOf(pattern.toUpperCase());
            if (SourceType.DEFAULT_SETTING.equals(sourceType)) {
                return sortPropertySourceDefaultOrder(propertySources);
            }
            return sortPropertySource(sourceType, propertySources);
        } catch (Exception e) {
            LOGGER.error("first source type parse error, it will be used default order!");
            return sortPropertySourceDefaultOrder(propertySources);
        }
    }
    
    private static List<AbstractPropertySource> sortPropertySourceDefaultOrder(
            AbstractPropertySource... propertySources) {
        final Map<SourceType, AbstractPropertySource> sourceMap = Arrays.stream(propertySources)
                .collect(Collectors.toMap(AbstractPropertySource::getType, propertySource -> propertySource));
        final List<AbstractPropertySource> collect = DEFAULT_ORDER.stream().map(sourceMap::get)
                .collect(Collectors.toList());
        LOGGER.info("environment search order:PROPERTIES->JVM->SYS->DEFAULT_SETTING");
        return collect;
    }
    
    private static List<AbstractPropertySource> sortPropertySource(SourceType firstType,
            AbstractPropertySource... propertySources) {
        List<SourceType> tempList = new ArrayList<>(4);
        tempList.add(firstType);
        
        final Map<SourceType, AbstractPropertySource> sourceMap = Arrays.stream(propertySources)
                .collect(Collectors.toMap(AbstractPropertySource::getType, propertySource -> propertySource));
        final List<AbstractPropertySource> collect = DEFAULT_ORDER.stream()
                .filter(sourceType -> !sourceType.equals(firstType)).collect(() -> tempList, List::add, List::addAll)
                .stream().map(sourceMap::get).filter(Objects::nonNull).collect(Collectors.toList());
        
        StringBuilder orderInfo = new StringBuilder("environment search order:");
        for (AbstractPropertySource abstractPropertySource : collect) {
            orderInfo.append(abstractPropertySource.getType().toString()).append("->");
        }
        orderInfo.append("DEFAULT_SETTING");
        
        LOGGER.info(orderInfo.toString());
        
        return collect;
    }
    
    @Override
    public String getProperty(String key) {
        return getProperty(key, null);
    }
    
    @Override
    public String getProperty(String key, String defaultValue) {
        return this.search(this.getScope(), key, String.class).orElse(defaultValue);
    }
    
    @Override
    public Boolean getBoolean(String key) {
        return getBoolean(key, null);
    }
    
    @Override
    public Boolean getBoolean(String key, Boolean defaultValue) {
        return this.search(this.getScope(), key, Boolean.class).orElse(defaultValue);
    }
    
    @Override
    public Integer getInteger(String key) {
        return getInteger(key, null);
    }
    
    @Override
    public Integer getInteger(String key, Integer defaultValue) {
        return this.search(this.getScope(), key, Integer.class).orElse(defaultValue);
    }
    
    @Override
    public Long getLong(String key) {
        return getLong(key, null);
    }
    
    @Override
    public Long getLong(String key, Long defaultValue) {
        return this.search(this.getScope(), key, Long.class).orElse(defaultValue);
    }
    
    @Override
    public void setProperty(String key, String value) {
        PROPERTIES_PROPERTY_SOURCE.setProperty(this.getScope(), key, value);
    }
    
    @Override
    public void addProperties(Properties properties) {
        PROPERTIES_PROPERTY_SOURCE.addProperties(this.getScope(), properties);
    }
    
    @Override
    public Properties asProperties() {
        Properties properties = new Properties();
        final ListIterator<AbstractPropertySource> iterator = PROPERTY_SOURCES.listIterator(
                PROPERTY_SOURCES.size());
        while (iterator.hasPrevious()) {
            final AbstractPropertySource previous = iterator.previous();
            if (previous instanceof PropertiesPropertySource) {
                properties.putAll(((PropertiesPropertySource) previous).asProperties(this.getScope()));
            } else {
                properties.putAll(previous.asProperties());
            }
        }
        return properties;
    }
    
    @Override
    public boolean containsKey(String key) {
        return this.containsKey(this.getScope(), key);
    }
    
    private boolean containsKey(ApplyScope scope, String key) {
        for (AbstractPropertySource propertySource : PROPERTY_SOURCES) {
            boolean containing;
            if (propertySource instanceof PropertiesPropertySource) {
                containing = ((PropertiesPropertySource) propertySource).containsKey(scope, key);
            } else {
                containing = propertySource.containsKey(key);
            }
            if (containing) {
                return true;
            }
        }
        return false;
    }
    
    private <T> Optional<T> search(ApplyScope scope, String key, Class<T> targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("target type must not be null!");
        }
        
        for (AbstractPropertySource propertySource : PROPERTY_SOURCES) {
            String value;
            if (propertySource instanceof PropertiesPropertySource) {
                value = ((PropertiesPropertySource) propertySource).getProperty(scope, key);
            } else {
                value = propertySource.getProperty(key);
            }
            if (value != null) {
                if (String.class.isAssignableFrom(targetType)) {
                    try {
                        return (Optional<T>) Optional.of(value);
                    } catch (Exception e) {
                        LOGGER.error("target type convert error", e);
                        return Optional.empty();
                    }
                    
                }
                return Optional.ofNullable(CONVERTER.convert(value, targetType));
            }
        }
        return Optional.empty();
    }
    
    protected ApplyScope getScope() {
        return ApplyScope.GLOBAL;
    }
    
}
