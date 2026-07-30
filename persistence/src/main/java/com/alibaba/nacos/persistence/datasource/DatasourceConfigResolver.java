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

package com.alibaba.nacos.persistence.datasource;

import com.alibaba.nacos.common.utils.ConvertUtils;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Resolver for datasource module configuration.
 *
 * @author Nacos
 */
final class DatasourceConfigResolver {
    
    static final String CANONICAL_PREFIX = "nacos.plugin.datasource.db";
    
    /**
     * Legacy datasource configuration prefix.
     *
     * @deprecated use {@link #CANONICAL_PREFIX} instead. Planned for removal in Nacos 4.0.0.
     */
    @Deprecated
    static final String LEGACY_PREFIX = "db";
    
    private static final String POOL_CONFIG_SUFFIX = "pool.config";
    
    private static final String QUERY_TIMEOUT_ITEM = "query-timeout";
    
    /**
     * Legacy datasource query timeout JVM property.
     *
     * @deprecated use {@code nacos.plugin.datasource.db.query-timeout} instead. Planned for
     *     removal in Nacos 4.0.0.
     */
    @Deprecated
    private static final String LEGACY_QUERY_TIMEOUT_PROPERTY = "QUERYTIMEOUT";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasourceConfigResolver.class);
    
    private final Environment environment;
    
    private boolean legacyWarningLogged;
    
    DatasourceConfigResolver(Environment environment) {
        this.environment = environment;
    }
    
    <T> T resolve(String itemKey, Class<T> targetType) {
        T canonicalValue = getProperty(CANONICAL_PREFIX, itemKey, targetType);
        if (canonicalValue != null) {
            return canonicalValue;
        }
        T legacyValue = getProperty(LEGACY_PREFIX, itemKey, targetType);
        if (legacyValue != null) {
            warnLegacy(LEGACY_PREFIX + "." + itemKey);
        }
        return legacyValue;
    }
    
    String resolveIndexed(String itemKey, int index, boolean sharedFallback) {
        String value = resolveIndexedFromPrefix(CANONICAL_PREFIX, itemKey, index,
            sharedFallback);
        if (value != null) {
            return value;
        }
        value = resolveIndexedFromPrefix(LEGACY_PREFIX, itemKey, index, sharedFallback);
        if (value != null) {
            warnLegacy(LEGACY_PREFIX + "." + itemKey);
        }
        return value;
    }
    
    void bindPoolConfig(HikariDataSource dataSource) {
        Binder binder = Binder.get(environment);
        Bindable<HikariDataSource> target = Bindable.ofInstance(dataSource);
        if (binder.bind(LEGACY_PREFIX + "." + POOL_CONFIG_SUFFIX, target).isBound()) {
            warnLegacy(LEGACY_PREFIX + "." + POOL_CONFIG_SUFFIX + ".*");
        }
        binder.bind(CANONICAL_PREFIX + "." + POOL_CONFIG_SUFFIX, target);
    }
    
    int resolveQueryTimeout(int defaultValue) {
        String canonicalValue = getProperty(CANONICAL_PREFIX, QUERY_TIMEOUT_ITEM, String.class);
        if (canonicalValue != null) {
            return ConvertUtils.toInt(canonicalValue, defaultValue);
        }
        String legacyValue = System.getProperty(LEGACY_QUERY_TIMEOUT_PROPERTY);
        if (legacyValue != null) {
            warnLegacy(LEGACY_QUERY_TIMEOUT_PROPERTY);
        }
        return ConvertUtils.toInt(legacyValue, defaultValue);
    }
    
    private String resolveIndexedFromPrefix(String prefix, String itemKey, int index,
        boolean sharedFallback) {
        String value = getIndexedProperty(prefix, itemKey, index);
        if (value != null) {
            return value;
        }
        if (sharedFallback || index == 0) {
            value = getProperty(prefix, itemKey, String.class);
        }
        if (value == null && sharedFallback && index > 0) {
            value = getIndexedProperty(prefix, itemKey, 0);
        }
        return value;
    }
    
    private String getIndexedProperty(String prefix, String itemKey, int index) {
        String value = getProperty(prefix, itemKey + "." + index, String.class);
        if (value == null) {
            value = getProperty(prefix, itemKey + "[" + index + "]", String.class);
        }
        return value;
    }
    
    private <T> T getProperty(String prefix, String itemKey, Class<T> targetType) {
        return environment.getProperty(prefix + "." + itemKey, targetType);
    }
    
    private void warnLegacy(String legacyKey) {
        if (legacyWarningLogged) {
            return;
        }
        legacyWarningLogged = true;
        LOGGER
            .warn("[DatasourceConfigResolver] Legacy datasource configuration '{}' is configured; "
                + "prefer '{}.*'.", legacyKey, CANONICAL_PREFIX);
    }
}
