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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.form.prompt.PromptForm;
import com.alibaba.nacos.ai.form.prompt.PromptHistoryForm;
import com.alibaba.nacos.ai.form.prompt.PromptLabelBindForm;
import com.alibaba.nacos.ai.form.prompt.PromptLabelForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.ai.form.prompt.PromptMetadataForm;
import com.alibaba.nacos.ai.form.prompt.PromptPublishForm;
import com.alibaba.nacos.ai.form.prompt.PromptQueryForm;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.PromptHandler;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * Remote implementation of Prompt handler.
 * 
 * <p>Calls remote Nacos server through maintainer client for Prompt operations.</p>
 *
 * @author nacos
 */
@Service
@EnabledRemoteHandler
@Conditional(ConditionFunctionEnabled.ConditionAiEnabled.class)
public class PromptRemoteHandler implements PromptHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public PromptRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public boolean publishPrompt(PromptPublishForm form, String srcUser, String srcIp) throws NacosException {
        return clientHolder.getAiMaintainerService().publishPrompt(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getVersion(),
                form.getTemplate(),
                form.getCommitMsg(),
                form.getDescription(),
                form.getBizTags()
        );
    }
    
    @Override
    public PromptMetaInfo getPromptMeta(PromptForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().getPromptMeta(
                form.getNamespaceId(),
                form.getPromptKey()
        );
    }
    
    @Override
    public PromptVersionInfo queryPromptDetail(PromptQueryForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().queryPromptDetail(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getVersion(),
                form.getLabel()
        );
    }
    
    @Override
    public boolean bindLabel(PromptLabelBindForm form, String srcUser, String srcIp) throws NacosException {
        return clientHolder.getAiMaintainerService().bindLabel(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getLabel(),
                form.getVersion()
        );
    }
    
    @Override
    public boolean unbindLabel(PromptLabelForm form, String srcUser, String srcIp) throws NacosException {
        return clientHolder.getAiMaintainerService().unbindLabel(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getLabel()
        );
    }
    
    @Override
    public boolean deletePrompt(PromptForm form, String srcUser, String srcIp) throws NacosException {
        return clientHolder.getAiMaintainerService().deletePrompt(
                form.getNamespaceId(),
                form.getPromptKey()
        );
    }
    
    @Override
    public Page<PromptMetaSummary> listPrompts(PromptListForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().listPrompts(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getSearch(),
                form.getBizTags(),
                form.getPageNo(),
                form.getPageSize()
        );
    }
    
    @Override
    public Page<PromptVersionSummary> listPromptVersions(PromptHistoryForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().listPromptVersions(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getPageNo(),
                form.getPageSize()
        );
    }
    
    @Override
    public boolean updatePromptMetadata(PromptMetadataForm form, String srcUser, String srcIp) throws NacosException {
        return clientHolder.getAiMaintainerService().updatePromptMetadata(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getDescription(),
                form.getBizTags()
        );
    }
}
