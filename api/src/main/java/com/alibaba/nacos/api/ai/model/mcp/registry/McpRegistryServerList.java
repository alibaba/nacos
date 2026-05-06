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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * McpRegistryServerList (renamed from ServerList) aligns with registry package.
 *
 * @author xinluo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpRegistryServerList {

    private List<ServerResponse> servers;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Metadata metadata;

    public List<ServerResponse> getServers() {
        return servers;
    }

    public void setServers(List<ServerResponse> servers) {
        this.servers = servers;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Metadata {
        @JsonProperty("nextCursor")
        @JsonAlias("next_cursor")
        private String nextCursor;

        private Integer count;

        public Metadata() {
        }

        public Metadata(String nextCursor, Integer count) {
            this.nextCursor = nextCursor;
            this.count = count;
        }

        public String getNextCursor() {
            return nextCursor;
        }

        public void setNextCursor(String nextCursor) {
            this.nextCursor = nextCursor;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }
}
