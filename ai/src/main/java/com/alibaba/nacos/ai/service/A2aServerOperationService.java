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
import com.alibaba.nacos.ai.utils.AgentCardUtil;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.beans.BeanUtils;

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
    
    public A2aServerOperationService(ConfigQueryChainService configQueryChainService,
            ConfigOperationService configOperationService, ConfigDetailService configDetailService) {
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
        AgentCardVersionInfo agentCardVersionInfo = AgentCardUtil.buildAgentCardVersionInfo(form, true);
        ConfigForm configForm = transferVersionInfoToConfigForm(agentCardVersionInfo, form.getNamespaceId());
        ConfigRequestInfo versionConfigRequest = new ConfigRequestInfo();
        versionConfigRequest.setUpdateForExist(Boolean.FALSE);
        configOperationService.publishConfig(configForm, versionConfigRequest, null);
        
        // 2. register agent's version info
        AgentCardDetailInfo agentCardDetailInfo = AgentCardUtil.buildAgentCardDetailInfo(form);
        ConfigForm configFormVersion = transferAgentInfoToConfigForm(agentCardDetailInfo, form.getNamespaceId());
        ConfigRequestInfo agentCardConfigRequest = new ConfigRequestInfo();
        agentCardConfigRequest.setUpdateForExist(Boolean.FALSE);
        configOperationService.publishConfig(configFormVersion, agentCardConfigRequest, null);
    }
    
    /**
     * Delete agent.
     *
     * @param form agent form
     */
    public void deleteAgent(AgentForm form) throws NacosException {
        String encodedName = ParamUtils.encodeName(form.getName());
        String namespaceId = form.getNamespaceId();
        
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(encodedName, AGENT_GROUP,
                namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            return;
        }
        
        AgentCardVersionInfo agentCardVersionInfo = JacksonUtils.toObj(response.getContent(),
                AgentCardVersionInfo.class);
        List<String> allVersions = agentCardVersionInfo.getVersionDetails().stream().map(AgentVersionDetail::getVersion)
                .toList();
        
        // 1. If version is specified, only delete the corresponding version of the agent
        if (form.getVersion() != null) {
            String versionDataId = encodedName + "-" + form.getVersion();
            configOperationService.deleteConfig(versionDataId, AGENT_VERSION_GROUP, namespaceId, null, null, "nacos",
                    null);
            
            List<AgentVersionDetail> versionDetails = agentCardVersionInfo.getVersionDetails();
            
            boolean isLatestVersion = form.getVersion().equals(agentCardVersionInfo.getLatestPublishedVersion());
            
            if (versionDetails.size() == 1 && versionDetails.get(0).getVersion().equals(form.getVersion())) {
                configOperationService.deleteConfig(encodedName, AGENT_GROUP, namespaceId, null, null, "nacos", null);
            } else {
                agentCardVersionInfo.getVersionDetails()
                        .removeIf(versionDetail -> versionDetail.getVersion().equals(form.getVersion()));
                
                if (isLatestVersion) {
                    agentCardVersionInfo.setLatestPublishedVersion(null);
                    agentCardVersionInfo.setVersion(null);
                }
                
                ConfigForm updateForm = transferVersionInfoToConfigForm(agentCardVersionInfo, namespaceId);
                ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
                configRequestInfo.setUpdateForExist(Boolean.TRUE);
                configOperationService.publishConfig(updateForm, configRequestInfo, null);
            }
        } else {
            // 2. If no version specified, delete all versions and agent information
            for (String version : allVersions) {
                String versionDataId = encodedName + "-" + version;
                configOperationService.deleteConfig(versionDataId, AGENT_VERSION_GROUP, namespaceId, null, null,
                        "nacos", null);
            }
            
            configOperationService.deleteConfig(encodedName, AGENT_GROUP, namespaceId, null, null, "nacos", null);
        }
    }
    
    /**
     * Update agent card.
     *
     * @param form agent update form
     * @throws NacosException nacos exception
     */
    public void updateAgentCard(AgentUpdateForm form) throws NacosException {
        final AgentCardVersionInfo existingAgentInfo = queryAgentCardVersionInfo(form);
        
        // 2. Check if the version exists, if not exist, add new version into version info
        boolean versionExisted = existingAgentInfo.getVersionDetails().stream()
                .anyMatch(agentVersionDetail -> StringUtils.equals(agentVersionDetail.getVersion(), form.getVersion()));
        if (!versionExisted) {
            existingAgentInfo.getVersionDetails()
                    .add(AgentCardUtil.buildAgentVersionDetail(form, form.getSetAsLatest()));
        }
        
        AgentCardDetailInfo agentCardDetailInfo = AgentCardUtil.buildAgentCardDetailInfo(form);
        BeanUtils.copyProperties(agentCardDetailInfo, existingAgentInfo, "versionDetails", "latestPublishedVersion");
        
        if (form.getSetAsLatest()) {
            existingAgentInfo.setLatestPublishedVersion(form.getVersion());
            
            List<AgentVersionDetail> updatedVersionDetails = existingAgentInfo.getVersionDetails().stream()
                    .peek(detail -> {
                        if (StringUtils.equals(detail.getVersion(), form.getVersion())) {
                            // Only update the corresponding version
                            detail.setLatest(true);
                            AgentCardUtil.updateUpdateTime(detail);
                        } else {
                            detail.setLatest(false);
                        }
                    }).toList();
            existingAgentInfo.setVersionDetails(updatedVersionDetails);
        }
        
        // 3. Update agent version info
        String namespaceId = form.getNamespaceId();
        ConfigForm configForm = transferVersionInfoToConfigForm(existingAgentInfo, namespaceId);
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setUpdateForExist(Boolean.TRUE);
        configOperationService.publishConfig(configForm, configRequestInfo, null);
        
        // 4. Update agent info
        ConfigForm versionConfigForm = transferAgentInfoToConfigForm(agentCardDetailInfo, namespaceId);
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
        String encodedName = ParamUtils.encodeName(agentListForm.getName());
        
        int pageNo = pageForm.getPageNo();
        int pageSize = pageForm.getPageSize();
        
        String dataId;
        if (StringUtils.isEmpty(encodedName) || Constants.A2A.SEARCH_BLUR.equals(encodedName)) {
            search = Constants.A2A.SEARCH_BLUR;
            dataId = Constants.ALL_PATTERN + encodedName + Constants.ALL_PATTERN;
        } else {
            search = Constants.A2A.SEARCH_ACCURATE;
            dataId = encodedName;
        }
        
        Page<ConfigInfo> configInfoPage = configDetailService.findConfigInfoPage(search, pageNo, pageSize, dataId,
                AGENT_GROUP, namespaceId, null);
        
        List<AgentCardVersionInfo> versionInfos = configInfoPage.getPageItems().stream()
                .map(configInfo -> JacksonUtils.toObj(configInfo.getContent(), AgentCardVersionInfo.class)).toList();
        
        Page<AgentCardVersionInfo> result = new Page<>();
        result.setPageItems(versionInfos);
        result.setTotalCount(configInfoPage.getTotalCount());
        result.setPagesAvailable((int) Math.ceil((double) configInfoPage.getTotalCount() / (double) pageSize));
        result.setPageNumber(pageNo);
        
        return result;
    }
    
    /**
     * List agent versions.
     * @param namespaceId namespace id of target agent
     * @param name        name of target agent
     * @return agent version detail list
     */
    public List<AgentVersionDetail> listAgentVersions(String namespaceId, String name) throws NacosApiException {
        AgentCardVersionInfo agentCardVersionInfo = queryAgentCardVersionInfo(namespaceId, name);
        return agentCardVersionInfo.getVersionDetails();
    }
    
    /**
     * Query Agent Card. If not specified version, query the latest version.
     *
     * @param form agent form
     * @return agent card detail info
     * @throws NacosApiException nacos api exception
     */
    public AgentCardDetailInfo getAgentCard(AgentForm form) throws NacosApiException {
        String namespaceId = form.getNamespaceId();
        AgentCardVersionInfo agentCardVersionInfo = queryAgentCardVersionInfo(form);
        return StringUtils.isEmpty(form.getVersion()) ? queryLatestVersion(agentCardVersionInfo, namespaceId)
                : queryTargetVersion(agentCardVersionInfo, form.getVersion(), namespaceId);
    }
    
    private AgentCardDetailInfo queryLatestVersion(AgentCardVersionInfo agentCardVersionInfo, String namespaceId)
            throws NacosApiException {
        String latestVersion = agentCardVersionInfo.getVersionDetails().stream().filter(AgentVersionDetail::isLatest)
                .findFirst().orElseThrow(
                        () -> new NacosApiException(NacosException.NOT_FOUND, ErrorCode.AGENT_VERSION_NOT_FOUND,
                                String.format("Agent %s latest version not found", agentCardVersionInfo.getName())))
                .getVersion();
        return queryTargetVersion(agentCardVersionInfo, latestVersion, namespaceId);
    }
    
    private AgentCardDetailInfo queryTargetVersion(AgentCardVersionInfo agentCardVersionInfo, String version,
            String namespaceId) throws NacosApiException {
        String versionDataId = ParamUtils.encodeName(agentCardVersionInfo.getName()) + "-" + version;
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(versionDataId,
                AGENT_VERSION_GROUP, namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.AGENT_VERSION_NOT_FOUND,
                    String.format("Agent %s version %s not found.", agentCardVersionInfo.getName(), version));
        }
        return JacksonUtils.toObj(response.getContent(), AgentCardDetailInfo.class);
    }
    
    private ConfigForm transferVersionInfoToConfigForm(AgentCardVersionInfo agentCardVersionInfo, String namespaceId) {
        ConfigForm configForm = new ConfigForm();
        String actualDataId = ParamUtils.encodeName(agentCardVersionInfo.getName());
        configForm.setDataId(actualDataId);
        configForm.setGroup(AGENT_GROUP);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(JacksonUtils.toJson(agentCardVersionInfo));
        configForm.setConfigTags("nacos.internal.config=agent");
        configForm.setAppName(agentCardVersionInfo.getName());
        configForm.setSrcUser("nacos");
        configForm.setType(ConfigType.JSON.getType());
        
        return configForm;
    }
    
    private ConfigForm transferAgentInfoToConfigForm(AgentCardDetailInfo storageInfo, String namespaceId) {
        ConfigForm configForm = new ConfigForm();
        String actualDataId = ParamUtils.encodeName(storageInfo.getName()) + "-" + storageInfo.getVersion();
        configForm.setDataId(actualDataId);
        configForm.setGroup(AGENT_VERSION_GROUP);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(JacksonUtils.toJson(storageInfo));
        configForm.setConfigTags("nacos.internal.config=agent-version");
        configForm.setAppName(storageInfo.getName());
        configForm.setSrcUser("nacos");
        configForm.setType(ConfigType.JSON.getType());
        
        return configForm;
    }
    
    private AgentCardVersionInfo queryAgentCardVersionInfo(AgentForm form) throws NacosApiException {
        return queryAgentCardVersionInfo(form.getNamespaceId(), form.getName());
    }
    
    private AgentCardVersionInfo queryAgentCardVersionInfo(String namespaceId, String name) throws NacosApiException {
        // Check if the agent exists
        String actualDataId = ParamUtils.encodeName(name);
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(actualDataId,
                AGENT_GROUP, namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.AGENT_NOT_FOUND,
                    "Cannot update agent: Agent not found: " + name);
        }
        return JacksonUtils.toObj(response.getContent(), AgentCardVersionInfo.class);
    }
}
