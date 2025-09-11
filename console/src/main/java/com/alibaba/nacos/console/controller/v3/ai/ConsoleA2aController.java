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
 */

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardUpdateForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.ai.param.AgentHttpParamExtractor;
import com.alibaba.nacos.ai.utils.AgentRequestUtil;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.console.proxy.ai.A2aProxy;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Console A2a Controller.
 *
 * @author KiteSoar
 */
@NacosApi
@RestController
@RequestMapping(Constants.A2A.CONSOLE_PATH)
@ExtractorManager.Extractor(httpExtractor = AgentHttpParamExtractor.class)
public class ConsoleA2aController {
    
    private final A2aProxy a2aProxy;
    
    public ConsoleA2aController(A2aProxy a2aProxy) {
        this.a2aProxy = a2aProxy;
    }
    
    /**
     * register agent.
     *
     * @param form the agent card form to register
     * @return result of the registration operation
     * @throws NacosException if the agent registration fails due to invalid input or internal error
     */
    @PostMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> registerAgent(AgentCardForm form) throws NacosException {
        form.validate();
        AgentCard agentCard = AgentRequestUtil.parseAgentCard(form);
        a2aProxy.registerAgent(agentCard, form);
        return Result.success("ok");
    }
    
    /**
     * get agent card.
     *
     * @param form the agent form to get
     * @return result of the get operation
     * @throws NacosApiException if the agent get fails due to invalid input or internal error
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<AgentCardDetailInfo> getAgentCard(AgentForm form) throws NacosException {
        form.validate();
        return Result.success(a2aProxy.getAgentCard(form));
    }
    
    /**
     * update agent.
     *
     * @param form the agent update form to update
     * @return result of the update operation
     * @throws NacosException if the agent update fails due to invalid input or internal error
     */
    @PutMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> updateAgentCard(AgentCardUpdateForm form) throws NacosException {
        form.validate();
        AgentCard agentCard = AgentRequestUtil.parseAgentCard(form);
        a2aProxy.updateAgentCard(agentCard, form);
        return Result.success("ok");
    }
    
    /**
     * delete agent.
     *
     * @param form the agent form to delete
     * @return result of the deletion operation
     * @throws NacosException if the agent deletion fails due to invalid input or internal error
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> deleteAgent(AgentForm form) throws NacosException {
        form.validate();
        a2aProxy.deleteAgent(form);
        return Result.success("ok");
    }
    
    /**
     * list agents.
     *
     * @param agentListForm the agent list form to list
     * @param pageForm the page form to list
     * @return result of the list operation
     * @throws NacosException if the agent list fails due to invalid input or internal error
     */
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Page<AgentCardVersionInfo>> listAgents(AgentListForm agentListForm, PageForm pageForm)
            throws NacosException {
        agentListForm.validate();
        pageForm.validate();
        return Result.success(a2aProxy.listAgents(agentListForm, pageForm));
    }
    
    /**
     * List all versions for target Agent.
     *
     * @param agentForm agent form
     * @return all version for target agent.
     * @throws NacosException nacos exception
     */
    @GetMapping("/version/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<List<AgentVersionDetail>> listAgentVersions(AgentForm agentForm) throws NacosException {
        agentForm.validate();
        return Result.success(a2aProxy.listAgentVersions(agentForm.getNamespaceId(), agentForm.getAgentName()));
    }
}

