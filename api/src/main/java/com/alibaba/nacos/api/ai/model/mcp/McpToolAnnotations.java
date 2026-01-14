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

package com.alibaba.nacos.api.ai.model.mcp;

/**
 * MCP Tool Annotations - Additional properties describing a Tool to clients.
 * 
 * <p>
 * NOTE: all properties in ToolAnnotations are <b>hints</b>.
 * They are not guaranteed to provide a faithful description of tool behavior.
 * Clients should never make tool use decisions based on ToolAnnotations
 * received from untrusted servers.
 * </p>
 *
 * @author xiweng.yy
 */
public class McpToolAnnotations {

    /**
     * A human-readable title for the tool.
     */
    private String title;

    /**
     * If true, the tool does not modify its environment. Default: false
     */
    private Boolean readOnlyHint;

    /**
     * If true, the tool may perform destructive updates to its environment.
     * If false, the tool performs only additive updates.
     * (This property is meaningful only when readOnlyHint == false)
     * Default: true
     */
    private Boolean destructiveHint;

    /**
     * If true, calling the tool repeatedly with the same arguments
     * will have no additional effect on its environment.
     * (This property is meaningful only when readOnlyHint == false)
     * Default: false
     */
    private Boolean idempotentHint;

    /**
     * If true, this tool may interact with an "open world" of external entities.
     * If false, the tool's domain of interaction is closed.
     * Default: true
     */
    private Boolean openWorldHint;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getReadOnlyHint() {
        return readOnlyHint;
    }

    public void setReadOnlyHint(Boolean readOnlyHint) {
        this.readOnlyHint = readOnlyHint;
    }

    public Boolean getDestructiveHint() {
        return destructiveHint;
    }

    public void setDestructiveHint(Boolean destructiveHint) {
        this.destructiveHint = destructiveHint;
    }

    public Boolean getIdempotentHint() {
        return idempotentHint;
    }

    public void setIdempotentHint(Boolean idempotentHint) {
        this.idempotentHint = idempotentHint;
    }

    public Boolean getOpenWorldHint() {
        return openWorldHint;
    }

    public void setOpenWorldHint(Boolean openWorldHint) {
        this.openWorldHint = openWorldHint;
    }
}
