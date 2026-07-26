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

import com.alibaba.nacos.api.ai.model.importer.AiResourceImportItem;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Base form for AI resource import APIs.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public abstract class AbstractAiResourceImportForm implements NacosForm, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String namespaceId;
    
    private String resourceType;
    
    private String sourceId;
    
    private String options;
    
    protected void validateSource() throws NacosApiException {
        if (StringUtils.isBlank(resourceType)) {
            throw missingParameter("resourceType");
        }
        if (StringUtils.isBlank(sourceId)) {
            throw missingParameter("sourceId");
        }
    }
    
    protected Map<String, String> parseOptions() throws NacosApiException {
        if (StringUtils.isBlank(options)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(options, new TypeReference<Map<String, String>>() {
            });
        } catch (RuntimeException e) {
            throw parseFailed("options", e);
        }
    }
    
    protected List<AiResourceImportItem> parseSelectedItems(String selectedItems)
        throws NacosApiException {
        if (StringUtils.isBlank(selectedItems)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(selectedItems,
                new TypeReference<List<AiResourceImportItem>>() {
                });
        } catch (RuntimeException e) {
            throw parseFailed("selectedItems", e);
        }
    }
    
    protected NacosApiException missingParameter(String name) {
        return new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
            "Required parameter `" + name + "` is not present.");
    }
    
    private NacosApiException parseFailed(String name, RuntimeException cause) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, cause,
            "Request parameter `" + name + "` can't be parsed.");
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    public String getSourceId() {
        return sourceId;
    }
    
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }
    
    public String getOptions() {
        return options;
    }
    
    public void setOptions(String options) {
        this.options = options;
    }
}
