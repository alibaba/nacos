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

import org.springframework.aot.AotDetector;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Properties;

/**
 * Explicit per-context initializer selection for the experimental multi-context AOT build.
 */
final class NacosAotInitializer {
    
    static final String INDEX = "META-INF/nacos/aot/initializers.properties";
    
    private NacosAotInitializer() {
    }
    
    static SpringApplicationBuilder configure(SpringApplicationBuilder builder, String phase) {
        if (!AotDetector.useGeneratedArtifacts()) {
            return builder;
        }
        Properties index;
        try {
            index = PropertiesLoaderUtils.loadProperties(new ClassPathResource(INDEX));
        } catch (IOException e) {
            throw new IllegalStateException("Missing Nacos AOT phase index: " + INDEX
                + ". Generate the matching AOT artifacts before enabling spring.aot.enabled.", e);
        }
        String initializer = index.getProperty(phase);
        if (!StringUtils.hasText(initializer)) {
            throw new IllegalStateException("Missing Nacos AOT initializer for phase: " + phase);
        }
        AotApplicationContextInitializer<ConfigurableApplicationContext> generated =
            AotApplicationContextInitializer.forInitializerClasses(initializer);
        // Nacos custom environment plugins run during contextLoaded. Validate the final
        // environment on ApplicationPreparedEvent, after those plugins and before refresh.
        builder.listeners((ApplicationListener<ApplicationPreparedEvent>) event -> {
            if (event.getSpringApplication() == builder.application()) {
                NacosAotManifest.validate(index, event.getApplicationContext().getEnvironment(),
                    event.getApplicationContext().getClassLoader());
            }
        });
        return builder.initializers(generated);
    }
}
