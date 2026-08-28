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

package com.alibaba.nacos.airegistry.controller;

import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import com.alibaba.nacos.airegistry.service.ard.ArdArtifact;
import com.alibaba.nacos.airegistry.service.ard.ArdArtifactService;
import com.alibaba.nacos.airegistry.service.ard.ArdSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies ARD artifacts in a web context separate from the main Nacos server.
 *
 * @author nacos
 */
class ArdWebContextIsolationTest {
    
    @Test
    void skillArtifactShouldBeServedOnlyByAdaptorContext() throws Exception {
        try (AnnotationConfigServletWebServerApplicationContext mainContext =
            start(MainServerApplication.class, "/nacos");
            AnnotationConfigServletWebServerApplicationContext adaptorContext =
                start(AdaptorApplication.class, "")) {
            int mainPort = mainContext.getWebServer().getPort();
            int adaptorPort = adaptorContext.getWebServer().getPort();
            
            assertEquals(200, get(mainPort, "/nacos/v3/client/ai/skills").statusCode());
            assertEquals(404, get(adaptorPort, "/v3/client/ai/skills").statusCode());
            
            String artifactPath = "/v3/ai/ard/artifacts?namespaceId=public"
                + "&resourceType=skill&resourceName=demo&version=1.0.0";
            HttpResponse<byte[]> artifact = get(adaptorPort, artifactPath);
            assertEquals(200, artifact.statusCode());
            assertEquals(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE,
                artifact.headers().firstValue("content-type").orElseThrow());
            assertEquals("skill-zip",
                new String(artifact.body(), StandardCharsets.UTF_8));
            assertEquals(404, get(mainPort, "/nacos" + artifactPath).statusCode());
        }
    }
    
    private AnnotationConfigServletWebServerApplicationContext start(
        Class<?> application, String contextPath) {
        AnnotationConfigServletWebServerApplicationContext context =
            new AnnotationConfigServletWebServerApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource("test", Map.of("server.port", "0",
                "server.servlet.context-path", contextPath,
                "nacos.ai.ard.enabled", "true",
                "management.elastic.metrics.export.enabled", "false",
                "management.influx.metrics.export.enabled", "false")));
        context.register(application);
        context.refresh();
        return context;
    }
    
    private HttpResponse<byte[]> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
    
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
        SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class})
    static class MainServerApplication {
        
        @Bean
        MainSkillController mainSkillController() {
            return new MainSkillController();
        }
    }
    
    @RestController
    static class MainSkillController {
        
        @GetMapping("/v3/client/ai/skills")
        byte[] get() {
            return "main-server".getBytes(StandardCharsets.UTF_8);
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
        SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class})
    @Import({ArdSearchController.class, ArdExceptionHandler.class})
    static class AdaptorApplication {
        
        @Bean
        ArdSearchService ardSearchService() {
            return mock(ArdSearchService.class);
        }
        
        @Bean
        ArdArtifactService ardArtifactService() throws Exception {
            ArdArtifactService service = mock(ArdArtifactService.class);
            when(service.get(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new ArdArtifact(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE,
                    "skill-zip".getBytes(StandardCharsets.UTF_8)));
            return service;
        }
    }
}
