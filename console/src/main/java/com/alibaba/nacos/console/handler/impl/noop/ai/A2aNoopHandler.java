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
 *
 */

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.ai.form.a2a.admin.AgentCardForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardUpdateForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.A2aHandler;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A2a inner handler.
 *
 * @author KiteSoar
 */
@Component
@ConditionalOnMissingBean(value = A2aHandler.class, ignored = A2aNoopHandler.class)
public class A2aNoopHandler implements A2aHandler {
    
    private static final String A2A_NOT_ENABLED_MESSAGE = "Nacos AI A2A module and API required both `naming` and `config` module.";
    
    @Override
    public void registerAgent(AgentCard agentCard, AgentCardForm agentCardForm) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                A2A_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public AgentCardDetailInfo getAgentCardWithVersions(AgentForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                A2A_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void deleteAgent(AgentForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                A2A_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void updateAgentCard(AgentCard agentCard, AgentCardUpdateForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                A2A_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public Page<AgentCardVersionInfo> listAgents(AgentListForm agentListForm, PageForm pageForm) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                A2A_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public List<AgentVersionDetail> listAgentVersions(String namespaceId, String name) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                A2A_NOT_ENABLED_MESSAGE);
    }
}
