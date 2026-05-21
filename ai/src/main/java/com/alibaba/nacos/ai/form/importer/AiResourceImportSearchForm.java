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

package com.alibaba.nacos.ai.form.importer;

import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;

/**
 * Form for searching external AI resource import candidates.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class AiResourceImportSearchForm extends AbstractAiResourceImportForm {
    
    private static final long serialVersionUID = 1L;
    
    private String query;
    
    private String cursor;
    
    private Integer limit;
    
    @Override
    public void validate() throws NacosApiException {
        validateSource();
    }
    
    /**
     * Convert form data to import search request.
     *
     * @return import search request
     * @throws NacosApiException if options can't be parsed
     */
    public AiResourceImportSearchRequest toRequest() throws NacosApiException {
        AiResourceImportSearchRequest request = new AiResourceImportSearchRequest();
        request.setNamespaceId(getNamespaceId());
        request.setResourceType(getResourceType());
        request.setSourceId(getSourceId());
        request.setQuery(query);
        request.setCursor(cursor);
        request.setLimit(limit);
        request.setOptions(parseOptions());
        return request;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
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
