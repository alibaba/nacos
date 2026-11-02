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

package com.alibaba.nacos.core.config;

import com.alibaba.nacos.common.json.AgentCardJackson3Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Register Card compatibility before the parent context creates its shared HTTP mapper.
 *
 * @author nacos
 */
@Configuration
public class AgentCardJacksonConfiguration {
    
    @Bean
    public AgentCardJackson3Module agentCardJacksonModule() {
        return new AgentCardJackson3Module();
    }
}
