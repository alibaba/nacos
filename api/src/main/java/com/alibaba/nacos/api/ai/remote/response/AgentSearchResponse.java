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

package com.alibaba.nacos.api.ai.remote.response;

import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.remote.response.Response;

/**
 * gRPC response for RAD Agent Search.
 *
 * @author Nacos
 */
public class AgentSearchResponse extends Response {
    
    private Page<AgentCatalogEntry> page;
    
    public Page<AgentCatalogEntry> getPage() {
        return page;
    }
    
    public void setPage(Page<AgentCatalogEntry> page) {
        this.page = page;
    }
}
