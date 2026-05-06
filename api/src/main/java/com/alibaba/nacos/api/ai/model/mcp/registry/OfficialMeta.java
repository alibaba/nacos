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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Official metadata inside _meta.
 *
 * @author xinluo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OfficialMeta {

    private String publishedAt;

    private String updatedAt;

    private Boolean isLatest;

    private String status;

    /**
     * Get published at timestamp.
     *
     * @return published at
     */
    public String getPublishedAt() {
        return publishedAt;
    }

    /**
     * Set published at timestamp.
     *
     * @param publishedAt published at
     */
    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    /**
     * Get updated at timestamp.
     *
     * @return updated at
     */
    public String getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Set updated at timestamp.
     *
     * @param updatedAt updated at
     */
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Get is latest flag.
     *
     * @return is latest
     */
    public Boolean getIsLatest() {
        return isLatest;
    }

    /**
     * Set is latest flag.
     *
     * @param isLatest is latest
     */
    public void setIsLatest(Boolean isLatest) {
        this.isLatest = isLatest;
    }

    /**
     * Get status.
     *
     * @return status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Set status.
     *
     * @param status status
     */
    public void setStatus(String status) {
        this.status = status;
    }
}
