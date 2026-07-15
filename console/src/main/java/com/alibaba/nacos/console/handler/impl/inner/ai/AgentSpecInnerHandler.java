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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecDraftCreateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecBizTagsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecLabelsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecListForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecOnlineForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecScopeForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecSubmitForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecUpdateForm;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecOperationService;
import com.alibaba.nacos.ai.utils.AgentSpecRequestUtil;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.ai.AgentSpecHandler;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AgentSpec inner handler.
 *
 * @author nacos
 */
@Component
@EnabledInnerHandler
@EnabledAiHandler
public class AgentSpecInnerHandler implements AgentSpecHandler {
    
    private final AgentSpecOperationService agentSpecOperationService;
    
    public AgentSpecInnerHandler(AgentSpecOperationService agentSpecOperationService) {
        this.agentSpecOperationService = agentSpecOperationService;
    }
    
    @Override
    public AgentSpecMeta getAgentSpec(AgentSpecForm form) throws NacosException {
        return agentSpecOperationService.getAgentSpecDetail(form.getNamespaceId(),
            form.getAgentSpecName(),
            form.getVersion());
    }
    
    @Override
    public AgentSpec getAgentSpecVersion(AgentSpecForm form) throws NacosException {
        return agentSpecOperationService.getAgentSpecVersionDetail(form.getNamespaceId(),
            form.getAgentSpecName(),
            form.getVersion());
    }
    
    @Override
    public void deleteAgentSpec(AgentSpecForm form) throws NacosException {
        agentSpecOperationService.deleteAgentSpec(form.getNamespaceId(), form.getAgentSpecName());
    }
    
    @Override
    public Page<AgentSpecSummary> listAgentSpecs(AgentSpecListForm agentSpecListForm,
        AiResourceFilterableForm filterableForm, PageForm pageForm) throws NacosException {
        return agentSpecOperationService.listAgentSpecs(agentSpecListForm.getNamespaceId(),
            agentSpecListForm.getAgentSpecName(), agentSpecListForm.getSearch(),
            agentSpecListForm.getOrderBy(), filterableForm.getOwner(), filterableForm.getScope(),
            pageForm.getPageNo(), pageForm.getPageSize());
    }
    
    @Override
    public String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException {
        return agentSpecOperationService.uploadAgentSpecFromZip(namespaceId, zipBytes, overwrite);
    }
    
    @Override
    public String createDraft(AgentSpecDraftCreateForm form) throws NacosException {
        return agentSpecOperationService.createDraft(form.getNamespaceId(), form.getAgentSpecName(),
            form.getBasedOnVersion(), form.getTargetVersion());
    }
    
    @Override
    public void updateDraft(AgentSpecUpdateForm form) throws NacosException {
        AgentSpec agentSpec = AgentSpecRequestUtil.parseAgentSpec(form);
        agentSpecOperationService.updateDraft(form.getNamespaceId(), agentSpec);
    }
    
    @Override
    public void deleteDraft(AgentSpecForm form) throws NacosException {
        agentSpecOperationService.deleteDraft(form.getNamespaceId(), form.getAgentSpecName());
    }
    
    @Override
    public String submit(AgentSpecSubmitForm form) throws NacosException {
        return agentSpecOperationService.submit(form.getNamespaceId(), form.getAgentSpecName(),
            form.getVersion());
    }
    
    @Override
    public void publish(AgentSpecPublishForm form) throws NacosException {
        agentSpecOperationService.publish(form.getNamespaceId(), form.getAgentSpecName(),
            form.getVersion(),
            true);
    }
    
    @Override
    public void forcePublish(AgentSpecPublishForm form) throws NacosException {
        agentSpecOperationService.forcePublish(form.getNamespaceId(), form.getAgentSpecName(),
            form.getVersion(),
            true);
    }
    
    @Override
    public void redraft(AgentSpecPublishForm form) throws NacosException {
        agentSpecOperationService.redraft(form.getNamespaceId(), form.getAgentSpecName(),
            form.getVersion());
    }
    
    @Override
    public void updateLabels(AgentSpecLabelsUpdateForm form) throws NacosException {
        Map<String, String> labels = JacksonUtils.toObj(form.getLabels(), Map.class);
        agentSpecOperationService.updateLabels(form.getNamespaceId(), form.getAgentSpecName(),
            labels);
    }
    
    @Override
    public void updateBizTags(AgentSpecBizTagsUpdateForm form) throws NacosException {
        agentSpecOperationService.updateBizTags(form.getNamespaceId(), form.getAgentSpecName(),
            form.getBizTags());
    }
    
    @Override
    public void changeOnlineStatus(AgentSpecOnlineForm form, boolean online) throws NacosException {
        agentSpecOperationService.changeOnlineStatus(form.getNamespaceId(), form.getAgentSpecName(),
            form.getScope(),
            form.getVersion(), online);
    }
    
    @Override
    public void updateScope(AgentSpecScopeForm form) throws NacosException {
        agentSpecOperationService.updateScope(form.getNamespaceId(), form.getAgentSpecName(),
            form.getScope());
    }
}
