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

package com.alibaba.nacos.ai.form;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;

import java.io.Serial;
import java.io.Serializable;

/**
 * Common filterable form for AI resource list APIs (Skill, AgentSpec, etc.).
 *
 * <p>Carries optional filter criteria that apply to all AI resource types.
 * Fields are nullable; when absent the corresponding filter is not applied and
 * the behaviour is identical to the previous version of the API (100% backward-compatible).</p>
 *
 * <p>Intended for future extensibility: additional common filter fields (e.g. {@code bizTag})
 * should be added here rather than duplicated across per-resource list forms.</p>
 *
 * @author nacos
 */
public class AiResourceFilterableForm implements NacosForm, Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * Optional filter by resource owner (creator identity).
     *
     * <ul>
     *   <li>Admin users: may specify any owner value to filter to that owner's resources,
     *       or leave blank to see all resources.</li>
     *   <li>Non-admin users: should only pass their own identity (i.e. "only mine") or leave blank.</li>
     * </ul>
     * When {@code null} or empty, no owner filter is applied.
     */
    private String owner;
    
    /**
     * Optional filter by visibility scope.
     *
     * <p>Accepted values: {@code "PUBLIC"} or {@code "PRIVATE"} (case-insensitive).
     * When {@code null} or empty, no scope filter is applied and both public and private
     * resources that the caller is authorized to see are returned.</p>
     */
    private String scope;
    
    /**
     * Optional filter by business tag.
     *
     * <p>When specified, only resources whose {@code bizTags} column contains the given value
     * are returned (fuzzy match). When {@code null} or empty, no bizTag filter is applied.</p>
     */
    private String bizTag;
    
    @Override
    public void validate() throws NacosApiException {
        if (StringUtils.isNotBlank(scope)
            && !VisibilityConstants.SCOPE_PUBLIC.equalsIgnoreCase(scope)
            && !VisibilityConstants.SCOPE_PRIVATE.equalsIgnoreCase(scope)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Request parameter `scope` must be PUBLIC or PRIVATE.");
        }
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
    }
    
    public String getBizTag() {
        return bizTag;
    }
    
    public void setBizTag(String bizTag) {
        this.bizTag = bizTag;
    }
}
