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

package com.alibaba.nacos.config.server.context;

import com.alibaba.nacos.config.server.configuration.datasource.DataSourceType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * NacosApplicationContextInitializer.
 *
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
public class NacosApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    
    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        MutablePropertySources mutablePropertySources = configurableApplicationContext.getEnvironment()
                .getPropertySources();
        String dataSourceType = getDatasourceType(mutablePropertySources);
        Map<String, Object> source = buildSource(dataSourceType);
        MapPropertySource propertiesPropertySource = new MapPropertySource("jpaOrMongoDbSwitch", source);
        mutablePropertySources.addLast(propertiesPropertySource);
    }
    
    private Map<String, Object> buildSource(String dataSourceType) {
        Map<String, Object> source = new HashMap<>(8);
        DataSourceType dataSourceTypeEnum = Optional.ofNullable(DataSourceType.resolve(dataSourceType))
                .orElseThrow(() -> new RuntimeException());
        if (dataSourceTypeEnum.equals(DataSourceType.MYSQL) || dataSourceTypeEnum.equals(DataSourceType.ORACLE)
                || dataSourceTypeEnum.equals(DataSourceType.POSTGRESQL) || dataSourceTypeEnum
                .equals(DataSourceType.EMBEDDED)) {
            source.put("spring.data.mongodb.repositories.type", "NONE");
        }
        return source;
    }
    
    private String getDatasourceType(MutablePropertySources mutablePropertySources) {
        Iterator<PropertySource<?>> iterable = mutablePropertySources.iterator();
        while (iterable.hasNext()) {
            PropertySource<?> propertySource = iterable.next();
            String datasourceType = (String) propertySource.getProperty("nacos.datasource.type");
            if (StringUtils.isNotEmpty(datasourceType)) {
                return datasourceType;
            }
        }
        throw new RuntimeException("没有获取到datasourceType");
    }
    
}
