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

package com.alibaba.nacos.plugin.auth.impl.oracle.configuration;

import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import com.alibaba.nacos.sys.env.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the activation contract of the Oracle auth plugin: the three primary persist services
 * are registered only for datasource dialect {@code oracle} on an external-storage server or
 * merged deployment; every other combination backs off completely, so non-Oracle deployments
 * behave identically to the default auth plugin.
 */
class OracleAuthPluginAutoConfigTest {
    
    private static final String[] PERSIST_SERVICE_BEAN_NAMES = {"oraclePermissionPersistService",
        "oracleRolePersistService", "oracleUserPersistService"};
    
    private boolean embeddedStorageCache;
    
    @BeforeEach
    void setUp() {
        embeddedStorageCache = DatasourceConfiguration.isEmbeddedStorage();
        DatasourceConfiguration.setEmbeddedStorage(false);
    }
    
    @AfterEach
    void tearDown() {
        DatasourceConfiguration.setEmbeddedStorage(embeddedStorageCache);
    }
    
    @Test
    void testOracleDialectRegistersPrimaryPersistServices() {
        try (AnnotationConfigApplicationContext context = contextWithDialect("oracle")) {
            assertEquals(1, context.getBeanNamesForType(PermissionPersistService.class).length);
            assertEquals(1, context.getBeanNamesForType(RolePersistService.class).length);
            assertEquals(1, context.getBeanNamesForType(UserPersistService.class).length);
            assertPrimary(context, "oraclePermissionPersistService");
            assertPrimary(context, "oracleRolePersistService");
            assertPrimary(context, "oracleUserPersistService");
        }
    }
    
    @Test
    void testNonOracleDialectBacksOff() {
        try (AnnotationConfigApplicationContext context = contextWithDialect("mysql")) {
            assertNoOracleBeans(context);
        }
    }
    
    @Test
    void testMissingDialectPropertyBacksOff() {
        try (
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(OracleAuthPluginAutoConfig.class);
            context.refresh();
            assertNoOracleBeans(context);
        }
    }
    
    @Test
    void testEmbeddedStorageBacksOffEvenWithOracleDialect() {
        DatasourceConfiguration.setEmbeddedStorage(true);
        try (AnnotationConfigApplicationContext context = contextWithDialect("oracle")) {
            assertNoOracleBeans(context);
        }
    }
    
    @Test
    void testConsoleDeploymentBacksOffEvenWithOracleDialect() {
        try (
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            Map<String, Object> properties = Map.of(
                PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY,
                "oracle", Constants.NACOS_DEPLOYMENT_TYPE, Constants.NACOS_DEPLOYMENT_TYPE_CONSOLE);
            context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("oracle-auth-test", properties));
            context.register(OracleAuthPluginAutoConfig.class);
            context.refresh();
            assertNoOracleBeans(context);
        }
    }
    
    private AnnotationConfigApplicationContext contextWithDialect(String dialect) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        // The property source must be in place before register(): AnnotatedBeanDefinitionReader
        // evaluates conditions at registration time, not only at refresh.
        context.getEnvironment()
            .getPropertySources()
            .addFirst(new MapPropertySource("oracle-auth-test",
                Collections.singletonMap(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY,
                    dialect)));
        context.register(OracleAuthPluginAutoConfig.class);
        // The persist services wire DynamicDataSource in @PostConstruct, which requires the
        // Nacos server environment; registration and primary flags are assertable without
        // instantiating them.
        context.addBeanFactoryPostProcessor(beanFactory -> {
            for (String beanName : PERSIST_SERVICE_BEAN_NAMES) {
                if (beanFactory.containsBeanDefinition(beanName)) {
                    beanFactory.getBeanDefinition(beanName).setLazyInit(true);
                }
            }
        });
        context.refresh();
        return context;
    }
    
    private void assertPrimary(AnnotationConfigApplicationContext context, String beanName) {
        assertTrue(context.getBeanFactory().getBeanDefinition(beanName).isPrimary(),
            beanName + " must be a primary bean");
    }
    
    private void assertNoOracleBeans(AnnotationConfigApplicationContext context) {
        assertEquals(0, context.getBeanNamesForType(PermissionPersistService.class).length);
        assertEquals(0, context.getBeanNamesForType(RolePersistService.class).length);
        assertEquals(0, context.getBeanNamesForType(UserPersistService.class).length);
        assertFalse(context.containsBean("oraclePermissionPersistService"));
        assertFalse(context.containsBean("oracleRolePersistService"));
        assertFalse(context.containsBean("oracleUserPersistService"));
    }
}
