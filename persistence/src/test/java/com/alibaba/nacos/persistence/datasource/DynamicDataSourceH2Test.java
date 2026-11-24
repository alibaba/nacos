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

import com.alibaba.nacos.persistence.DerbyTestUtils;
import com.alibaba.nacos.persistence.H2TestUtils;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The embedded H2 datasource of {@link DynamicDataSource} when its initialization fails.
 */
class DynamicDataSourceH2Test {
    
    private static final String PROBE_TABLE =
        "CREATE TABLE IF NOT EXISTS h2_probe (id bigint NOT NULL PRIMARY KEY);\n";
    
    private static final String BROKEN_INDEX =
        "CREATE UNIQUE INDEX uk_broken_probe ON h2_probe (no_such_column);\n";
    
    private static final String VALID_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS uk_probe ON h2_probe (id);\n";
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        DerbyTestUtils.resetDynamicDataSource();
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
        DerbyTestUtils.resetDynamicDataSource();
        EnvUtil.setEnvironment(null);
        EnvUtil.setNacosHomePath(null);
    }
    
    @Test
    void testFailedInitializationIsNotCachedSoEveryCallFailsUntilTheSchemaIsValid()
        throws Exception {
        H2TestUtils.writeSchema(tempDir, PROBE_TABLE + BROKEN_INDEX);
        DynamicDataSource dynamicDataSource = DynamicDataSource.getInstance();
        
        // A caller that catches the error and asks again must not get a half-initialized datasource.
        RuntimeException first =
            assertThrows(RuntimeException.class, dynamicDataSource::getDataSource);
        RuntimeException second =
            assertThrows(RuntimeException.class, dynamicDataSource::getDataSource);
        
        assertTrue(first.getCause().getCause().getMessage().contains("uk_broken_probe"),
            first.getCause().getCause().getMessage());
        assertTrue(second.getCause().getCause().getMessage().contains("uk_broken_probe"),
            second.getCause().getCause().getMessage());
        assertNull(ReflectionTestUtils.getField(dynamicDataSource, "localDataSourceService"));
        assertEquals(1, H2TestUtils.countOpenSessions(tempDir),
            "the pools of the failed initializations must be closed");
        
        // The next call initializes again, and the datasource is cached from the moment it initialized.
        H2TestUtils.writeSchema(tempDir, PROBE_TABLE + VALID_INDEX);
        DataSourceService recovered = dynamicDataSource.getDataSource();
        assertInstanceOf(LocalDataSourceServiceImpl.class, recovered);
        assertEquals(PersistenceConstant.H2, recovered.getDataSourceType());
        assertSame(recovered, dynamicDataSource.getDataSource());
    }
}
