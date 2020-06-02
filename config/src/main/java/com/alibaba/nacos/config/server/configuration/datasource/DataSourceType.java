package com.alibaba.nacos.config.server.configuration.datasource;

import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
public enum DataSourceType {
    /**
     * 内存
     */
    EMBEDDED,
    /**
     * mysql
     */
    MYSQL,
    /**
     * oracle
     */
    ORACLE,
    /**
     * postgresql
     */
    POSTGRESQL;
    private static final Map<String, DataSourceType> MAPPINGS = new HashMap<>(16);

    static {
        for (DataSourceType dataSourceType : values()) {
            MAPPINGS.put(dataSourceType.name(), dataSourceType);
        }
    }

    public boolean matches(String method) {
        return (this == resolve(method));
    }

    @Nullable
    public static DataSourceType resolve(@Nullable String dataSourceType) {

        return (dataSourceType != null ? MAPPINGS.get(dataSourceType.toUpperCase()) : null);
    }
}
