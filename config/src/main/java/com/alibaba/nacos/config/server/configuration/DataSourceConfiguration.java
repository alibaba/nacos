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

package com.alibaba.nacos.config.server.configuration;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.core.utils.SystemUtils.NACOS_HOME;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@DependsOn(value = "serverNodeManager")
@Configuration
public class DataSourceConfiguration {

    private static final String JDBC_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";
    private static final String DERBY_BASE_DIR = "data" + File.separator + "derby-data";
    private static final String USER_NAME = "nacos";
    private static final String PASSWORD = "nacos";

    @ConditionalOnProperty(value = "nacos.standalone", havingValue = "true")
    @Bean
    public BasicDataSource standAlone() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(JDBC_DRIVER_NAME);
        ds.setUrl("jdbc:derby:" + NACOS_HOME + File.separator + DERBY_BASE_DIR + ";create=true");
        ds.setUsername(USER_NAME);
        ds.setPassword(PASSWORD);
        ds.setInitialSize(20);
        ds.setMaxActive(30);
        ds.setMaxIdle(50);
        ds.setMaxWait(10000L);
        ds.setPoolPreparedStatements(true);
        ds.setTimeBetweenEvictionRunsMillis(TimeUnit.MINUTES
                .toMillis(10L));
        ds.setTestWhileIdle(true);
        return ds;
    }

    @Conditional(ClusterV2Condition.class)
    @Bean
    public ClusterDataSourceV2 clusterV2() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(JDBC_DRIVER_NAME);
        ds.setUrl("jdbc:derby:" + NACOS_HOME + File.separator + DERBY_BASE_DIR + ";create=true");
        ds.setUsername(USER_NAME);
        ds.setPassword(PASSWORD);
        ds.setInitialSize(20);
        ds.setMaxActive(30);
        ds.setMaxIdle(50);
        ds.setMaxWait(10000L);
        ds.setPoolPreparedStatements(true);
        ds.setTimeBetweenEvictionRunsMillis(TimeUnit.MINUTES
                .toMillis(10L));
        ds.setTestWhileIdle(true);
        return new ClusterDataSourceV2(ds);
    }

    private static class ClusterV2Condition implements Condition {

        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata metadata) {
            final String isAlone = ctx.getEnvironment().getProperty("nacos.standalone", "false");
            final String isV2 = ctx.getEnvironment().getProperty("nacos.config.store.type");
            return StringUtils.equalsIgnoreCase(isAlone, "false") && StringUtils.equalsIgnoreCase(isV2, "inner");
        }
    }

}
