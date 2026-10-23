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

import com.alibaba.nacos.NacosServerBasicApplication;
import com.alibaba.nacos.NacosServerWebApplication;
import com.alibaba.nacos.airegistry.NacosAiRegistry;
import com.alibaba.nacos.console.NacosConsole;
import com.alibaba.nacos.core.listener.startup.NacosStartUpManager;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.DeploymentType;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.aot.AotDetector;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.support.RegistrationPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class NacosBootstrapTest {
    
    private String deployment;
    
    @BeforeEach
    void rememberDeployment() {
        deployment = System.getProperty(Constants.NACOS_DEPLOYMENT_TYPE);
    }
    
    @AfterEach
    void restoreDeployment() {
        if (deployment == null) {
            System.clearProperty(Constants.NACOS_DEPLOYMENT_TYPE);
        } else {
            System.setProperty(Constants.NACOS_DEPLOYMENT_TYPE, deployment);
        }
    }
    
    @ParameterizedTest
    @CsvSource({"default,none,false", "merged,mcp,true", "server,none,false",
        "server,mcp,false", "server,skill,false", "server,ard,false", "console,none,false"})
    void shouldPreserveJvmContextSequenceAndParents(String type, String protocol, boolean jmx) {
        if ("default".equals(type)) {
            System.clearProperty(Constants.NACOS_DEPLOYMENT_TYPE);
        } else {
            System.setProperty(Constants.NACOS_DEPLOYMENT_TYPE, type);
        }
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
            "spring.jmx.enabled", jmx,
            "nacos.ai.mcp.registry.enabled", "mcp".equals(protocol),
            "nacos.ai.skill.registry.enabled", "skill".equals(protocol),
            "nacos.ai.ard.enabled", "ard".equals(protocol))));
        ConfigurableApplicationContext core = mock(ConfigurableApplicationContext.class);
        when(core.getEnvironment()).thenReturn(environment);
        MBeanExporter exporter = mock(MBeanExporter.class);
        when(core.getBean(MBeanExporter.class)).thenReturn(exporter);
        List<Class<?>> sources = new ArrayList<>();
        String[] arguments = {"--test.option=value"};
        List<String> phases = new ArrayList<>();
        try (MockedStatic<EnvUtil> env = mockStatic(EnvUtil.class);
            MockedStatic<NacosStartUpManager> startup = mockStatic(NacosStartUpManager.class);
            MockedStatic<AotDetector> detector = mockStatic(AotDetector.class);
            MockedConstruction<SpringApplicationBuilder> builders = mockConstruction(
                SpringApplicationBuilder.class, withSettings().defaultAnswer(RETURNS_SELF),
                (builder, context) -> {
                    sources.add(((Class<?>[]) context.arguments().get(0))[0]);
                    when(builder.run(arguments)).thenReturn(core);
                })) {
            startup.when(() -> NacosStartUpManager.start(any(String.class))).thenAnswer(call -> {
                phases.add(call.getArgument(0));
                return null;
            });
            NacosBootstrap.main(arguments);
            boolean consoleOnly = "console".equals(type);
            List<Class<?>> expectedSources = new ArrayList<>();
            List<String> expectedPhases = new ArrayList<>();
            if (!consoleOnly) {
                expectedSources.add(NacosServerBasicApplication.class);
                expectedSources.add(NacosServerWebApplication.class);
                expectedPhases.add("core");
                expectedPhases.add("web");
            }
            if (!"server".equals(type)) {
                expectedSources.add(NacosConsole.class);
                expectedPhases.add("console");
            }
            if (!consoleOnly && !"none".equals(protocol)) {
                expectedSources.add(NacosAiRegistry.class);
                expectedPhases.add("ai-registry");
            }
            assertEquals(expectedSources, sources);
            assertEquals(expectedPhases, phases);
            env.verify(() -> EnvUtil.setDeploymentType(
                DeploymentType.getType("default".equals(type) ? "merged" : type)));
            for (int i = 0; i < builders.constructed().size(); i++) {
                SpringApplicationBuilder builder = builders.constructed().get(i);
                verify(builder).run(arguments);
                if (i == 0) {
                    verify(builder, never()).parent(any(ConfigurableApplicationContext.class));
                    if (!consoleOnly) {
                        verify(builder).web(WebApplicationType.NONE);
                    }
                } else {
                    verify(builder).parent(core);
                }
            }
            if (jmx && !consoleOnly) {
                verify(exporter).setRegistrationPolicy(RegistrationPolicy.IGNORE_EXISTING);
            } else {
                verify(core, never()).getBean(MBeanExporter.class);
            }
        }
    }
    
    @Test
    void shouldRejectUnknownDeploymentBeforeCreatingContexts() {
        System.setProperty(Constants.NACOS_DEPLOYMENT_TYPE, "unknown");
        try (MockedStatic<EnvUtil> env = mockStatic(EnvUtil.class);
            MockedConstruction<SpringApplicationBuilder> builders =
                mockConstruction(SpringApplicationBuilder.class)) {
            assertThrows(IllegalArgumentException.class, () -> NacosBootstrap.main(new String[0]));
            assertEquals(0, builders.constructed().size());
            env.verify(() -> EnvUtil.setDeploymentType(DeploymentType.ILLEGAL));
        }
    }
}
