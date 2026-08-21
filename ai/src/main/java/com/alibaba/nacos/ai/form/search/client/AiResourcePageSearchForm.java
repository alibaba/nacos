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
 * Common numbered-page form for one resource-specific Search facade.
 *
 * @author Nacos
 */
public class AiResourcePageSearchForm implements NacosForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    protected static final int MAX_FILTER_VALUES = 32;
    
    private static final int MAX_QUERY_LENGTH = 1024;
    
    private String namespaceId;
    
    private String query;
    
    private List<String> tagsAll;
    
    @Override
    public void validate() throws NacosApiException {
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        AgentValidationUtils.validateNamespaceId(namespaceId);
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            throw invalid("query exceeds " + MAX_QUERY_LENGTH + " characters");
        }
        tagsAll = normalize(tagsAll, MAX_FILTER_VALUES, "tagsAll");
    }
    
    protected List<String> normalize(List<String> values, int maxSize, String field)
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
    
    protected NacosApiException invalid(String message) {
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
    
    public List<String> getTagsAll() {
        return tagsAll;
    }
    
    public void setTagsAll(List<String> tagsAll) {
        this.tagsAll = tagsAll;
    }
}
