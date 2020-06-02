package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.config.server.configuration.datasource.DataSourceType;
import lombok.Data;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@Component
@Data
@ConfigurationProperties(prefix = "nacos.datasource")
public class NacosMultipleDataSourceProperties {

    private DataSourceType type;
    private MongoProperties mongodb;
    private RelationalDataSource relational;

    @Data
    public static class RelationalDataSource {

        private boolean slaveEnable = true;
        private DataSourceProperties master;
        private DataSourceProperties slave;
    }

}
