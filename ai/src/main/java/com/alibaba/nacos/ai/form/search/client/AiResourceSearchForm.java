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

package com.alibaba.nacos.ai.form.search.client;

import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Generic cursor-based AI Resource Search form.
 *
 * @author Nacos
 */
public class AiResourceSearchForm implements NacosForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private static final int DEFAULT_LIMIT = 20;
    
    private static final int MAX_LIMIT = 100;
    
    private static final int MAX_QUERY_LENGTH = 1024;
    
    private static final int MAX_FILTER_VALUES = 32;
    
    private static final int MAX_CURSOR_LENGTH = 2048;
    
    private String namespaceId;
    
    private String query;
    
    private List<String> resourceTypes;
    
    private List<String> tagsAll;
    
    private List<String> capabilitiesAny;
    
    private String cursor;
    
    private Integer limit;
    
    @Override
    public void validate() throws NacosApiException {
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        AgentValidationUtils.validateNamespaceId(namespaceId);
        validateLength(query, MAX_QUERY_LENGTH, "query");
        validateLength(cursor, MAX_CURSOR_LENGTH, "cursor");
        resourceTypes = normalize(resourceTypes, MAX_FILTER_VALUES, "resourceTypes");
        tagsAll = normalize(tagsAll, MAX_FILTER_VALUES, "tagsAll");
        capabilitiesAny = normalize(capabilitiesAny, MAX_FILTER_VALUES, "capabilitiesAny");
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw invalid("limit must be between 1 and " + MAX_LIMIT);
        }
    }
    
    private List<String> normalize(List<String> values, int maxSize, String field)
        throws NacosApiException {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                normalized.add(value.trim());
            }
        }
        if (normalized.size() > maxSize) {
            throw invalid(field + " exceeds " + maxSize + " values");
        }
        return new ArrayList<>(normalized);
    }
    
    private void validateLength(String value, int maxLength, String field)
        throws NacosApiException {
        if (value != null && value.length() > maxLength) {
            throw invalid(field + " exceeds " + maxLength + " characters");
        }
    }
    
    private NacosApiException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public List<String> getResourceTypes() {
        return resourceTypes;
    }
    
    public void setResourceTypes(List<String> resourceTypes) {
        this.resourceTypes = resourceTypes;
    }
    
    public List<String> getTagsAll() {
        return tagsAll;
    }
    
    public void setTagsAll(List<String> tagsAll) {
        this.tagsAll = tagsAll;
    }
    
    public List<String> getCapabilitiesAny() {
        return capabilitiesAny;
    }
    
    public void setCapabilitiesAny(List<String> capabilitiesAny) {
        this.capabilitiesAny = capabilitiesAny;
    }
    
    public String getCursor() {
        return cursor;
    }
    
    public void setCursor(String cursor) {
        this.cursor = cursor;
    }
    
    public Integer getLimit() {
        return limit;
    }
    
    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
