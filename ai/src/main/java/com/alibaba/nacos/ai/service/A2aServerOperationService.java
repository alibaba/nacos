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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.a2a.admin.AgentDetailForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentUpdateForm;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.enums.ResponseCode;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.beans.BeanUtils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.ai.constant.Constants.A2A.AGENT_GROUP;
import static com.alibaba.nacos.ai.constant.Constants.A2A.AGENT_VERSION_GROUP;

/**
 * A2a server operation service.
 *
 * @author KiteSoar
 */
@org.springframework.stereotype.Service
public class A2aServerOperationService {
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigDetailService configDetailService;
    
    public A2aServerOperationService(ConfigQueryChainService configQueryChainService, ConfigOperationService configOperationService,
            ConfigDetailService configDetailService) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.configDetailService = configDetailService;
    }
    
    /**
     * Register agent.
     *
     * @param form agent detail form
     * @throws NacosException nacos exception
     */
    public void registerAgent(AgentDetailForm form) throws NacosException {
        // 1. register agent's info
        AgentCardVersionInfo agentCardVersionInfo = buildAgentCardVersionInfo(form);
        ConfigForm configForm = transferVersionInfoToConfigForm(agentCardVersionInfo, form);
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setUpdateForExist(Boolean.FALSE);
        configOperationService.publishConfig(configForm, configRequestInfo, null);
        
        // 2. register agent's version info
        ConfigForm configFormVersion = transferAgentInfoToConfigForm(form);
        ConfigRequestInfo configRequestInfo0 = new ConfigRequestInfo();
        configRequestInfo0.setUpdateForExist(Boolean.FALSE);
        configOperationService.publishConfig(configFormVersion, configRequestInfo0, null);
    }
    
    private AgentCardVersionInfo buildAgentCardVersionInfo(AgentDetailForm form) {
        AgentCardVersionInfo agentCardVersionInfo = new AgentCardVersionInfo();
        agentCardVersionInfo.setProtocolVersion(form.getProtocolVersion());
        agentCardVersionInfo.setName(form.getName());
        agentCardVersionInfo.setDescription(form.getDescription());
        agentCardVersionInfo.setUrl(form.getUrl());
        agentCardVersionInfo.setVersion(form.getVersion());
        agentCardVersionInfo.setPreferredTransport(form.getPreferredTransport());
        agentCardVersionInfo.setAdditionalInterfaces(form.getAdditionalInterfaces());
        agentCardVersionInfo.setIconUrl(form.getIconUrl());
        agentCardVersionInfo.setProvider(form.getProvider());
        agentCardVersionInfo.setCapabilities(form.getCapabilities());
        agentCardVersionInfo.setSecuritySchemes(form.getSecuritySchemes());
        agentCardVersionInfo.setSecurity(form.getSecurity());
        agentCardVersionInfo.setDefaultInputModes(form.getDefaultInputModes());
        agentCardVersionInfo.setDefaultOutputModes(form.getDefaultOutputModes());
        agentCardVersionInfo.setSkills(form.getSkills());
        agentCardVersionInfo.setSupportsAuthenticatedExtendedCard(form.getSupportsAuthenticatedExtendedCard());
        agentCardVersionInfo.setDocumentationUrl(form.getDocumentationUrl());
        agentCardVersionInfo.setRegistrationType(form.getRegistrationType());
        
        agentCardVersionInfo.setLatestPublishedVersion(form.getVersion());
        AgentVersionDetail agentVersionDetail = new AgentVersionDetail();
        agentVersionDetail.setCreatedAt(getCurrentTime());
        agentVersionDetail.setUpdatedAt(getCurrentTime());
        agentVersionDetail.setVersion(form.getVersion());
        agentVersionDetail.setLatest(true);
        agentCardVersionInfo.setVersionDetails(Collections.singletonList(agentVersionDetail));
        
        return agentCardVersionInfo;
    }
    
    private String getCurrentTime() {
        ZonedDateTime currentTime = ZonedDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.RELEASE_DATE_FORMAT);
        return currentTime.format(formatter);
    }
    
    private ConfigForm transferVersionInfoToConfigForm(AgentCardVersionInfo agentCardVersionInfo, AgentDetailForm form) {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(form.getName());
        configForm.setGroup(AGENT_GROUP);
        configForm.setNamespaceId(form.getNamespaceId());
        configForm.setContent(JacksonUtils.toJson(agentCardVersionInfo));
        configForm.setConfigTags("nacos.internal.config=agent");
        configForm.setAppName(form.getName());
        configForm.setSrcUser("nacos");
        configForm.setType(ConfigType.JSON.getType());
        
        return configForm;
    }
    
    private ConfigForm transferAgentInfoToConfigForm(AgentDetailForm form) {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(form.getName() + "-" + form.getVersion());
        configForm.setGroup(AGENT_VERSION_GROUP);
        configForm.setNamespaceId(form.getNamespaceId());
        configForm.setContent(JacksonUtils.toJson(form));
        configForm.setConfigTags("nacos.internal.config=agent-version");
        configForm.setAppName(form.getName());
        configForm.setSrcUser("nacos");
        configForm.setType(ConfigType.JSON.getType());
        
        return configForm;
    }
    
    public AgentCardVersionInfo getAgentCard(AgentForm form) {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId(form.getName());
        request.setGroup(AGENT_GROUP);
        request.setTenant(form.getNamespaceId());
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        if (ResponseCode.FAIL.getCode() == response.getResultCode()) {
            throw new NacosConfigException(response.getMessage());
        }
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            return null;
        }
        
        return JacksonUtils.toObj(response.getContent(), AgentCardVersionInfo.class);
    }
    
    /**
     * Delete agent.
     *
     * @param form agent form
     */
    public void deleteAgent(AgentForm form) throws NacosException {
        String dataId = form.getName();
        String namespaceId = form.getNamespaceId();
        
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(dataId, AGENT_GROUP, namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            return;
        }
        
        AgentCardVersionInfo agentCardVersionInfo = JacksonUtils.toObj(response.getContent(), AgentCardVersionInfo.class);
        List<String> allVersions = agentCardVersionInfo.getVersionDetails().stream().map(AgentVersionDetail::getVersion).toList();
        
        // 1. If version is specified, only delete the corresponding version of the agent
        if (form.getVersion() != null) {
            String versionDataId = form.getName() + form.getVersion();
            configOperationService.deleteConfig(versionDataId, AGENT_VERSION_GROUP, namespaceId, null, null, "nacos",
                    null);
            
            List<AgentVersionDetail> versionDetails = agentCardVersionInfo.getVersionDetails();
            
            boolean isLatestVersion = form.getVersion().equals(agentCardVersionInfo.getLatestPublishedVersion());
            
            if (versionDetails.size() == 1 && versionDetails.get(0).getVersion().equals(form.getVersion())) {
                configOperationService.deleteConfig(dataId, AGENT_GROUP, namespaceId, null, null, "nacos", null);
            } else {
                agentCardVersionInfo.getVersionDetails().removeIf(versionDetail -> versionDetail.getVersion().equals(form.getVersion()));
                
                if (isLatestVersion) {
                    agentCardVersionInfo.setLatestPublishedVersion(null);
                    agentCardVersionInfo.setVersion(null);
                }
                
                ConfigForm updateForm = new ConfigForm();
                updateForm.setDataId(dataId);
                updateForm.setGroup(AGENT_GROUP);
                updateForm.setNamespaceId(namespaceId);
                updateForm.setContent(JacksonUtils.toJson(agentCardVersionInfo));
                updateForm.setConfigTags("nacos.internal.config=agent");
                updateForm.setAppName(form.getName());
                updateForm.setSrcUser("nacos");
                
                ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
                configRequestInfo.setUpdateForExist(Boolean.TRUE);
                configOperationService.publishConfig(updateForm, configRequestInfo, null);
            }
        } else {
            // 2. If no version specified, delete all versions and agent information
            for (String version : allVersions) {
                String versionDataId = form.getName() + "-" + version;
                configOperationService.deleteConfig(versionDataId, AGENT_VERSION_GROUP, namespaceId, null, null,
                        "nacos", null);
            }
            
            configOperationService.deleteConfig(dataId, AGENT_GROUP, namespaceId, null, null, "nacos", null);
        }
    }
    
    /**
     * Update agent card.
     *
     * @param form agent update form
     * @throws NacosException nacos exception
     */
    public void updateAgentCard(AgentUpdateForm form) throws NacosException {
        String dataId = form.getName();
        String groupName = AGENT_GROUP;
        String namespaceId = form.getNamespaceId();
        
        // 1. Check if the agent exists
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(dataId, groupName, namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            throw new NacosConfigException("Cannot update agent: Agent not found: " + form.getName());
        }
        
        final AgentCardVersionInfo existingAgentInfo = JacksonUtils.toObj(response.getContent(), AgentCardVersionInfo.class);
        
        // 2. Check if the version exists
        String versionDataId = form.getName() + "-" + form.getVersion();
        ConfigQueryChainRequest versionRequest = new ConfigQueryChainRequest();
        versionRequest.setDataId(versionDataId);
        versionRequest.setGroup(AGENT_VERSION_GROUP);
        versionRequest.setTenant(namespaceId);
        ConfigQueryChainResponse versionResponse = configQueryChainService.handle(versionRequest);
        
        if (versionResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            throw new NacosConfigException("Cannot update agent: Version not found: " + form.getVersion());
        }
        
        BeanUtils.copyProperties(form, existingAgentInfo, "versionDetails", "latestPublishedVersion");
        
        if (form.getSetAsLatest() != null && form.getSetAsLatest()) {
            existingAgentInfo.setLatestPublishedVersion(form.getVersion());
            
            List<AgentVersionDetail> updatedVersionDetails = existingAgentInfo.getVersionDetails().stream()
                    .peek(detail -> {
                        if (detail.getVersion().equals(form.getVersion())) {
                            // Only update the corresponding version
                            detail.setLatest(true);
                            detail.setUpdatedAt(getCurrentTime());
                        } else {
                            detail.setLatest(false);
                        }
                    }).toList();
            existingAgentInfo.setVersionDetails(updatedVersionDetails);
        }
        
        // 3. Update agent version info
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(dataId);
        configForm.setGroup(groupName);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(JacksonUtils.toJson(existingAgentInfo));
        configForm.setConfigTags("nacos.internal.config=agent");
        configForm.setAppName(form.getName());
        configForm.setSrcUser("nacos");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setUpdateForExist(Boolean.TRUE);
        configOperationService.publishConfig(configForm, configRequestInfo, null);
        
        // 4. Update agent info
        ConfigForm versionConfigForm = transferAgentInfoToConfigForm(form);
        ConfigRequestInfo versionConfigRequestInfo = new ConfigRequestInfo();
        versionConfigRequestInfo.setUpdateForExist(Boolean.TRUE);
        configOperationService.publishConfig(versionConfigForm, versionConfigRequestInfo, null);
    }
    
    /**
     * List agents.
     *
     * @param agentListForm agent list form
     * @param pageForm      page form
     * @return agent card version info list
     */
    public Page<AgentCardVersionInfo> listAgents(AgentListForm agentListForm, PageForm pageForm) {
        String search;
        String namespaceId = agentListForm.getNamespaceId();
        String name = agentListForm.getName();
        
        int pageNo = pageForm.getPageNo();
        int pageSize = pageForm.getPageSize();
        
        String dataId;
        if (StringUtils.isEmpty(name) || Constants.A2A.SEARCH_BLUR.equals(name)) {
            search = Constants.A2A.SEARCH_BLUR;
            dataId = Constants.ALL_PATTERN + name + Constants.ALL_PATTERN;
        } else {
            search = Constants.A2A.SEARCH_ACCURATE;
            dataId = name;
        }
        
        Page<ConfigInfo> configInfoPage = configDetailService.findConfigInfoPage(search, pageNo, pageSize, dataId,
                AGENT_GROUP, namespaceId, null);
        
        List<AgentCardVersionInfo> versionInfos = configInfoPage.getPageItems().stream()
                .map(configInfo -> JacksonUtils.toObj(configInfo.getContent(), AgentCardVersionInfo.class))
                .toList();
        
        Page<AgentCardVersionInfo> result = new Page<>();
        result.setPageItems(versionInfos);
        result.setTotalCount(configInfoPage.getTotalCount());
        result.setPagesAvailable((int) Math.ceil((double) configInfoPage.getTotalCount() / (double) pageSize));
        result.setPageNumber(pageNo);
        
        return result;
    }
}
