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

import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentCardJacksonConfigurationTest {
    
    @Test
    void testChildContextUsesConfiguredParentMapper() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(AgentCardJacksonConfiguration.class)
            .run(parent -> {
                JsonMapper mapper = parent.getBean(JsonMapper.class);
                new ApplicationContextRunner().withParent(parent)
                    .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                    .run(child -> {
                        assertSame(mapper, child.getBean(JsonMapper.class));
                        for (String requirements : new String[] {
                            "[{\"schemes\":{\"apiKey\":{\"list\":[]}}}]", "[{\"apiKey\":[]}]"}) {
                            String json = "{\"securityRequirements\":" + requirements + "}";
                            AgentCardDetailInfo card =
                                mapper.readValue(json, AgentCardDetailInfo.class);
                            assertEquals(mapper.readTree(json).get("securityRequirements"),
                                mapper.readTree(mapper.writeValueAsString(card))
                                    .get("securityRequirements"));
                        }
                    });
            });
    }
}
