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

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosSkillListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.listener.NacosPromptEvent;
import com.alibaba.nacos.api.ai.listener.NacosSkillEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigQueryResult;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.ai.cache.NacosAgentCardCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosPromptCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosSkillCacheHolder;
import com.alibaba.nacos.client.ai.event.AgentCardListenerInvoker;
import com.alibaba.nacos.client.ai.event.AiChangeNotifier;
import com.alibaba.nacos.client.ai.event.McpServerChangedEvent;
import com.alibaba.nacos.client.ai.event.McpServerListenerInvoker;
import com.alibaba.nacos.client.ai.event.PromptChangedEvent;
import com.alibaba.nacos.client.ai.event.PromptListenerInvoker;
import com.alibaba.nacos.client.ai.event.SkillListenerInvoker;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ClientBasicParamUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Nacos AI client service implementation.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosAiService implements AiService {
    
    private static final Logger LOGGER = LogUtils.logger(NacosAiService.class);
    
    /**
     * Fixed group for all prompt configurations.
     */
    private static final String PROMPT_GROUP = "nacos-ai-prompt";
    
    /**
     * CAS retry count for prompt publish.
     */
    private static final int CAS_RETRY_COUNT = 3;
    
    /**
     * Version format pattern: major.minor.patch.
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    
    private final String namespaceId;
    
    private final AiGrpcClient grpcClient;
    
    private final NacosMcpServerCacheHolder mcpServerCacheHolder;
    
    private final NacosAgentCardCacheHolder agentCardCacheHolder;
    
    private final NacosSkillCacheHolder skillCacheHolder;
    
    private final NacosPromptCacheHolder promptCacheHolder;
    
    private final AiChangeNotifier aiChangeNotifier;
    
    private final ConfigService configService;
    
    public NacosAiService(Properties properties) throws NacosException {
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        LOGGER.info(ClientBasicParamUtil.getInputParameters(clientProperties.asProperties()));
        this.namespaceId = initNamespace(clientProperties);
        // Create ConfigService instance for Skill operations
        this.configService = NacosFactory.createConfigService(clientProperties.asProperties());
        this.grpcClient = new AiGrpcClient(namespaceId, clientProperties);
        this.mcpServerCacheHolder = new NacosMcpServerCacheHolder(grpcClient, clientProperties);
        this.agentCardCacheHolder = new NacosAgentCardCacheHolder(grpcClient, clientProperties);
        this.skillCacheHolder = new NacosSkillCacheHolder(configService, this.namespaceId);
        this.promptCacheHolder = new NacosPromptCacheHolder(configService, this.namespaceId);
        this.aiChangeNotifier = new AiChangeNotifier();
        start();
    }
    
    private String initNamespace(NacosClientProperties properties) {
        String tempNamespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        if (StringUtils.isBlank(tempNamespace)) {
            return Constants.DEFAULT_NAMESPACE_ID;
        }
        return tempNamespace;
    }
    
    private void start() throws NacosException {
        this.grpcClient.start(this.mcpServerCacheHolder, this.agentCardCacheHolder);
        NotifyCenter.registerToPublisher(McpServerChangedEvent.class, 16384);
        NotifyCenter.registerToPublisher(PromptChangedEvent.class, 16384);
        NotifyCenter.registerSubscriber(this.aiChangeNotifier);
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String mcpName, String version) throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `mcpName` not present");
        }
        return grpcClient.queryMcpServer(mcpName, version);
    }
    
    @Override
    public String releaseMcpServer(McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
            McpEndpointSpec endpointSpecification) throws NacosException {
        if (null == serverSpecification) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification` not present");
        }
        if (StringUtils.isBlank(serverSpecification.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification.name` not present");
        }
        if (null == serverSpecification.getVersionDetail() || StringUtils.isBlank(
                serverSpecification.getVersionDetail().getVersion())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification.versionDetail.version` not present");
        }
        return grpcClient.releaseMcpServer(serverSpecification, toolSpecification, endpointSpecification);
    }
    
    @Override
    public void registerMcpServerEndpoint(String mcpName, String address, int port, String version)
            throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        Instance instance = new Instance();
        instance.setIp(address);
        instance.setPort(port);
        instance.validate();
        grpcClient.registerMcpServerEndpoint(mcpName, address, port, version);
    }
    
    @Override
    public void deregisterMcpServerEndpoint(String mcpName, String address, int port) throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        Instance instance = new Instance();
        instance.setIp(address);
        instance.setPort(port);
        instance.validate();
        grpcClient.deregisterMcpServerEndpoint(mcpName, address, port);
    }
    
    @Override
    public McpServerDetailInfo subscribeMcpServer(String mcpName, String version,
            AbstractNacosMcpServerListener mcpServerListener) throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        if (null == mcpServerListener) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpServerListener` can't be empty or null");
        }
        McpServerListenerInvoker listenerInvoker = new McpServerListenerInvoker(mcpServerListener);
        aiChangeNotifier.registerListener(mcpName, version, listenerInvoker);
        McpServerDetailInfo result = grpcClient.subscribeMcpServer(mcpName, version);
        if (null != result && !listenerInvoker.isInvoked()) {
            listenerInvoker.invoke(new NacosMcpServerEvent(result));
        }
        return result;
    }
    
    @Override
    public void unsubscribeMcpServer(String mcpName, String version, AbstractNacosMcpServerListener mcpServerListener)
            throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        if (null == mcpServerListener) {
            return;
        }
        McpServerListenerInvoker listenerInvoker = new McpServerListenerInvoker(mcpServerListener);
        aiChangeNotifier.deregisterListener(mcpName, version, listenerInvoker);
        if (!aiChangeNotifier.isMcpServerSubscribed(mcpName, version)) {
            grpcClient.unsubscribeMcpServer(mcpName, version);
        }
    }
    
    @Override
    public AgentCardDetailInfo getAgentCard(String agentName, String version, String registrationType)
            throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        return grpcClient.getAgentCard(agentName, version, registrationType);
    }
    
    @Override
    public void releaseAgentCard(AgentCard agentCard, String registrationType, boolean setAsLatest)
            throws NacosException {
        if (null == agentCard) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentCard` can't be null");
        }
        validateAgentCardField("name", agentCard.getName());
        validateAgentCardField("version", agentCard.getVersion());
        validateAgentCardField("protocolVersion", agentCard.getProtocolVersion());
        if (StringUtils.isBlank(registrationType)) {
            registrationType = AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE;
        }
        grpcClient.releaseAgentCard(agentCard, registrationType, setAsLatest);
    }
    
    @Override
    public void registerAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        validateAgentEndpoint(endpoint);
        grpcClient.registerAgentEndpoint(agentName, endpoint);
    }
    
    @Override
    public void registerAgentEndpoint(String agentName, Collection<AgentEndpoint> endpoints) throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        validateAgentEndpoint(endpoints);
        grpcClient.registerAgentEndpoints(agentName, endpoints);
    }
    
    @Override
    public void deregisterAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        validateAgentEndpoint(endpoint);
        grpcClient.deregisterAgentEndpoint(agentName, endpoint);
    }
    
    @Override
    public AgentCardDetailInfo subscribeAgentCard(String agentName, String version,
            AbstractNacosAgentCardListener agentCardListener) throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        if (null == agentCardListener) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentCardListener` can't be empty or null");
        }
        AgentCardListenerInvoker listenerInvoker = new AgentCardListenerInvoker(agentCardListener);
        aiChangeNotifier.registerListener(agentName, version, listenerInvoker);
        AgentCardDetailInfo result = grpcClient.subscribeAgentCard(agentName, version);
        if (null != result && !listenerInvoker.isInvoked()) {
            listenerInvoker.invoke(new NacosAgentCardEvent(result));
        }
        return result;
    }
    
    @Override
    public void unsubscribeAgentCard(String agentName, String version, AbstractNacosAgentCardListener agentCardListener)
            throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        if (null == agentCardListener) {
            return;
        }
        AgentCardListenerInvoker listenerInvoker = new AgentCardListenerInvoker(agentCardListener);
        aiChangeNotifier.deregisterListener(agentName, version, listenerInvoker);
        if (!aiChangeNotifier.isAgentCardSubscribed(agentName, version)) {
            grpcClient.unsubscribeAgentCard(agentName, version);
        }
    }
    
    private void validateAgentEndpoint(Collection<AgentEndpoint> endpoints) throws NacosApiException {
        if (null == endpoints || endpoints.isEmpty()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `endpoints` can't be empty or null, if want to deregister endpoints, please use deregister API.");
        }
        Set<String> versions = new HashSet<>();
        for (AgentEndpoint endpoint : endpoints) {
            validateAgentEndpoint(endpoint);
            versions.add(endpoint.getVersion());
        }
        if (versions.size() > 1) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    String.format("Required parameter `endpoint.version` can't be different, current includes: %s.",
                            String.join(",", versions)));
        }
    }
    
    private void validateAgentEndpoint(AgentEndpoint endpoint) throws NacosApiException {
        if (null == endpoint) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `endpoint` can't be null");
        }
        if (StringUtils.isBlank(endpoint.getVersion())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `endpoint.version` can't be empty or null");
        }
        Instance instance = new Instance();
        instance.setIp(endpoint.getAddress());
        instance.setPort(endpoint.getPort());
        instance.validate();
    }
    
    private static void validateAgentCardField(String fieldName, String fieldValue) throws NacosApiException {
        if (StringUtils.isEmpty(fieldValue)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `agentCard." + fieldName + "` not present");
        }
    }
    
    @Override
    public Skill loadSkill(String skillName) throws NacosException {
        if (StringUtils.isBlank(skillName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `skillName` not present");
        }
        
        // Build main config info
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        
        // Query main config (skill.json)
        String mainConfigContent;
        try {
            mainConfigContent = configService.getConfig(mainConfigInfo.getDataId(), mainConfigInfo.getGroup(), 3000);
        } catch (NacosException e) {
            throw new NacosException(NacosException.NOT_FOUND,
                    "Skill main configuration not found for skillName: " + skillName + ", error: " + e.getMessage());
        }
        
        if (StringUtils.isBlank(mainConfigContent)) {
            throw new NacosException(NacosException.NOT_FOUND,
                    "Skill main configuration not found for skillName: " + skillName);
        }
        
        // Parse main config
        SkillMainConfig mainConfig;
        try {
            mainConfig = JacksonUtils.toObj(mainConfigContent, SkillMainConfig.class);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                    "Failed to parse  skill main configuration: " + e.getMessage(), e);
        }
        
        // Build Skill object
        Skill skill = new Skill();
        skill.setNamespaceId(this.namespaceId);
        skill.setName(mainConfig.getName());
        skill.setDescription(mainConfig.getDescription());
        skill.setInstruction(mainConfig.getInstruction());
        
        // Query all Resource configs
        Map<String, SkillResource> resourceMap = new HashMap<>(
                mainConfig.getResources() != null ? mainConfig.getResources().size() : 16);
        if (mainConfig.getResources() != null && !mainConfig.getResources().isEmpty()) {
            for (SkillResourceRef resourceRef : mainConfig.getResources()) {
                // Generate resourceId from type and name
                String resourceId = SkillUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
                
                // Query resource config using resourceRef info
                SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                        skillName, resourceRef.getType(), resourceRef.getName());
                String resourceContent;
                try {
                    resourceContent = configService.getConfig(resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), 3000);
                } catch (NacosException e) {
                    LOGGER.warn("Resource configuration not found: dataId={}, group={}, error={}",
                            resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), e.getMessage());
                    continue;
                }
                
                if (StringUtils.isNotBlank(resourceContent)) {
                    try {
                        SkillResource resource = JacksonUtils.toObj(resourceContent, SkillResource.class);
                        // Use resource name as key (from resource object, not resourceId)
                        resourceMap.put(resource.getName() != null ? resource.getName() : resourceId, resource);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse resource configuration: dataId={}, group={}, error={}",
                                resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), e.getMessage());
                    }
                }
            }
        }
        skill.setResource(resourceMap);
        
        return skill;
    }
    
    /**
     * Skill main config (from skill.json).
     */
    private static class SkillMainConfig {
        private String name;
        private String description;
        private String instruction;
        private List<SkillResourceRef> resources;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getInstruction() {
            return instruction;
        }
        
        public void setInstruction(String instruction) {
            this.instruction = instruction;
        }
        
        public List<SkillResourceRef> getResources() {
            return resources;
        }
        
        public void setResources(List<SkillResourceRef> resources) {
            this.resources = resources;
        }
    }
    
    /**
     * Skill resource reference (in skill.json).
     */
    private static class SkillResourceRef {
        private String name;
        private String type;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
    }
    
    @Override
    public Skill subscribeSkill(String skillName, AbstractNacosSkillListener skillListener) throws NacosException {
        if (StringUtils.isBlank(skillName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `skillName` can't be empty or null");
        }
        if (null == skillListener) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `skillListener` can't be empty or null");
        }
        
        SkillListenerInvoker listenerInvoker = new SkillListenerInvoker(skillListener);
        aiChangeNotifier.registerListener(skillName, listenerInvoker);
        Skill result = skillCacheHolder.subscribeSkill(skillName);
        if (null != result && !listenerInvoker.isInvoked()) {
            listenerInvoker.invoke(new NacosSkillEvent(skillName, result));
        }
        return result;
    }
    
    @Override
    public void unsubscribeSkill(String skillName, AbstractNacosSkillListener skillListener) throws NacosException {
        if (StringUtils.isBlank(skillName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `skillName` can't be empty or null");
        }
        if (null == skillListener) {
            return;
        }
        SkillListenerInvoker listenerInvoker = new SkillListenerInvoker(skillListener);
        aiChangeNotifier.deregisterListener(skillName, listenerInvoker);
        if (!aiChangeNotifier.isSkillSubscribed(skillName)) {
            skillCacheHolder.unsubscribeSkill(skillName);
        }
    }
    
    // ==================== Prompt Methods ====================
    
    @Override
    public Prompt getPrompt(String promptKey) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `promptKey` can't be empty or null");
        }
        return promptCacheHolder.getPrompt(promptKey);
    }
    
    @Override
    public boolean publishPrompt(Prompt prompt) throws NacosException {
        if (prompt == null) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `prompt` can't be null");
        }
        if (StringUtils.isBlank(prompt.getPromptKey())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `prompt.promptKey` can't be empty or null");
        }
        if (StringUtils.isBlank(prompt.getVersion())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `prompt.version` can't be empty or null");
        }
        
        // 1. Validate version format (must be major.minor.patch)
        validateVersionFormat(prompt.getVersion());
        
        String dataId = prompt.getPromptKey() + ".json";
        
        for (int retry = 0; retry < CAS_RETRY_COUNT; retry++) {
            // 2. Get current prompt with MD5 (for version check and CAS)
            ConfigQueryResult queryResult = configService.getConfigWithResult(dataId, PROMPT_GROUP, 3000);
            String currentContent = queryResult.getContent();
            String currentMd5 = queryResult.getMd5();
            
            // 3. Version validation - new version must be greater than current
            if (StringUtils.isNotBlank(currentContent)) {
                Prompt currentPrompt = parsePromptFromContent(prompt.getPromptKey(), currentContent);
                if (currentPrompt != null && StringUtils.isNotBlank(currentPrompt.getVersion())) {
                    if (!isVersionGreater(prompt.getVersion(), currentPrompt.getVersion())) {
                        throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                                "New version " + prompt.getVersion() + " must be greater than current version " 
                                        + currentPrompt.getVersion());
                    }
                }
            }
            
            // 4. Build new content
            Map<String, Object> contentMap = new HashMap<>(4);
            contentMap.put("promptKey", prompt.getPromptKey());
            contentMap.put("version", prompt.getVersion());
            if (StringUtils.isNotBlank(prompt.getTemplate())) {
                contentMap.put("template", prompt.getTemplate());
            }
            if (StringUtils.isNotBlank(prompt.getCommitMsg())) {
                contentMap.put("commitMsg", prompt.getCommitMsg());
            }
            String newContent = JacksonUtils.toJson(contentMap);
            
            // 5. Publish with CAS
            try {
                boolean success;
                if (currentMd5 != null) {
                    // CAS publish with MD5 check
                    success = configService.publishConfigCas(dataId, PROMPT_GROUP, newContent, currentMd5, "json");
                } else {
                    // First publish (no existing config)
                    success = configService.publishConfig(dataId, PROMPT_GROUP, newContent, "json");
                }
                
                if (success) {
                    LOGGER.info("[publishPrompt] Successfully published prompt: promptKey={}, version={}", 
                            prompt.getPromptKey(), prompt.getVersion());
                    return true;
                }
            } catch (NacosException e) {
                LOGGER.warn("[publishPrompt] Publish failed, retry {}/{}, error: {}", 
                        retry + 1, CAS_RETRY_COUNT, e.getMessage());
                // Continue to retry
            }
            
            // CAS conflict or other failure, retry
            LOGGER.warn("[publishPrompt] CAS conflict for prompt {}, retry {}/{}", 
                    prompt.getPromptKey(), retry + 1, CAS_RETRY_COUNT);
            
            // Exponential backoff before retry
            try {
                Thread.sleep(100L * (1L << retry));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                        "Interrupted while retrying publish");
            }
        }
        
        throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "Failed to publish prompt after " + CAS_RETRY_COUNT + " retries due to CAS conflict");
    }
    
    /**
     * Validate version format (must be major.minor.patch).
     */
    private void validateVersionFormat(String version) throws NacosException {
        if (!VERSION_PATTERN.matcher(version).matches()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Version must be in format major.minor.patch (e.g., 1.0.0), got: " + version);
        }
    }
    
    /**
     * Check if newVersion is greater than currentVersion.
     */
    private boolean isVersionGreater(String newVersion, String currentVersion) {
        String[] newParts = newVersion.split("\\.");
        String[] currentParts = currentVersion.split("\\.");
        
        for (int i = 0; i < 3; i++) {
            int newPart = Integer.parseInt(newParts[i]);
            int currentPart = Integer.parseInt(currentParts[i]);
            if (newPart > currentPart) {
                return true;
            } else if (newPart < currentPart) {
                return false;
            }
        }
        return false;
    }
    
    /**
     * Parse Prompt from JSON content.
     */
    private Prompt parsePromptFromContent(String promptKey, String content) {
        try {
            return promptCacheHolder.parsePromptContent(promptKey, content);
        } catch (Exception e) {
            LOGGER.warn("[publishPrompt] Failed to parse current prompt content: {}", e.getMessage());
            return null;
        }
    }
    
    @Override
    public Prompt subscribePrompt(String promptKey, AbstractNacosPromptListener promptListener) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `promptKey` can't be empty or null");
        }
        if (null == promptListener) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `promptListener` can't be null");
        }
        
        PromptListenerInvoker listenerInvoker = new PromptListenerInvoker(promptListener);
        aiChangeNotifier.registerListener(promptKey, listenerInvoker);
        Prompt result = promptCacheHolder.subscribePrompt(promptKey);
        if (null != result && !listenerInvoker.isInvoked()) {
            listenerInvoker.invoke(new NacosPromptEvent(promptKey, result));
        }
        return result;
    }
    
    @Override
    public void unsubscribePrompt(String promptKey, AbstractNacosPromptListener promptListener) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `promptKey` can't be empty or null");
        }
        if (null == promptListener) {
            return;
        }
        PromptListenerInvoker listenerInvoker = new PromptListenerInvoker(promptListener);
        aiChangeNotifier.deregisterListener(promptKey, listenerInvoker);
        if (!aiChangeNotifier.isPromptSubscribed(promptKey)) {
            promptCacheHolder.unsubscribePrompt(promptKey);
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        this.grpcClient.shutdown();
        this.mcpServerCacheHolder.shutdown();
        this.skillCacheHolder.shutdown();
        this.promptCacheHolder.shutdown();
        // ConfigService will be closed automatically when client shuts down
    }
}
