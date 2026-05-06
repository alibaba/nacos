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

package com.alibaba.nacos.ai.form.mcp.admin;

import java.io.Serial;

/**
 * Mcp server update form.
 *
 * @author xinluo
 */
public class McpUpdateForm extends McpDetailForm {
    
    @Serial
    private static final long serialVersionUID = 4144251088520249913L;
    
    private boolean latest = true;

    private boolean overrideExisting = false;
    
    public Boolean getLatest() {
        return latest;
    }
    
    public void setLatest(Boolean publish) {
        this.latest = publish;
    }

    public boolean isOverrideExisting() {
        return overrideExisting;
    }

    public void setOverrideExisting(boolean overrideExisting) {
        this.overrideExisting = overrideExisting;
    }
}
