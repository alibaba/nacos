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

package com.alibaba.nacos.ai.model.mcp;

import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;

import java.util.List;

/**
 * Url page result.
 * @author xinluo
 */
public class UrlPageResult {

    private List<McpServerDetailInfo> servers;

    private String nextCursor;

    public UrlPageResult(List<McpServerDetailInfo> servers, String nextCursor) {
        this.servers = servers;
        this.nextCursor = nextCursor;
    }

    public List<McpServerDetailInfo> getServers() {
        return servers;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public void setServers(List<McpServerDetailInfo> servers) {
        this.servers = servers;
    }
}
