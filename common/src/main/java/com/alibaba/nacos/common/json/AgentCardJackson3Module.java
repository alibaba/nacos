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

package com.alibaba.nacos.common.json;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.util.StdConverter;

import java.util.List;
import java.util.Map;

/**
 * AgentCard wire compatibility for Jackson 3, without annotations on API models.
 *
 * @author nacos
 */
public class AgentCardJackson3Module extends SimpleModule {
    
    private static final long serialVersionUID = 1L;
    
    public AgentCardJackson3Module() {
        super("NacosAgentCard");
        setMixInAnnotation(AgentCard.class, AgentCardMixin.class);
    }
    
    /** Apply conversion only to Card-level securityRequirements. */
    public abstract static class AgentCardMixin {
        
        /**
         * Bind the existing scope-map property to its wire representation.
         *
         * @return security requirements
         */
        @JsonSerialize(converter = RequirementsWriter.class)
        @JsonDeserialize(converter = RequirementsReader.class)
        public abstract List<Map<String, List<String>>> getSecurityRequirements();
    }
    
    /** Preserve the input representation when writing requirements. */
    public static class RequirementsWriter
        extends StdConverter<List<Map<String, List<String>>>, List<Map<String, Object>>> {
        
        @Override
        public List<Map<String, Object>> convert(List<Map<String, List<String>>> value) {
            return AgentCardSecurityRequirements.toJson(value);
        }
    }
    
    /** Accept standard A2A and historical Nacos requirements. */
    public static class RequirementsReader
        extends StdConverter<List<Map<String, Object>>, List<Map<String, List<String>>>> {
        
        @Override
        public List<Map<String, List<String>>> convert(List<Map<String, Object>> value) {
            return AgentCardSecurityRequirements.fromJson(value);
        }
    }
}
