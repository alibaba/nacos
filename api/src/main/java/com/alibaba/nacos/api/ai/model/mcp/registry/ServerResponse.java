/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * ServerResponse.
 *
 * @author xinluo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerResponse {

    private McpRegistryServerDetail server;

    @JsonProperty("_meta")
    private Meta meta;

    public McpRegistryServerDetail getServer() {
        return server;
    }

    public void setServer(McpRegistryServerDetail server) {
        this.server = server;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    /**
     * _meta wrapper allowing extension namespaces.
     *
     * @author xinluo
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Meta {

        @JsonProperty("io.modelcontextprotocol.registry/official")
        private OfficialMeta official;

        @JsonAnySetter
        private Map<String, Object> additionalMetadata = new HashMap<>();

        public OfficialMeta getOfficial() {
            return official;
        }

        public void setOfficial(OfficialMeta official) {
            this.official = official;
        }

        @JsonAnyGetter
        public Map<String, Object> getAdditionalMetadata() {
            return additionalMetadata;
        }
    }
}
