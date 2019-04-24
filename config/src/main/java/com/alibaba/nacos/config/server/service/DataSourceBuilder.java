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
package com.alibaba.nacos.config.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyNameAliases;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.util.ClassUtils;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;


/**
 *  this is a class for dynamic DataSource connection pool. Its implementation reference is
 *  org.springframework.boot.jdbc.DataSourceBuilder. org.springframework.boot.jdbc.DataSourceBuilder is a final
 *  class and inconvenient to use, so this class's idea is based on this org.springframework.boot.jdbc.DataSourceBuilder class.
 *  make a little changes.
 *
 * @author codewaltz1994
 */

public  class DataSourceBuilder<T extends DataSource> {
    private static final String[] DATA_SOURCE_TYPE_NAMES = new String[]{"com.alibaba.druid.pool.DruidDataSource","com.zaxxer.hikari.HikariDataSource", "org.apache.tomcat.jdbc.pool.DataSource", "org.apache.commons.dbcp2.BasicDataSource","org.apache.commons.dbcp.BasicDataSource"};

    private static final String DRIVER_CLASS_NAME = "driverClassName";
    private static final String DEFAULT_DATA_SOURCE_TYPE_NAME = "com.zaxxer.hikari.HikariDataSource";
    private Class<? extends DataSource> type;
    private ClassLoader classLoader;
    private Map<String, String> properties = new HashMap();


    public static DataSourceBuilder<?> create() {
        return new DataSourceBuilder((ClassLoader)null);
    }

    public static DataSourceBuilder<?> create(ClassLoader classLoader) {
        return new DataSourceBuilder(classLoader);
    }

    private DataSourceBuilder(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public T build() {
        Class<? extends DataSource> type = this.getType();
        DataSource result = (DataSource) BeanUtils.instantiateClass(type);
        this.maybeGetDriverClassName();
        this.bind(result);
        return (T) result;
    }

    private void maybeGetDriverClassName() {
        if (!this.properties.containsKey(DRIVER_CLASS_NAME)
            && this.properties.containsKey("url")) {
            String url = (String) this.properties.get("url");
            String driverClass = DatabaseDriver.fromJdbcUrl(url).getDriverClassName();
            this.properties.put(DRIVER_CLASS_NAME, driverClass);
        }

    }

    private void bind(DataSource result) {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(
            this.properties);
        ConfigurationPropertyNameAliases aliases = new ConfigurationPropertyNameAliases();
        aliases.addAliases("url", new String[] { "jdbc-url" });
        aliases.addAliases("username", new String[] { "user" });
        Binder binder = new Binder(
            new ConfigurationPropertySource[] { source.withAliases(aliases) });
        binder.bind(ConfigurationPropertyName.EMPTY, Bindable.ofInstance(result));
    }

    public <D extends DataSource> DataSourceBuilder<D> type(Class<D> type) {
        this.type = type;
        return (DataSourceBuilder<D>) this;
    }

    public DataSourceBuilder<T> url(String url) {
        this.properties.put("url", url);
        return this;
    }

    public DataSourceBuilder<T> driverClassName(String driverClassName) {
        this.properties.put("driverClassName", driverClassName);
        return this;
    }

    public DataSourceBuilder<T> username(String username) {
        this.properties.put("username", username);
        return this;
    }

    public DataSourceBuilder<T> password(String password) {
        this.properties.put("password", password);
        return this;
    }

    public static Class<? extends DataSource> findType(ClassLoader classLoader) {

        String[] var1 = DATA_SOURCE_TYPE_NAMES;
        int var2 = var1.length;
        int var3 = 0;

        while (var3 < var2) {
            String name = var1[var3];

            try {
                if(BasicDataSourceServiceImpl.DATASOURCE_CONNECTION_POOL_TYPE.equals(name)) {
                    return (Class<? extends DataSource>) ClassUtils.forName(name,
                        classLoader);
                }
            }
            catch (Exception var6) {

            }
            ++var3;
        }

        //don't have 'db.pool.type' peoperty use hikariCP for default
        try {
            return (Class<? extends DataSource>) ClassUtils.forName(DEFAULT_DATA_SOURCE_TYPE_NAME,classLoader);
        }catch (Exception var6) {
            return null;
        }

    }

    private Class<? extends DataSource> getType() {
        Class<? extends DataSource> type = this.type != null ? this.type
            : findType(this.classLoader);
        if (type != null) {
            return type;
        }
        else {
            throw new IllegalStateException("No supported DataSource type found");
        }
    }
}
