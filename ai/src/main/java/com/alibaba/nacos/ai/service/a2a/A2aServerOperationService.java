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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.ai.service.a2a.identity.AgentIdCodecHolder;
import com.alibaba.nacos.ai.utils.AgentCardUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
    
    private final SyncEffectService syncEffectService;
    
    private final ServiceStorage serviceStorage;
    
    private final AgentIdCodecHolder agentIdCodecHolder;
    
    public A2aServerOperationService(ConfigQueryChainService configQueryChainService,
            ConfigOperationService configOperationService, ConfigDetailService configDetailService,
            SyncEffectService syncEffectService, ServiceStorage serviceStorage,
            AgentIdCodecHolder agentIdCodecHolder) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.configDetailService = configDetailService;
        this.syncEffectService = syncEffectService;
        this.serviceStorage = serviceStorage;
        this.agentIdCodecHolder = agentIdCodecHolder;
    }
    
    /**
     * Register agent.
     *
     * @param agentCard agent card
     * @throws NacosException nacos exception
     */
    public void registerAgent(AgentCard agentCard, String namespaceId, String registrationType) throws NacosException {
        try {
            // 1. register agent's info
            AgentCardVersionInfo agentCardVersionInfo = AgentCardUtil.buildAgentCardVersionInfo(agentCard,
                    registrationType, true);
            ConfigForm configForm = transferVersionInfoToConfigForm(agentCardVersionInfo, namespaceId);
            ConfigRequestInfo versionConfigRequest = new ConfigRequestInfo();
            versionConfigRequest.setUpdateForExist(Boolean.FALSE);
            configOperationService.publishConfig(configForm, versionConfigRequest, null);
            
            // 2. register agent's version info
            AgentCardDetailInfo agentCardDetailInfo = AgentCardUtil.buildAgentCardDetailInfo(agentCard,
                    registrationType);
            ConfigForm configFormVersion = transferAgentInfoToConfigForm(agentCardDetailInfo, namespaceId);
            ConfigRequestInfo agentCardConfigRequest = new ConfigRequestInfo();
            agentCardConfigRequest.setUpdateForExist(Boolean.FALSE);
            long startOperationTime = System.currentTimeMillis();
            configOperationService.publishConfig(configFormVersion, agentCardConfigRequest, null);
            
            syncEffectService.toSync(configFormVersion, startOperationTime);
        } catch (ConfigAlreadyExistsException e) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    String.format("AgentCard name %s already exist", agentCard.getName()));
        }
    }
    
    /**
     * Delete agent.
     *
     * @param namespaceId   namespaceId of  agent
     * @param agentName     agent name
     * @param version       target version of want to delete, if is null or empty, delete all versions
     * @throws NacosException nacos exception
     */
    public void deleteAgent(String namespaceId, String agentName, String version) throws NacosException {
        String encodedName = agentIdCodecHolder.encode(agentName);
        
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
        if (StringUtils.isNotEmpty(version)) {
            String versionDataId = encodedName + "-" + version;
            configOperationService.deleteConfig(versionDataId, AGENT_VERSION_GROUP, namespaceId, null, null, "nacos",
                    null);
            
            List<AgentVersionDetail> versionDetails = agentCardVersionInfo.getVersionDetails();
            
            boolean isLatestVersion = version.equals(agentCardVersionInfo.getLatestPublishedVersion());
            
            if (versionDetails.size() == 1 && versionDetails.get(0).getVersion().equals(version)) {
                configOperationService.deleteConfig(encodedName, AGENT_GROUP, namespaceId, null, null, "nacos", null);
            } else {
                agentCardVersionInfo.getVersionDetails()
                        .removeIf(versionDetail -> versionDetail.getVersion().equals(version));
                
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
            for (String each : allVersions) {
                String versionDataId = encodedName + "-" + each;
                configOperationService.deleteConfig(versionDataId, AGENT_VERSION_GROUP, namespaceId, null, null,
                        "nacos", null);
            }
            
            configOperationService.deleteConfig(encodedName, AGENT_GROUP, namespaceId, null, null, "nacos", null);
        }
    }
    
    /**
     * Update agent card.
     *
     * @param agentCard         the new agent card information
     * @param namespaceId       namespace id
     * @param registrationType  new registration type
     * @param setAsLatest       whether set as latest version
     * @throws NacosException nacos exception
     */
    public void updateAgentCard(AgentCard agentCard, String namespaceId, String registrationType, boolean setAsLatest)
            throws NacosException {
        final AgentCardVersionInfo existingAgentInfo = queryAgentCardVersionInfo(namespaceId, agentCard.getName());
        
        // Check if the version exists, if not exist, add new version into version info
        boolean versionExisted = existingAgentInfo.getVersionDetails().stream().anyMatch(
                agentVersionDetail -> StringUtils.equals(agentVersionDetail.getVersion(), agentCard.getVersion()));
        if (!versionExisted) {
            existingAgentInfo.getVersionDetails().add(AgentCardUtil.buildAgentVersionDetail(agentCard, setAsLatest));
        }
        
        // If input new registrationType is empty, use existed registrationType.
        if (StringUtils.isEmpty(registrationType)) {
            registrationType = existingAgentInfo.getRegistrationType();
        }
        AgentCardDetailInfo agentCardDetailInfo = AgentCardUtil.buildAgentCardDetailInfo(agentCard, registrationType);
        BeanUtils.copyProperties(agentCardDetailInfo, existingAgentInfo, "versionDetails", "latestPublishedVersion");
        
        if (setAsLatest) {
            existingAgentInfo.setLatestPublishedVersion(agentCard.getVersion());
            
            List<AgentVersionDetail> updatedVersionDetails = existingAgentInfo.getVersionDetails().stream()
                    .peek(detail -> {
                        if (StringUtils.equals(detail.getVersion(), agentCard.getVersion())) {
                            // Only update the corresponding version
                            detail.setLatest(true);
                            AgentCardUtil.updateUpdateTime(detail);
                        } else {
                            detail.setLatest(false);
                        }
                    }).toList();
            existingAgentInfo.setVersionDetails(updatedVersionDetails);
        }
        
        // Update agent version info
        ConfigForm configForm = transferVersionInfoToConfigForm(existingAgentInfo, namespaceId);
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setUpdateForExist(Boolean.TRUE);
        configOperationService.publishConfig(configForm, configRequestInfo, null);
        
        // Update agent info
        ConfigForm versionConfigForm = transferAgentInfoToConfigForm(agentCardDetailInfo, namespaceId);
        ConfigRequestInfo versionConfigRequestInfo = new ConfigRequestInfo();
        versionConfigRequestInfo.setUpdateForExist(Boolean.TRUE);
        long startOperationTime = System.currentTimeMillis();
        configOperationService.publishConfig(versionConfigForm, versionConfigRequestInfo, null);
        
        syncEffectService.toSync(versionConfigForm, startOperationTime);
    }
    
    /**
     * List agents.
     *
     * @param namespaceId   namespace id
     * @param agentName     agent name
     * @param search        search type, {@link Constants.A2A#SEARCH_BLUR} or {@link Constants.A2A#SEARCH_ACCURATE}
     * @param pageNo        page number
     * @param pageSize      page size
     *
     * @return agent card version info list
     */
    public Page<AgentCardVersionInfo> listAgents(String namespaceId, String agentName, String search, int pageNo,
            int pageSize) throws NacosException {
        
        String dataId;
        if (StringUtils.isEmpty(agentName) || Constants.A2A.SEARCH_BLUR.equalsIgnoreCase(search)) {
            search = Constants.A2A.SEARCH_BLUR;
            dataId = Constants.ALL_PATTERN + agentIdCodecHolder.encodeForSearch(agentName) + Constants.ALL_PATTERN;
        } else {
            search = Constants.A2A.SEARCH_ACCURATE;
            dataId = agentIdCodecHolder.encode(agentName);
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
     * @param namespaceId   namespaceId of agent
     * @param agentName     agent name
     * @param version       target version of want to query, if is null or empty, get latest version
     * @param registrationType registration type
     * @return agent card detail info
     * @throws NacosApiException nacos api exception
     */
    public AgentCardDetailInfo getAgentCard(String namespaceId, String agentName, String version,
            String registrationType) throws NacosApiException {
        AgentCardVersionInfo agentCardVersionInfo = queryAgentCardVersionInfo(namespaceId, agentName);
        return StringUtils.isEmpty(version) ? queryLatestVersion(agentCardVersionInfo, namespaceId, registrationType)
                : queryTargetVersion(agentCardVersionInfo, version, namespaceId, registrationType);
    }
    
    private AgentCardDetailInfo queryLatestVersion(AgentCardVersionInfo agentCardVersionInfo, String namespaceId,
            String registrationType) throws NacosApiException {
        String latestVersion = agentCardVersionInfo.getVersionDetails().stream().filter(AgentVersionDetail::isLatest)
                .findFirst().orElseThrow(
                        () -> new NacosApiException(NacosException.NOT_FOUND, ErrorCode.AGENT_VERSION_NOT_FOUND,
                                String.format("Agent %s latest version not found", agentCardVersionInfo.getName())))
                .getVersion();
        return queryTargetVersion(agentCardVersionInfo, latestVersion, namespaceId, registrationType);
    }
    
    private AgentCardDetailInfo queryTargetVersion(AgentCardVersionInfo agentCardVersionInfo, String version,
            String namespaceId, String registrationType) throws NacosApiException {
        String versionDataId = agentIdCodecHolder.encode(agentCardVersionInfo.getName()) + "-" + version;
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(versionDataId,
                AGENT_VERSION_GROUP, namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.AGENT_VERSION_NOT_FOUND,
                    String.format("Agent %s version %s not found.", agentCardVersionInfo.getName(), version));
        }
        AgentCardDetailInfo result = JacksonUtils.toObj(response.getContent(), AgentCardDetailInfo.class);
        if (StringUtils.isBlank(registrationType)) {
            registrationType = result.getRegistrationType();
        }
        if (AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE.equalsIgnoreCase(registrationType)) {
            injectEndpoint(result, namespaceId);
        }
        if (agentCardVersionInfo.getLatestPublishedVersion().equals(result.getVersion())) {
            result.setLatestVersion(true);
        }
        return result;
    }
    
    private void injectEndpoint(AgentCardDetailInfo agentCard, String namespaceId) {
        String serviceName = agentIdCodecHolder.encode(agentCard.getName()) + "::" + agentCard.getVersion();
        Service service = Service.newService(namespaceId, Constants.A2A.AGENT_ENDPOINT_GROUP, serviceName);
        ServiceInfo serviceInfo = serviceStorage.getData(service);
        if (serviceInfo.getHosts().isEmpty()) {
            return;
        }
        List<AgentInterface> allAgentEndpoints = serviceInfo.getHosts().stream().map(AgentCardUtil::buildAgentInterface)
                .toList();
        agentCard.setAdditionalInterfaces(allAgentEndpoints);
        List<AgentInterface> matchTransportEndpoints = allAgentEndpoints.stream()
                .filter(agentInterface -> agentInterface.getTransport()
                        .equalsIgnoreCase(agentCard.getPreferredTransport())).toList();
        AgentInterface randomPreferredTransportEndpoint = randomOne(
                matchTransportEndpoints.isEmpty() ? allAgentEndpoints : matchTransportEndpoints);
        agentCard.setUrl(randomPreferredTransportEndpoint.getUrl());
        agentCard.setPreferredTransport(randomPreferredTransportEndpoint.getTransport());
    }
    
    /**
     * TODO abstract a choose policy.
     */
    private AgentInterface randomOne(List<AgentInterface> agentInterfaces) {
        return agentInterfaces.get(ThreadLocalRandom.current().nextInt(agentInterfaces.size()));
    }
    
    private ConfigForm transferVersionInfoToConfigForm(AgentCardVersionInfo agentCardVersionInfo, String namespaceId) {
        ConfigForm configForm = new ConfigForm();
        String actualDataId = agentIdCodecHolder.encode(agentCardVersionInfo.getName());
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
        String actualDataId = agentIdCodecHolder.encode(storageInfo.getName()) + "-" + storageInfo.getVersion();
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
    
    private AgentCardVersionInfo queryAgentCardVersionInfo(String namespaceId, String name) throws NacosApiException {
        // Check if the agent exists
        String actualDataId = agentIdCodecHolder.encode(name);
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(actualDataId,
                AGENT_GROUP, namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.AGENT_NOT_FOUND,
                    "Agent not found: " + name);
        }
        return JacksonUtils.toObj(response.getContent(), AgentCardVersionInfo.class);
    }
}
