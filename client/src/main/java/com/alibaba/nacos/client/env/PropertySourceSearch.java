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
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

class PropertySourceSearch {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertySourceSearch.class);
    
    private static final List<SourceType> DEFAULT_ORDER = Arrays.asList(SourceType.PROPERTIES, SourceType.JVM,
            SourceType.SYS);
    
    private final List<AbstractPropertySource> propertySources;
    
    private final CompositeConverter converter;
    
    private PropertySourceSearch(List<AbstractPropertySource> propertySources) {
        this.propertySources = propertySources;
        this.propertySources.add(new DefaultSettingPropertySource());
        this.converter = new CompositeConverter();
    }
    
    static PropertySourceSearch build(Properties properties) {
        if (properties == null) {
            properties = new Properties();
        }
        PropertiesPropertySource customizePropertySource = new PropertiesPropertySource(properties);
        JvmArgsPropertySource jvmArgsPropertySource = new JvmArgsPropertySource();
        SystemEnvPropertySource systemEnvPropertySource = new SystemEnvPropertySource();
    
        String searchPattern = jvmArgsPropertySource.getProperty(Constants.SysEnv.NACOS_ENVS_SEARCH);
        if (StringUtils.isBlank(searchPattern)) {
            searchPattern = systemEnvPropertySource.getProperty(Constants.SysEnv.NACOS_ENVS_SEARCH);
        }
        
        return resolve(searchPattern, customizePropertySource, jvmArgsPropertySource, systemEnvPropertySource);
    }
    
    private static PropertySourceSearch resolve(String pattern, AbstractPropertySource... propertySources) {
        
        if (StringUtils.isBlank(pattern)) {
            return createPropertySourceSearchWithDefaultOrder(propertySources);
        }
        
        try {
            final SourceType sourceType = SourceType.valueOf(pattern.toUpperCase());
            return createPropertySourceSearchByFirstType(sourceType, propertySources);
        } catch (Exception e) {
            LOGGER.error("first source type parse error, it will be use default order!");
            return createPropertySourceSearchWithDefaultOrder(propertySources);
        }
    }
    
    private static PropertySourceSearch createPropertySourceSearchWithDefaultOrder(AbstractPropertySource... propertySources) {
        final Map<SourceType, AbstractPropertySource> sourceMap = Arrays.stream(propertySources)
                .collect(Collectors.toMap(AbstractPropertySource::getType, propertySource -> propertySource));
        final List<AbstractPropertySource> collect = DEFAULT_ORDER.stream().map(sourceMap::get).collect(Collectors.toList());
        return new PropertySourceSearch(collect);
    }
    
    private static PropertySourceSearch createPropertySourceSearchByFirstType(SourceType firstType,
            AbstractPropertySource... propertySources) {
        
        List<SourceType> tempList = new ArrayList<>(4);
        tempList.add(firstType);
        
        final Map<SourceType, AbstractPropertySource> sourceMap = Arrays.stream(propertySources)
                .collect(Collectors.toMap(AbstractPropertySource::getType, propertySource -> propertySource));
        final List<AbstractPropertySource> collect = DEFAULT_ORDER.stream().filter(sourceType -> !sourceType.equals(firstType))
                .collect(() -> tempList, List::add, List::addAll).stream().map(sourceMap::get)
                .collect(Collectors.toList());
        
        return new PropertySourceSearch(collect);
    }
    
    <T> Optional<T> search(String key, Class<T> targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("target type must be not null!");
        }
    
        for (AbstractPropertySource propertySource : propertySources) {
            final String value = propertySource.getProperty(key);
            if (value != null) {
                if (String.class.isAssignableFrom(targetType)) {
                    return (Optional<T>) Optional.of(value);
                }
                return Optional.ofNullable(converter.convert(value, targetType));
            }
        }
        return Optional.empty();
    }
}
