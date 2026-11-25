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

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.persistence.H2TestUtils;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDataSourceServiceH2Test {
    
    private static final String PROBE_TABLE =
        "CREATE TABLE IF NOT EXISTS h2_probe (id bigint NOT NULL PRIMARY KEY);\n";
    
    private static final String BROKEN_INDEX =
        "CREATE UNIQUE INDEX uk_broken_probe ON h2_probe (no_such_column);\n";
    
    private static final String VALID_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS uk_probe ON h2_probe (id);\n";
    
    @TempDir
    Path tempDir;
    
    private LocalDataSourceServiceImpl service;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setNacosHomePath(tempDir.toString());
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY,
            PersistenceConstant.H2);
        EnvUtil.setEnvironment(environment);
        EnvUtil.setIsStandalone(true);
        DatasourceConfiguration.setUseExternalDb(false);
        DatasourceConfiguration.setEmbeddedStorage(true);
    }
    
    @AfterEach
    void tearDown() {
        closeDataSource(service);
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testInitUsesH2EngineAndDataDirectory() throws Exception {
        service = new LocalDataSourceServiceImpl();
        service.init();
        
        assertEquals(PersistenceConstant.H2, service.getDataSourceType());
        assertTrue(Files.exists(tempDir.resolve("data").resolve(PersistenceConstant.H2_BASE_DIR)
            .resolve("nacos.mv.db")));
        String jdbcUrl = ((HikariDataSource) service.getDatasource()).getJdbcUrl();
        assertTrue(jdbcUrl.startsWith("jdbc:h2:file:"), jdbcUrl);
        assertTrue(jdbcUrl.endsWith(";WRITE_DELAY=0;TRACE_LEVEL_FILE=0"), jdbcUrl);
    }
    
    @Test
    void testCommittedRowSurvivesReopenOfSameDataDirectory() throws Exception {
        LocalDataSourceServiceImpl first = new LocalDataSourceServiceImpl();
        first.init();
        first.getJdbcTemplate().update("INSERT INTO h2_probe (payload) VALUES (?)", "committed");
        closeDataSource(first);
        
        service = new LocalDataSourceServiceImpl();
        service.init();
        
        String payload =
            service.getJdbcTemplate().queryForObject("SELECT payload FROM h2_probe", String.class);
        assertEquals("committed", payload);
    }
    
    @Test
    void testDerbyOnlyRecoveryOperationsAreRejectedForH2() throws Exception {
        service = new LocalDataSourceServiceImpl();
        service.init();
        service.getJdbcTemplate().update("INSERT INTO h2_probe (payload) VALUES (?)", "kept");
        
        assertThrows(IllegalStateException.class, () -> service.cleanAndReopenDerby());
        assertThrows(IllegalStateException.class,
            () -> service.restoreDerby("jdbc:h2:file:unused", () -> null));
        
        Integer rows = service.getJdbcTemplate().queryForObject("SELECT count(*) FROM h2_probe",
            Integer.class);
        assertEquals(1, rows);
    }
    
    @Test
    void testFailingSchemaStatementStopsInitialization() throws Exception {
        H2TestUtils.writeSchema(tempDir, PROBE_TABLE + BROKEN_INDEX);
        
        service = new LocalDataSourceServiceImpl();
        NacosRuntimeException error =
            assertThrows(NacosRuntimeException.class, () -> service.init());
        
        assertTrue(error.getCause().getMessage().contains("uk_broken_probe"),
            error.getCause().getMessage());
    }
    
    @Test
    void testFailedInitializationClosesItsPoolAndTheServiceCanInitializeAgain() throws Exception {
        H2TestUtils.writeSchema(tempDir, PROBE_TABLE + BROKEN_INDEX);
        service = new LocalDataSourceServiceImpl();
        
        assertThrows(NacosRuntimeException.class, () -> service.init());
        assertEquals(1, H2TestUtils.countOpenSessions(tempDir),
            "the pool of the failed initialization must be closed");
        
        // Another attempt with the same schema fails the same way, and closes its pool as well.
        assertThrows(NacosRuntimeException.class, () -> service.init());
        assertEquals(1, H2TestUtils.countOpenSessions(tempDir),
            "the pool of the second failed initialization must be closed");
        
        // The service holds no datasource of the failed attempts, and initializes once the schema is valid.
        assertNull(service.getJdbcTemplate());
        H2TestUtils.writeSchema(tempDir, PROBE_TABLE + VALID_INDEX);
        service.init();
        assertEquals(PersistenceConstant.H2, service.getDataSourceType());
        assertEquals(0, service.getJdbcTemplate().queryForObject("SELECT count(*) FROM h2_probe",
            Integer.class));
    }
    
    private static void closeDataSource(LocalDataSourceServiceImpl dataSourceService) {
        // A service whose initialization failed has no datasource, because it closed its pool itself.
        if (null != dataSourceService && null != dataSourceService.getJdbcTemplate()) {
            ((HikariDataSource) dataSourceService.getDatasource()).close();
        }
    }
}
