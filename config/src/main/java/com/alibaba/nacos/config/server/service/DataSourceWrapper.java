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

import javax.sql.DataSource;

/**
 * @author codewaltz1994
 */
public  class DataSourceWrapper{
    private String driverClassName;
    private String url;
    private String username;
    private String password;

    private DataSource ds;

    private DynamicDataSourceConnectionPool dynamicDataSourceConnectionPool;

    public DataSourceWrapper(DynamicDataSourceConnectionPool dynamicDataSourceConnectionPool){
        this.dynamicDataSourceConnectionPool = dynamicDataSourceConnectionPool;
    }
    public DataSource getDataSource(){
        DataSource tmp = dynamicDataSourceConnectionPool.getDataSource(getDriverClassName(), getUrl(), getUsername(), getPassword());
        ds = tmp;
        return ds;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public DataSource getDs() {
        return ds;
    }

    public void setDs(DataSource ds) {
        this.ds = ds;
    }
}
