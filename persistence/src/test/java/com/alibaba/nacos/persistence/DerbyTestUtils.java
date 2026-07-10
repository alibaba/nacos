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

package com.alibaba.nacos.persistence;

import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.datasource.ExternalDataSourceServiceImpl;
import com.alibaba.nacos.persistence.datasource.LocalDataSourceServiceImpl;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.util.List;

/**
 * Derby test utilities.
 *
 * @author Nacos
 */
public final class DerbyTestUtils {
    
    private static final long DERBY_SHUTDOWN_WAIT_MILLIS = 500L;
    
    private DerbyTestUtils() {
    }
    
    /**
     * Create isolated Derby test environment.
     *
     * @return mock environment
     */
    public static MockEnvironment createDerbyTestEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.persistence.sql.derby.limit.enabled", "false");
        environment.setProperty("db.pool.config.connection-timeout", "30000");
        environment.setProperty("db.pool.config.minimum-idle", "0");
        environment.setProperty("db.pool.config.maximum-pool-size", "1");
        return environment;
    }
    
    /**
     * Reset Derby related test state.
     *
     * @param nacosHome isolated nacos home
     */
    public static void resetDerbyState(Path nacosHome) {
        resetDynamicDataSource();
        shutdownDerby();
        deleteDerbyDirectory(nacosHome);
        DatasourceConfiguration.setEmbeddedStorage(true);
        DatasourceConfiguration.setUseExternalDb(false);
        EnvUtil.setEnvironment(null);
        EnvUtil.setNacosHomePath(null);
        EmbeddedStorageContextHolder.cleanAllContext();
    }
    
    /**
     * Reset DynamicDataSource singleton state.
     */
    public static void resetDynamicDataSource() {
        DynamicDataSource dynamicDataSource = DynamicDataSource.getInstance();
        Object localDataSourceService =
            ReflectionTestUtils.getField(dynamicDataSource, "localDataSourceService");
        closeDataSourceService(localDataSourceService);
        Object basicDataSourceService =
            ReflectionTestUtils.getField(dynamicDataSource, "basicDataSourceService");
        closeDataSourceService(basicDataSourceService);
        ReflectionTestUtils.setField(dynamicDataSource, "localDataSourceService", null);
        ReflectionTestUtils.setField(dynamicDataSource, "basicDataSourceService", null);
    }
    
    /**
     * Close LocalDataSourceServiceImpl datasource.
     *
     * @param dataSourceService local datasource service
     */
    public static void closeLocalDataSource(LocalDataSourceServiceImpl dataSourceService) {
        if (dataSourceService == null) {
            return;
        }
        try {
            closeDataSource(dataSourceService.getDatasource());
        } catch (Exception e) {
            // Ignore test cleanup exceptions.
        }
    }
    
    private static void closeDataSourceService(Object dataSourceService) {
        if (dataSourceService instanceof LocalDataSourceServiceImpl) {
            closeLocalDataSource((LocalDataSourceServiceImpl) dataSourceService);
            return;
        }
        if (dataSourceService instanceof ExternalDataSourceServiceImpl) {
            closeExternalDataSource((ExternalDataSourceServiceImpl) dataSourceService);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static void closeExternalDataSource(
        ExternalDataSourceServiceImpl dataSourceService) {
        Object dataSources = ReflectionTestUtils.getField(dataSourceService, "dataSourceList");
        if (dataSources instanceof List) {
            for (HikariDataSource each : (List<HikariDataSource>) dataSources) {
                closeDataSource(each);
            }
        }
    }
    
    private static void closeDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }
    
    private static void shutdownDerby() {
        try {
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (Exception e) {
            // Ignore shutdown exception as Derby always throws one on successful shutdown.
        }
        try {
            Thread.sleep(DERBY_SHUTDOWN_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static void deleteDerbyDirectory(Path nacosHome) {
        if (nacosHome == null) {
            return;
        }
        try {
            DiskUtils.deleteDirectory(getDerbyDirectory(nacosHome).toString());
        } catch (Exception e) {
            // Ignore test cleanup exceptions.
        }
    }
    
    private static Path getDerbyDirectory(Path nacosHome) {
        return Paths.get(nacosHome.toString(), "data", PersistenceConstant.DERBY_BASE_DIR);
    }
}
