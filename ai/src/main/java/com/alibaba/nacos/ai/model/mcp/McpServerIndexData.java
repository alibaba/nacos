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

package com.alibaba.nacos.ai.model.mcp;

/**
 * McpServerIndexData.
 * 
 * @author xinluo
 */
public class McpServerIndexData {

    private String id;

    private String namespaceId;

    /**
     * Factory method for index data.
     * @param id server id
     * @param namespaceId namespaceId
     * @return index
     */
    public static McpServerIndexData newIndexData(String id, String namespaceId) {
        McpServerIndexData data = new McpServerIndexData();
        data.setNamespaceId(namespaceId);
        data.setId(id);
        return data;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
}
