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

package com.alibaba.nacos.bootstrap;

import com.alibaba.nacos.sys.env.DeploymentType;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.aot.AotDetector;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Verifies that AOT selection is opt-in and preserves the context hierarchy.
 */
class NacosAotInitializerTest {
    
    @Test
    void shouldLeaveOrdinaryJvmBootstrapUntouched() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(NacosBootstrap.class);
        var initializers = builder.application().getInitializers();
        var listeners = builder.application().getListeners();
        try (MockedStatic<AotDetector> detector = mockStatic(AotDetector.class);
            MockedStatic<PropertiesLoaderUtils> loader = mockStatic(PropertiesLoaderUtils.class);
            MockedStatic<EnvUtil> environment = mockStatic(EnvUtil.class)) {
            detector.when(AotDetector::useGeneratedArtifacts).thenReturn(false);
            assertSame(builder, NacosAotInitializer.configure(builder, "core"));
            assertSame(builder, NacosAotInitializer.configure(builder, "web"));
            assertSame(builder, NacosAotInitializer.configure(builder, "console"));
            assertSame(builder, NacosAotInitializer.configure(builder, "ai-registry"));
            assertEquals(initializers, builder.application().getInitializers());
            assertEquals(listeners, builder.application().getListeners());
            loader.verifyNoInteractions();
            environment.verifyNoInteractions();
        }
    }
    
    @Test
    void shouldExplainMissingAotArtifacts() {
        try (MockedStatic<AotDetector> detector = mockStatic(AotDetector.class);
            MockedStatic<PropertiesLoaderUtils> loader = mockStatic(PropertiesLoaderUtils.class)) {
            detector.when(AotDetector::useGeneratedArtifacts).thenReturn(true);
            loader.when(() -> PropertiesLoaderUtils.loadProperties(any(Resource.class)))
                .thenThrow(new IOException("missing index"));
            IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NacosAotInitializer.configure(new SpringApplicationBuilder(), "core"));
            assertTrue(error.getMessage().contains(NacosAotInitializer.INDEX));
            assertTrue(error.getMessage().contains("spring.aot.enabled"));
        }
    }
    
    @Test
    void shouldRejectMissingPhaseInsteadOfUsingAnotherInitializer() {
        Properties index = new Properties();
        index.setProperty("core", CoreInitializer.class.getName());
        try (MockedStatic<AotDetector> detector = mockStatic(AotDetector.class);
            MockedStatic<PropertiesLoaderUtils> loader = mockStatic(PropertiesLoaderUtils.class)) {
            detector.when(AotDetector::useGeneratedArtifacts).thenReturn(true);
            loader.when(() -> PropertiesLoaderUtils.loadProperties(any(Resource.class)))
                .thenReturn(index);
            IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NacosAotInitializer.configure(new SpringApplicationBuilder(), "web"));
            assertTrue(error.getMessage().contains("web"));
        }
    }
    
    @Test
    void shouldSelectDifferentInitializersAndPreserveParent() {
        Properties index = new Properties();
        index.setProperty("core", CoreInitializer.class.getName());
        index.setProperty("web", WebInitializer.class.getName());
        try (MockedStatic<AotDetector> detector = mockStatic(AotDetector.class);
            MockedStatic<PropertiesLoaderUtils> loader = mockStatic(PropertiesLoaderUtils.class);
            MockedStatic<EnvUtil> environment = mockStatic(EnvUtil.class);
            GenericApplicationContext core = new GenericApplicationContext();
            GenericApplicationContext web = new GenericApplicationContext(core)) {
            detector.when(AotDetector::useGeneratedArtifacts).thenReturn(true);
            loader.when(() -> PropertiesLoaderUtils.loadProperties(any(Resource.class)))
                .thenReturn(index);
            environment.when(EnvUtil::getDeploymentType).thenReturn(DeploymentType.SERVER);
            environment.when(EnvUtil::getStandaloneMode).thenReturn(true);
            initialize("core", core);
            initialize("web", web);
            assertEquals("core-generated", core.getDisplayName());
            assertEquals("web-generated", web.getDisplayName());
            assertSame(core, web.getParent());
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void shouldValidatePreparedEnvironmentBeforeRefreshAndIgnoreOtherApplications() {
        try (MockedStatic<AotDetector> detector = mockStatic(AotDetector.class);
            MockedStatic<PropertiesLoaderUtils> loader = mockStatic(PropertiesLoaderUtils.class);
            MockedStatic<EnvUtil> environment = mockStatic(EnvUtil.class);
            GenericApplicationContext context = new GenericApplicationContext()) {
            environment.when(EnvUtil::getDeploymentType).thenReturn(DeploymentType.SERVER);
            environment.when(EnvUtil::getStandaloneMode).thenReturn(true);
            Properties index =
                NacosAotManifest.snapshot(context.getEnvironment(), context.getClassLoader());
            index.setProperty("core", CoreInitializer.class.getName());
            detector.when(AotDetector::useGeneratedArtifacts).thenReturn(true);
            loader.when(() -> PropertiesLoaderUtils.loadProperties(any(Resource.class)))
                .thenReturn(index);
            SpringApplicationBuilder builder = new SpringApplicationBuilder();
            var original = builder.application().getListeners();
            NacosAotInitializer.configure(builder, "core");
            ApplicationListener<ApplicationPreparedEvent> listener =
                (ApplicationListener<ApplicationPreparedEvent>) builder.application().getListeners()
                    .stream().filter(item -> !original.contains(item)).findFirst().orElseThrow();
            listener.onApplicationEvent(
                new ApplicationPreparedEvent(builder.application(), new String[0], context));
            context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("custom-environment-plugin",
                    Map.of("nacos.plugin.datasource-dialect.type", "mysql")));
            listener.onApplicationEvent(new ApplicationPreparedEvent(
                new SpringApplicationBuilder().application(), new String[0], context));
            IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> listener.onApplicationEvent(
                    new ApplicationPreparedEvent(builder.application(), new String[0], context)));
            assertTrue(error.getMessage().contains("datasource"));
        }
    }
    
    @SuppressWarnings("unchecked")
    private void initialize(String phase, GenericApplicationContext context) {
        SpringApplicationBuilder builder =
            NacosAotInitializer.configure(new SpringApplicationBuilder(), phase);
        builder.application().getInitializers().stream()
            .filter(AotApplicationContextInitializer.class::isInstance)
            .map(
                initializer -> (ApplicationContextInitializer<GenericApplicationContext>) initializer)
            .forEach(initializer -> initializer.initialize(context));
    }
    
    public static class CoreInitializer
        implements ApplicationContextInitializer<GenericApplicationContext> {
        
        @Override
        public void initialize(GenericApplicationContext context) {
            context.setDisplayName("core-generated");
        }
    }
    
    public static class WebInitializer
        implements ApplicationContextInitializer<GenericApplicationContext> {
        
        @Override
        public void initialize(GenericApplicationContext context) {
            context.setDisplayName("web-generated");
        }
    }
}
