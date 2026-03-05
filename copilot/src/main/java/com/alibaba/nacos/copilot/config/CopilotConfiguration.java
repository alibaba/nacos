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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.copilot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Copilot auto-configuration entry point, loaded via {@code AutoConfiguration.imports}.
 *
 * <p>The {@link ComponentScan} ensures that all copilot components are properly registered as Spring beans
 * in all deployment modes, including standalone console mode where the default package scan does not cover
 * the copilot package.
 *
 * <p>This configuration is guarded by two conditions:
 * <ul>
 *     <li>{@code nacos.copilot.enabled} must not be {@code false} (default {@code true})</li>
 *     <li>Deployment type must not be {@code server}</li>
 * </ul>
 *
 * @author nacos
 */
@Configuration
@ConditionalOnProperty(name = "nacos.copilot.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'${nacos.deployment.type:merged}' != 'server'")
@ComponentScan(basePackages = "com.alibaba.nacos.copilot")
public class CopilotConfiguration {
}
