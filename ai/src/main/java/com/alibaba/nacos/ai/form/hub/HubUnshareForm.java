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

import com.alibaba.nacos.ai.service.hub.HubSkillRequestValidator;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * HTTP form for the Hub Skill unshare operation.
 *
 * @author nacos
 */
public class HubUnshareForm implements NacosForm {
    
    private static final long serialVersionUID = 1L;
    
    private String hubEntryId;
    
    private String expectedMetaVersion;
    
    @Override
    public void validate() throws NacosApiException {
        prepareRequest();
    }
    
    /**
     * Prepare and validate the service request from HTTP parameters.
     *
     * @return validated request
     * @throws NacosApiException if the request is invalid
     */
    public HubUnshareRequest prepareRequest() throws NacosApiException {
        HubUnshareRequest request = new HubUnshareRequest();
        request.setHubEntryId(StringUtils.isBlank(hubEntryId) ? null : Long.valueOf(hubEntryId));
        request.setExpectedMetaVersion(
            StringUtils.isBlank(expectedMetaVersion) ? null : Long.valueOf(expectedMetaVersion));
        HubSkillRequestValidator.validate(request);
        return request;
    }
    
    public String getHubEntryId() {
        return hubEntryId;
    }
    
    public void setHubEntryId(String hubEntryId) {
        this.hubEntryId = hubEntryId;
    }
    
    public String getExpectedMetaVersion() {
        return expectedMetaVersion;
    }
    
    public void setExpectedMetaVersion(String expectedMetaVersion) {
        this.expectedMetaVersion = expectedMetaVersion;
    }
}
