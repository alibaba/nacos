package com.alibaba.nacos.config.server.service;

import javax.sql.DataSource;


public  class DataSourceWrapper{
    private String driverClassName;
    private String url;
    private String username;
    private String password;

    private DataSource ds;

    private DynamicDataSourceCP dynamicDataSourceCP;

    public DataSourceWrapper(DynamicDataSourceCP dynamicDataSourceCP){
        this.dynamicDataSourceCP = dynamicDataSourceCP;
    }
    public DataSource getDataSource(){
        DataSource tmp =dynamicDataSourceCP.getDataSource(getDriverClassName(), getUrl(), getUsername(), getPassword());
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
