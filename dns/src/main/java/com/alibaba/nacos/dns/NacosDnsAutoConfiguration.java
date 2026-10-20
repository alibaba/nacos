/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.dns;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Nacos DNS server.
 *
 * <p>{@link NacosDnsMetrics} is created with an optional {@link MeterRegistry}:
 * when actuator is not on the classpath, a no-op in-memory registry is used so
 * the DNS server can start without any monitoring dependency.
 *
 * @author Nacos
 */
@Configuration
@EnableConfigurationProperties(NacosDnsProperties.class)
public class NacosDnsAutoConfiguration {
    
    @Bean
    public NacosDnsMetrics nacosDnsMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        return new NacosDnsMetrics(registryProvider.getIfAvailable());
    }
}
