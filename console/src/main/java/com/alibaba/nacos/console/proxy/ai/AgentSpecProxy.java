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

package com.alibaba.nacos.console.proxy.ai;

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
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.AgentSpecHandler;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.stereotype.Component;

/**
 * AgentSpec proxy.
 *
 * @author nacos
 */
@Component
public class AgentSpecProxy {
    
    private final AgentSpecHandler agentSpecHandler;
    
    public AgentSpecProxy(AgentSpecHandler agentSpecHandler) {
        this.agentSpecHandler = agentSpecHandler;
    }
    
    public AgentSpecMeta getAgentSpec(AgentSpecForm form) throws NacosException {
        return agentSpecHandler.getAgentSpec(form);
    }
    
    public AgentSpec getAgentSpecVersion(AgentSpecForm form) throws NacosException {
        return agentSpecHandler.getAgentSpecVersion(form);
    }
    
    public void deleteAgentSpec(AgentSpecForm form) throws NacosException {
        agentSpecHandler.deleteAgentSpec(form);
    }
    
    public Page<AgentSpecSummary> listAgentSpecs(AgentSpecListForm agentSpecListForm,
        AiResourceFilterableForm filterableForm, PageForm pageForm) throws NacosException {
        return agentSpecHandler.listAgentSpecs(agentSpecListForm, filterableForm, pageForm);
    }
    
    public String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes)
        throws NacosException {
        return uploadAgentSpecFromZip(namespaceId, zipBytes, false);
    }
    
    public String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException {
        return agentSpecHandler.uploadAgentSpecFromZip(namespaceId, zipBytes, overwrite);
    }
    
    public String createDraft(AgentSpecDraftCreateForm form) throws NacosException {
        return agentSpecHandler.createDraft(form);
    }
    
    public void updateDraft(AgentSpecUpdateForm form) throws NacosException {
        agentSpecHandler.updateDraft(form);
    }
    
    public void deleteDraft(AgentSpecForm form) throws NacosException {
        agentSpecHandler.deleteDraft(form);
    }
    
    public String submit(AgentSpecSubmitForm form) throws NacosException {
        return agentSpecHandler.submit(form);
    }
    
    public void publish(AgentSpecPublishForm form) throws NacosException {
        agentSpecHandler.publish(form);
    }
    
    public void forcePublish(AgentSpecPublishForm form) throws NacosException {
        agentSpecHandler.forcePublish(form);
    }
    
    public void redraft(AgentSpecPublishForm form) throws NacosException {
        agentSpecHandler.redraft(form);
    }
    
    public void updateLabels(AgentSpecLabelsUpdateForm form) throws NacosException {
        agentSpecHandler.updateLabels(form);
    }
    
    public void updateBizTags(AgentSpecBizTagsUpdateForm form) throws NacosException {
        agentSpecHandler.updateBizTags(form);
    }
    
    public void changeOnlineStatus(AgentSpecOnlineForm form, boolean online) throws NacosException {
        agentSpecHandler.changeOnlineStatus(form, online);
    }
    
    public void updateScope(AgentSpecScopeForm form) throws NacosException {
        agentSpecHandler.updateScope(form);
    }
    
    public void online(AgentSpecOnlineForm form) throws NacosException {
        agentSpecHandler.changeOnlineStatus(form, true);
    }
    
    public void offline(AgentSpecOnlineForm form) throws NacosException {
        agentSpecHandler.changeOnlineStatus(form, false);
    }
}
