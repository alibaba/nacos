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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;

/** HTTP entry form preserving large IDs and returning controlled parameter errors. */
public class HubSkillReadEntryForm implements NacosForm {
    
    private static final long serialVersionUID = 1L;
    private String hubEntryId;
    @Override
    public void validate() throws NacosApiException {
        prepareEntryId();
    }
    
    /** Parse and validate the immutable Hub entry ID. */
    public Long prepareEntryId() throws NacosApiException {
        Long value;
        try {
            value = Long.valueOf(hubEntryId);
        } catch (NumberFormatException failure) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "hubEntryId must be a positive long integer");
        }
        HubSkillReadRequestValidator.validateEntryId(value);
        return value;
    }
    
    public String getHubEntryId() {
        return hubEntryId;
    }
    
    public void setHubEntryId(String value) {
        hubEntryId = value;
    }
}
