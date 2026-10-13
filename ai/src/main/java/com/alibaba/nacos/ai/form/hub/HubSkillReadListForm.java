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

package com.alibaba.nacos.ai.form.hub;

import com.alibaba.nacos.ai.service.hub.HubSkillReadRequestValidator;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import java.util.List;

/** HTTP directory form with explicit numeric validation before service access. */
public class HubSkillReadListForm implements NacosForm {
    
    private static final long serialVersionUID = 1L;
    private String pageNo = "1";
    private String pageSize = "20";
    private String query;
    private List<String> tagsAll;
    
    @Override
    public void validate() throws NacosApiException {
        prepareRequest();
    }
    
    /** Convert the HTTP form into a validated directory request. */
    public HubSkillListRequest prepareRequest() throws NacosApiException {
        HubSkillListRequest result = new HubSkillListRequest();
        try {
            result.setPageNo(Integer.parseInt(pageNo));
            result.setPageSize(Integer.parseInt(pageSize));
        } catch (NumberFormatException failure) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Invalid Hub page parameters");
        }
        result.setQuery(query);
        result.setTagsAll(tagsAll);
        HubSkillReadRequestValidator.validate(result);
        return result;
    }
    
    public String getPageNo() {
        return pageNo;
    }
    
    public void setPageNo(String value) {
        pageNo = value;
    }
    
    public String getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(String value) {
        pageSize = value;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String value) {
        query = value;
    }
    
    public List<String> getTagsAll() {
        return tagsAll;
    }
    
    public void setTagsAll(List<String> value) {
        tagsAll = value;
    }
}
