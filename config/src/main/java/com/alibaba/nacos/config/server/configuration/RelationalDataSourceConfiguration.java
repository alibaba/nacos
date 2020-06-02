package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.config.server.configuration.datasource.DynamicDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@ConditionalOnExpression("T(com.alibaba.nacos.config.server.configuration.datasource.DataSourceType).MYSQL.matches('${nacos.datasource.type}')" +
    " || T(com.alibaba.nacos.config.server.configuration.datasource.DataSourceType).ORACLE.matches('${nacos.datasource.type}')" +
    " || T(com.alibaba.nacos.config.server.configuration.datasource.DataSourceType).POSTGRESQL.matches('${nacos.datasource.type}')")
@Configuration
public class RelationalDataSourceConfiguration {

    @Bean("db-master-properties")
    @ConfigurationProperties("nacos.datasource.relational.master")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("db-slave-properties")
    @ConfigurationProperties("nacos.datasource.relational.slave")
    public DataSourceProperties slaveDataSourceProperties() {
        return new DataSourceProperties();
    }


    @Bean("nacos-db-master")
    @ConfigurationProperties(prefix = "nacos.datasource.relational.master.hikari")
    HikariDataSource masterDataSource(NacosMultipleDataSourceProperties nacosMultipleDataSourceProperties) {
        DataSourceProperties master =
                nacosMultipleDataSourceProperties.getRelational().getMaster();
        HikariDataSource dataSource = createDataSource(master);
        if (StringUtils.hasText(master.getName())) {
            dataSource.setPoolName(master.getName());
        } else {
            dataSource.setPoolName("nacos-db-master");
        }
        return dataSource;
    }

    @Bean("nacos-db-salve")
    @ConfigurationProperties(prefix = "nacos.datasource.relational.slave.hikari")
    HikariDataSource salveDataSource(NacosMultipleDataSourceProperties nacosMultipleDataSourceProperties) {
        boolean slaveEnable = nacosMultipleDataSourceProperties.getRelational().isSlaveEnable();
        DataSourceProperties master = nacosMultipleDataSourceProperties.getRelational().getMaster();
        DataSourceProperties slave = nacosMultipleDataSourceProperties.getRelational().getSlave();
        DataSourceProperties properties = slaveEnable ? slave : master;
        HikariDataSource dataSource = createDataSource(properties);
        if (StringUtils.hasText(properties.getName())) {
            dataSource.setPoolName(properties.getName());
        } else {
            dataSource.setPoolName("nacos-db-salve");
        }
        return dataSource;
    }


    @Primary
    @Bean
    public DataSource dataSource(@Autowired @Qualifier("nacos-db-master") HikariDataSource master, @Qualifier(
            "nacos-db-salve") HikariDataSource slave) {
        return new DynamicDataSource(master, slave);
    }

    protected static HikariDataSource createDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

}
