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

package com.alibaba.nacos.ai.form.agent.admin;

import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateAdminRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;

/**
 * Form for creating one initial or subsequent Agent draft.
 *
 * @author Nacos
 */
public class AgentDraftCreateForm extends AbstractAgentDraftForm {
    
    private static final long serialVersionUID = 1L;
    
    @Override
    public void validate() throws NacosApiException {
        toRequest();
    }
    
    /**
     * Parse and validate the form once to build a concrete Admin draft request.
     *
     * @return validated Agent draft-create request
     * @throws NacosApiException when a JSON-valued form field is invalid
     */
    public AgentDraftCreateAdminRequest toRequest() throws NacosApiException {
        AgentDraftCreateAdminRequest result = new AgentDraftCreateAdminRequest();
        fillRequest(result);
        result.validate();
        return result;
    }
}
