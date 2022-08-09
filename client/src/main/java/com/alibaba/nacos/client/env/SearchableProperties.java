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
 * Searchable NProperties.
 *
 * @author onewe
 */
class SearchableProperties implements NProperties {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchableProperties.class);
    
    private static final JvmArgsPropertySource JVM_ARGS_PROPERTY_SOURCE = new JvmArgsPropertySource();
    
    private static final SystemEnvPropertySource SYSTEM_ENV_PROPERTY_SOURCE = new SystemEnvPropertySource();
    
    private static final DefaultSettingPropertySource DEFAULT_SETTING_PROPERTY_SOURCE = new DefaultSettingPropertySource();
    
    private static final List<SourceType> DEFAULT_ORDER = Arrays.asList(SourceType.PROPERTIES, SourceType.JVM,
            SourceType.SYS);
    
    private static final CompositeConverter CONVERTER = new CompositeConverter();
    
    static final SearchableProperties INSTANCE = new SearchableProperties();
    
    private final List<AbstractPropertySource> propertySources;
    
    private final PropertiesPropertySource propertiesPropertySource;
    
    private SearchableProperties() {
        this(new PropertiesPropertySource());
    }
    
    private SearchableProperties(PropertiesPropertySource propertiesPropertySource) {
        this.propertiesPropertySource = propertiesPropertySource;
        final List<AbstractPropertySource> sourceList = build(propertiesPropertySource,
                JVM_ARGS_PROPERTY_SOURCE, SYSTEM_ENV_PROPERTY_SOURCE);
        sourceList.add(DEFAULT_SETTING_PROPERTY_SOURCE);
        this.propertySources = sourceList;
    }
    
    @Override
    public String getProperty(String key) {
        return getProperty(key, null);
    }
    
    @Override
    public String getProperty(String key, String defaultValue) {
        return this.search(key, String.class).orElse(defaultValue);
    }
    
    @Override
    public Boolean getBoolean(String key) {
        return getBoolean(key, null);
    }
    
    @Override
    public Boolean getBoolean(String key, Boolean defaultValue) {
        return this.search(key, Boolean.class).orElse(defaultValue);
    }
    
    @Override
    public Integer getInteger(String key) {
        return getInteger(key, null);
    }
    
    @Override
    public Integer getInteger(String key, Integer defaultValue) {
        return this.search(key, Integer.class).orElse(defaultValue);
    }
    
    @Override
    public Long getLong(String key) {
        return getLong(key, null);
    }
    
    @Override
    public Long getLong(String key, Long defaultValue) {
        return this.search(key, Long.class).orElse(defaultValue);
    }
    
    @Override
    public void setProperty(String key, String value) {
        propertiesPropertySource.setProperty(key, value);
    }
    
    @Override
    public void addProperties(Properties properties) {
        propertiesPropertySource.addProperties(properties);
    }
    
    @Override
    public Properties asProperties() {
        Properties properties = new Properties();
        final ListIterator<AbstractPropertySource> iterator = propertySources.listIterator(
                propertySources.size());
        while (iterator.hasPrevious()) {
            final AbstractPropertySource previous = iterator.previous();
            properties.putAll(previous.asProperties());
        }
        return properties;
    }
    
    @Override
    public boolean containsKey(String key) {
        for (AbstractPropertySource propertySource : propertySources) {
            final boolean containing = propertySource.containsKey(key);
            if (containing) {
                return true;
            }
        }
        return false;
    }
    
    private <T> Optional<T> search(String key, Class<T> targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("target type must not be null!");
        }
        
        for (AbstractPropertySource propertySource : propertySources) {
            final String value = propertySource.getProperty(key);
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
    
    private List<AbstractPropertySource> build(AbstractPropertySource... propertySources) {
    
        String firstEnv = JVM_ARGS_PROPERTY_SOURCE.getProperty(Constants.SysEnv.NACOS_ENV_FIRST);
        if (StringUtils.isBlank(firstEnv)) {
            firstEnv = SYSTEM_ENV_PROPERTY_SOURCE.getProperty(Constants.SysEnv.NACOS_ENV_FIRST);
        }
        
        if (StringUtils.isBlank(firstEnv)) {
            return sortPropertySourceDefaultOrder(propertySources);
        }
        
        try {
            final SourceType sourceType = SourceType.valueOf(firstEnv.toUpperCase());
            if (SourceType.DEFAULT_SETTING.equals(sourceType) || SourceType.PROPERTIES.equals(sourceType)) {
                return sortPropertySourceDefaultOrder(propertySources);
            }
            return sortPropertySource(sourceType, propertySources);
        } catch (Exception e) {
            LOGGER.error("first source type parse error, it will be used default order!");
            return sortPropertySourceDefaultOrder(propertySources);
        }
    }
    
    private List<AbstractPropertySource> sortPropertySourceDefaultOrder(
            AbstractPropertySource... propertySources) {
        final Map<SourceType, AbstractPropertySource> sourceMap = Arrays.stream(propertySources)
                .collect(Collectors.toMap(AbstractPropertySource::getType, propertySource -> propertySource));
        final List<AbstractPropertySource> collect = DEFAULT_ORDER.stream().map(sourceMap::get)
                .collect(Collectors.toList());
        LOGGER.info("properties search order:PROPERTIES->JVM->SYS->DEFAULT_SETTING");
        return collect;
    }
    
    private List<AbstractPropertySource> sortPropertySource(SourceType firstType,
            AbstractPropertySource... propertySources) {
        List<SourceType> tempList = new ArrayList<>(4);
        tempList.add(firstType);
        
        final Map<SourceType, AbstractPropertySource> sourceMap = Arrays.stream(propertySources)
                .collect(Collectors.toMap(AbstractPropertySource::getType, propertySource -> propertySource));
        final List<AbstractPropertySource> collect = DEFAULT_ORDER.stream()
                .filter(sourceType -> !sourceType.equals(firstType)).collect(() -> tempList, List::add, List::addAll)
                .stream().map(sourceMap::get).filter(Objects::nonNull).collect(Collectors.toList());
        
        StringBuilder orderInfo = new StringBuilder("properties search order:");
        for (AbstractPropertySource abstractPropertySource : collect) {
            orderInfo.append(abstractPropertySource.getType().toString()).append("->");
        }
        orderInfo.append("DEFAULT_SETTING");
        
        LOGGER.info(orderInfo.toString());
        
        return collect;
    }
    
    @Override
    public NProperties derive() {
        return new SearchableProperties(new PropertiesPropertySource(this.propertiesPropertySource));
    }
    
    @Override
    public NProperties derive(Properties properties) {
        final NProperties nProperties = this.derive();
        nProperties.addProperties(properties);
        return nProperties;
    }
}
