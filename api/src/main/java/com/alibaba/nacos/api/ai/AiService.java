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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosSkillListener;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.utils.StringUtils;
import java.util.Collection;

/**
 * AI resource services and compatible legacy operation delegates.
 *
 * @author Nacos
 */
public interface AiService
    extends McpService, A2aService, SkillService, AgentSpecService, PromptService {
    
    /**
     * Return the mcp resource service.
     *
     * @return resource service sharing this AI client lifecycle
     * @since 3.3.0
     */
    default McpService mcp() {
        throw new UnsupportedOperationException("McpService is not implemented by this AiService.");
    }
    
    /**
     * Return the skill resource service.
     *
     * @return resource service sharing this AI client lifecycle
     * @since 3.3.0
     */
    default SkillService skill() {
        throw new UnsupportedOperationException(
            "SkillService is not implemented by this AiService.");
    }
    
    /**
     * Return the agentSpec resource service.
     *
     * @return resource service sharing this AI client lifecycle
     * @since 3.3.0
     */
    default AgentSpecService agentSpec() {
        throw new UnsupportedOperationException(
            "AgentSpecService is not implemented by this AiService.");
    }
    
    /**
     * Return the prompt resource service.
     *
     * @return resource service sharing this AI client lifecycle
     * @since 3.3.0
     */
    default PromptService prompt() {
        throw new UnsupportedOperationException(
            "PromptService is not implemented by this AiService.");
    }
    
    /**
     * Return the agent resource service.
     *
     * @return resource service sharing this AI client lifecycle
     * @since 3.3.0
     */
    default AgentService agent() {
        throw new UnsupportedOperationException(
            "AgentService is not implemented by this AiService.");
    }
    
    /**
     * Get mcp server detail info for the latest published version.
     *
     * @param mcpName name of mcp server
     * @return detail information of MCP server
     * @throws NacosException if request parameter is invalid or mcp server not found or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.0.3")
    default McpServerDetailInfo getMcpServer(String mcpName) throws NacosException {
        return getMcpServer(mcpName, null);
    }
    
    /**
     * Get mcp server detail info.
     *
     * @param mcpName name of MCP name
     * @param version version of MCP, if null, will get the latest published version
     * @return detail information of MCP server
     * @throws NacosException if request parameter is invalid or mcp server not found or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.0.3")
    default McpServerDetailInfo getMcpServer(String mcpName, String version) throws NacosException {
        return mcp().getMcpServer(mcpName, version);
    }
    
    /**
     * Release new mcp server or release new version of exist mcp server request.
     *
     * <p>
     *     If mcp server is not exist, will create an new mcp server with parameter specification.
     *     If mcp server is exist, but version in specification is new one, request will create a new version of mcp server.
     *     If mcp server is exist, and version in specification is exist, request will do nothing.
     * </p>
     *
     * @param serverSpecification mcp server specification
     * @param toolSpecification   mcp server tool specification
     * @return mcp id
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.0.3")
    default String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification)
        throws NacosException {
        return releaseMcpServer(serverSpecification, toolSpecification, (McpEndpointSpec) null);
    }
    
    /**
     * Release one MCP Version with an explicit lifecycle-draft choice.
     *
     * <p>{@code createDraft=false} preserves the historical direct-online behavior.
     * {@code createDraft=true} creates only a standard lifecycle draft.</p>
     *
     * @param serverSpecification MCP Server specification
     * @param toolSpecification optional Tool specification
     * @param createDraft whether to create a lifecycle draft instead of direct-online release
     * @return internal MCP id
     * @throws NacosException when validation or release fails
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.3.0")
    default String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification, boolean createDraft) throws NacosException {
        return releaseMcpServer(serverSpecification, toolSpecification, null, null, createDraft);
    }
    
    /**
     * Release new mcp server or release new version of exist mcp server request.
     *
     * @param serverSpecification mcp server specification
     * @param toolSpecification mcp server tool specification
     * @param resourceSpecification mcp server resource specification
     * @return mcp id
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.1")
    default String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification) throws NacosException {
        return releaseMcpServer(serverSpecification, toolSpecification, resourceSpecification,
            null);
    }
    
    /**
     * Release new mcp server or release new version of exist mcp server request.
     *
     * <p>
     *     If mcp server is not exist, will create an new mcp server with parameter specification.
     *     If mcp server is exist, but version in specification is new one, request will create a new version of mcp server.
     *     If mcp server is exist, and version in specification is exist, request will do nothing.
     * </p>
     *
     * @param serverSpecification mcp server specification
     * @param toolSpecification   mcp server tool specification
     * @param endpointSpecification mcp server endpoint specification, optional, if null, will create ref service auto.
     * @return mcp id
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.0.3")
    default String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        return mcp().releaseMcpServer(serverSpecification, toolSpecification,
            endpointSpecification);
    }
    
    /**
     * Release new mcp server or release new version of exist mcp server request.
     *
     * @param serverSpecification mcp server specification
     * @param toolSpecification mcp server tool specification
     * @param resourceSpecification mcp server resource specification
     * @param endpointSpecification mcp server endpoint specification, optional, if null, will create ref service auto.
     * @return mcp id
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.1")
    default String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification)
        throws NacosException {
        return mcp().releaseMcpServer(serverSpecification, toolSpecification, resourceSpecification,
            endpointSpecification);
    }
    
    /**
     * Release one MCP Version with complete optional content and lifecycle-draft choice.
     *
     * <p>The default preserves compatibility for third-party implementations when
     * {@code createDraft=false}. Implementations that support draft release override this method;
     * an unknown implementation must not silently direct-publish when {@code createDraft=true}.</p>
     *
     * @param serverSpecification MCP Server specification
     * @param toolSpecification optional Tool specification
     * @param resourceSpecification optional Resource specification
     * @param endpointSpecification optional Endpoint specification
     * @param createDraft whether to create a lifecycle draft instead of direct-online release
     * @return internal MCP id
     * @throws NacosException when validation or release fails
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.3.0")
    default String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification,
        boolean createDraft) throws NacosException {
        if (createDraft) {
            throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
                "MCP lifecycle draft release is not implemented by this AiService.");
        }
        return releaseMcpServer(serverSpecification, toolSpecification, resourceSpecification,
            endpointSpecification);
    }
    
    /**
     * Register an endpoint into target mcp server for all version.
     *
     * @param mcpName   name of mcp server
     * @param address   address of endpoint
     * @param port      port of endpoint
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.0.3")
    default void registerMcpServerEndpoint(String mcpName, String address, int port)
        throws NacosException {
        registerMcpServerEndpoint(mcpName, address, port, null);
    }
    
    /**
     * Register an endpoint into target mcp server for target version.
     *
     * @param mcpName   name of mcp server
     * @param address   address of endpoint
     * @param port      port of endpoint
     * @param version   version of mcp server
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.0.3")
    default void registerMcpServerEndpoint(String mcpName, String address, int port, String version)
        throws NacosException {
        mcp().registerMcpServerEndpoint(mcpName, address, port, version);
    }
    
    /**
     * Deregister an endpoint from target mcp server for any version.
     *
     * <p>
     *     The registered endpoint must be registered by this client service.
     *     If the registered endpoint is registered by other client service, the endpoint will fail to deregister.
     * </p>
     *
     * @param mcpName   name of mcp server
     * @param address   address of endpoint
     * @param port      port of endpoint
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.0.3")
    default void deregisterMcpServerEndpoint(String mcpName, String address, int port)
        throws NacosException {
        mcp().deregisterMcpServerEndpoint(mcpName, address, port);
    }
    
    /**
     * Subscribe mcp server.
     *
     * @param mcpName           name of mcp server
     * @param mcpServerListener listener of mcp server, callback when mcp server is changed
     * @return The detail info of mcp server at current time
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.0.3")
    default McpServerDetailInfo subscribeMcpServer(String mcpName,
        AbstractNacosMcpServerListener mcpServerListener)
        throws NacosException {
        return subscribeMcpServer(mcpName, null, mcpServerListener);
    }
    
    /**
     * Subscribe mcp server.
     *
     * @param mcpName           name of mcp server
     * @param version           version of mcp server
     * @param mcpServerListener listener of mcp server, callback when mcp server is changed
     * @return The detail info of mcp server at current time, nullable if agent card not found
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.0.3")
    default McpServerDetailInfo subscribeMcpServer(String mcpName, String version,
        AbstractNacosMcpServerListener mcpServerListener) throws NacosException {
        return mcp().subscribeMcpServer(mcpName, version, mcpServerListener);
    }
    
    /**
     * Un-subscribe mcp server.
     *
     * @param mcpName           name of mcp server
     * @param mcpServerListener listener of mcp server
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.0.3")
    default void unsubscribeMcpServer(String mcpName,
        AbstractNacosMcpServerListener mcpServerListener)
        throws NacosException {
        unsubscribeMcpServer(mcpName, null, mcpServerListener);
    }
    
    /**
     * Un-subscribe mcp server.
     *
     * @param mcpName           name of mcp server
     * @param version           version of mcp server
     * @param mcpServerListener listener of mcp server
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #mcp()} instead.
     */
    @Deprecated
    @Override
    @Since("3.0.3")
    default void unsubscribeMcpServer(String mcpName, String version,
        AbstractNacosMcpServerListener mcpServerListener)
        throws NacosException {
        mcp().unsubscribeMcpServer(mcpName, version, mcpServerListener);
    }
    
    /**
     * Download skill as ZIP byte array by skill name. Defaults to latest version.
     *
     * <p>The ZIP contains the skill directory structure: SKILL.md and all resource files.
     * Binary resources are decoded from Base64 back to raw bytes.</p>
     *
     * @param skillName skill name (unique identifier)
     * @return ZIP file as byte array
     * @throws NacosException if skill not found or query error
     * @deprecated Use {@link #skill()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.0")
    default byte[] downloadSkillZip(String skillName) throws NacosException {
        return skill().downloadSkillZip(skillName);
    }
    
    /**
     * Download skill as ZIP byte array by skill name and target version.
     *
     * @param skillName skill name (unique identifier)
     * @param version   target skill version, if null, will get latest version
     * @return ZIP file as byte array
     * @throws NacosException if skill not found or query error
     * @deprecated Use {@link #skill()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.0")
    default byte[] downloadSkillZipByVersion(String skillName, String version)
        throws NacosException {
        return skill().downloadSkillZipByVersion(skillName, version);
    }
    
    /**
     * Download skill as ZIP byte array by skill name and target label.
     *
     * @param skillName skill name (unique identifier)
     * @param label     target skill label (e.g. "latest", "stable")
     * @return ZIP file as byte array
     * @throws NacosException if skill not found or query error
     * @deprecated Use {@link #skill()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.0")
    default byte[] downloadSkillZipByLabel(String skillName, String label) throws NacosException {
        return skill().downloadSkillZipByLabel(skillName, label);
    }
    
    /**
     * Load agent spec by agent spec name.
     *
     * <p>
     * This method will query the agent spec main configuration and all resource configurations,
     * then assemble them into a complete AgentSpec object.
     * </p>
     *
     * @param agentSpecName agent spec name (unique identifier)
     * @return complete AgentSpec object with all resources
     * @throws NacosException if agent spec not found or query error
     * @deprecated Use {@link #agentSpec()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.0")
    default AgentSpec loadAgentSpec(String agentSpecName) throws NacosException {
        return agentSpec().loadAgentSpec(agentSpecName);
    }
    
    /**
     * Subscribe agent spec.
     *
     * @param agentSpecName       name of agent spec
     * @param agentSpecListener   listener of agent spec, callback when agent spec configuration is changed
     * @return The agent spec object at current time, nullable if agent spec not found
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #agentSpec()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.0")
    default AgentSpec subscribeAgentSpec(String agentSpecName,
        AbstractNacosAgentSpecListener agentSpecListener)
        throws NacosException {
        return agentSpec().subscribeAgentSpec(agentSpecName, agentSpecListener);
    }
    
    /**
     * Un-subscribe agent spec.
     *
     * @param agentSpecName       name of agent spec
     * @param agentSpecListener   listener of agent spec
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #agentSpec()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.0")
    default void unsubscribeAgentSpec(String agentSpecName,
        AbstractNacosAgentSpecListener agentSpecListener)
        throws NacosException {
        agentSpec().unsubscribeAgentSpec(agentSpecName, agentSpecListener);
    }
    
    /**
     * Get prompt by prompt key.
     *
     * @param promptKey prompt key (unique identifier)
     * @return prompt object with current version
     * @throws NacosException if prompt not found or query error
     * @deprecated Use {@link #prompt()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.0")
    default Prompt getPrompt(String promptKey) throws NacosException {
        return prompt().getPrompt(promptKey);
    }
    
    /**
     * Get prompt by prompt key and target version.
     *
     * @param promptKey prompt key (unique identifier)
     * @param version target prompt version, if null, will get latest version
     * @return prompt object with target version
     * @throws NacosException if prompt not found or query error
     * @deprecated Use {@link #prompt()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.0")
    default Prompt getPromptByVersion(String promptKey, String version) throws NacosException {
        return prompt().getPromptByVersion(promptKey, version);
    }
    
    /**
     * Get prompt by prompt key and target label.
     *
     * @param promptKey prompt key (unique identifier)
     * @param label target prompt label
     * @return prompt object with target label
     * @throws NacosException if prompt not found or query error
     * @deprecated Use {@link #prompt()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.0")
    default Prompt getPromptByLabel(String promptKey, String label) throws NacosException {
        return prompt().getPromptByLabel(promptKey, label);
    }
    
    /**
     * Subscribe prompt changes.
     *
     * @param promptKey      prompt key
     * @param version        target prompt version, optional
     * @param label          target prompt label, optional
     * @param promptListener listener for prompt changes
     * @return current prompt object, may be null if prompt not found
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #prompt()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.0")
    default Prompt subscribePrompt(String promptKey, String version, String label,
        AbstractNacosPromptListener promptListener) throws NacosException {
        return prompt().subscribePrompt(promptKey, version, label, promptListener);
    }
    
    /**
     * Un-subscribe prompt changes.
     *
     * @param promptKey      prompt key
     * @param version        target prompt version, optional
     * @param label          target prompt label, optional
     * @param promptListener listener for prompt changes
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #prompt()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.0")
    default void unsubscribePrompt(String promptKey, String version, String label,
        AbstractNacosPromptListener promptListener) throws NacosException {
        prompt().unsubscribePrompt(promptKey, version, label, promptListener);
    }
    
    /**
     * Subscribe skill changes.
     *
     * @param skillName     skill name
     * @param version       target skill version, optional
     * @param label         target skill label, optional
     * @param skillListener listener for skill changes
     * @return current skill ZIP bytes, may be {@code null} when the skill is not found
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #skill()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.2")
    default byte[] subscribeSkill(String skillName, String version, String label,
        AbstractNacosSkillListener skillListener) throws NacosException {
        return skill().subscribeSkill(skillName, version, label, skillListener);
    }
    
    /**
     * Un-subscribe skill changes.
     *
     * @param skillName     skill name
     * @param version       target skill version, optional
     * @param label         target skill label, optional
     * @param skillListener listener previously registered via
     *                      {@link #subscribeSkill(String, String, String, AbstractNacosSkillListener)}
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #skill()} instead.
     */
    @Deprecated
    @Override
    @Since("3.2.2")
    default void unsubscribeSkill(String skillName, String version, String label,
        AbstractNacosSkillListener skillListener) throws NacosException {
        skill().unsubscribeSkill(skillName, version, label, skillListener);
    }
    
    /**
     * Get agent card with nacos extension detail with latest version.
     *
     * @param agentName name of agent card
     * @return agent card with nacos extension detail
     * @throws NacosException if request parameter is invalid or agent card not found or handle error
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default AgentCardDetailInfo getAgentCard(String agentName) throws NacosException {
        return getAgentCard(agentName, StringUtils.EMPTY);
    }
    
    /**
     * Get agent card with nacos extension detail with target version.
     *
     * @param agentName name of agent card
     * @param version   target version, if null or empty, get latest version
     * @return agent card with nacos extension detail
     * @throws NacosException if request parameter is invalid or agent card not found or handle error
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default AgentCardDetailInfo getAgentCard(String agentName, String version)
        throws NacosException {
        return getAgentCard(agentName, version, StringUtils.EMPTY);
    }
    
    /**
     * Get agent card with nacos extension detail with target version.
     *
     * @param agentName        name of agent card
     * @param version          target version, if null or empty, get latest version
     * @param registrationType {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_URL} or
     *                         {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_SERVICE} default is empty, means use agent card
     *                         setting in nacos.
     * @return agent card with nacos extension detail
     * @throws NacosException if request parameter is invalid or agent card not found or handle error
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default AgentCardDetailInfo getAgentCard(String agentName, String version,
        String registrationType)
        throws NacosException {
        return agent().getAgentCard(agentName, version, registrationType);
    }
    
    /**
     * Release new agent card or new version with default service type endpoint.
     *
     * <p>
     * If current agent card and version exist, This API will do nothing. If current agent card exist but version not
     * exist, This API will release new version. If current t agent card not exist, This API will release new agent
     * card.
     * </p>
     *
     * @param agentCard agent card need to release
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void releaseAgentCard(AgentCard agentCard) throws NacosException {
        releaseAgentCard(agentCard, AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE);
    }
    
    /**
     * Release new agent card or new version.
     *
     * <p>
     * If current agent card and version exist, This API will do nothing. If current agent card exist but version not
     * exist, This API will release new version. If current t agent card not exist, This API will release new agent
     * card.
     * </p>
     *
     * @param agentCard        agent card need to release
     * @param registrationType {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_URL} or
     *                         {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_SERVICE}
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void releaseAgentCard(AgentCard agentCard, String registrationType)
        throws NacosException {
        releaseAgentCard(agentCard, registrationType, false);
    }
    
    /**
     * Release new agent card or new version.
     *
     * <p>
     * If current agent card and version exist, This API will do nothing. If current agent card exist but version not
     * exist, This API will release new version. If current t agent card not exist, This API will release new agent
     * card.
     * </p>
     *
     * @param agentCard        agent card need to release
     * @param registrationType {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_URL} or
     *                         {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_SERVICE}
     * @param setAsLatest      whether set new version as latest, default is false. This parameter is only effect when
     *                         new version is released. If current agent card not exist, whatever this parameter is, it
     *                         will be set as latest.
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void releaseAgentCard(AgentCard agentCard, String registrationType, boolean setAsLatest)
        throws NacosException {
        agent().releaseAgentCard(agentCard, registrationType, setAsLatest);
    }
    
    /**
     * Register endpoint to agent card.
     *
     * @param agentName name of agent
     * @param version   version of this endpoint
     * @param address   address for this endpoint
     * @param port      port of this endpoint
     * @throws NacosException if request parameter is invalid or handle error or agent not found
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void registerAgentEndpoint(String agentName, String version, String address, int port)
        throws NacosException {
        registerAgentEndpoint(agentName, version, address, port,
            AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT);
    }
    
    /**
     * Register endpoint to agent card.
     *
     * @param agentName name of agent
     * @param version   version of this endpoint
     * @param address   address for this endpoint
     * @param port      port of this endpoint
     * @param transport supported transport, according to A2A protocol, it should be `JSONRPC`, `GRPC` and `HTTP+JSON`
     * @throws NacosException if request parameter is invalid or handle error or agent not found
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void registerAgentEndpoint(String agentName, String version, String address, int port,
        String transport)
        throws NacosException {
        registerAgentEndpoint(agentName, version, address, port, transport, StringUtils.EMPTY);
    }
    
    /**
     * Register endpoint to agent card.
     *
     * @param agentName name of agent
     * @param version   version of this endpoint
     * @param address   address for this endpoint
     * @param port      port of this endpoint
     * @param transport supported transport, according to A2A protocol, it should be `JSONRPC`, `GRPC` and `HTTP+JSON`
     * @param path      The path of endpoint request
     * @throws NacosException if request parameter is invalid or handle error or agent not found
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void registerAgentEndpoint(String agentName, String version, String address, int port,
        String transport,
        String path) throws NacosException {
        registerAgentEndpoint(agentName, version, address, port, transport, path, false);
    }
    
    /**
     * Register endpoint to agent card.
     *
     * @param agentName  name of agent
     * @param version    version of this endpoint
     * @param address    address for this endpoint
     * @param port       port of this endpoint
     * @param transport  supported transport, according to A2A protocol, it should be `JSONRPC`, `GRPC` and `HTTP+JSON`
     * @param path       The path of endpoint request
     * @param supportTls whether support tls
     * @throws NacosException if request parameter is invalid or handle error or agent not found
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void registerAgentEndpoint(String agentName, String version, String address, int port,
        String transport,
        String path, boolean supportTls) throws NacosException {
        AgentEndpoint agentEndpoint = new AgentEndpoint();
        agentEndpoint.setAddress(address);
        agentEndpoint.setPort(port);
        agentEndpoint.setTransport(transport);
        agentEndpoint.setPath(path);
        agentEndpoint.setSupportTls(supportTls);
        agentEndpoint.setVersion(version);
        registerAgentEndpoint(agentName, agentEndpoint);
    }
    
    /**
     * Register endpoint to agent card.
     *
     * @param agentName name of agent
     * @param endpoint  endpoint info
     * @throws NacosException if request parameter is invalid or handle error or agent not found
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void registerAgentEndpoint(String agentName, AgentEndpoint endpoint)
        throws NacosException {
        agent().registerAgentEndpoint(agentName, endpoint);
    }
    
    /**
     * Batch register endpoints to agent card.
     *
     * <p>
     * Conflict with {@link #registerAgentEndpoint(String, AgentEndpoint)}, this API will overwrite all endpoint
     * registered by {@link #registerAgentEndpoint(String, AgentEndpoint)}.
     * </p>
     *
     * @param agentName name of agent
     * @param endpoints collection of endpoints
     * @throws NacosException if request parameter is invalid or handle error or agent not found
     * @since 3.1.1
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.1")
    default void registerAgentEndpoint(String agentName, Collection<AgentEndpoint> endpoints)
        throws NacosException {
        agent().registerAgentEndpoint(agentName, endpoints);
    }
    
    /**
     * Deregister endpoint from agent card which registered by this client.
     *
     * <p>
     * Only endpoint registered by this client can be deregistered. Other endpoint registered by other clients, call
     * this API will no any effect.
     * </p>
     *
     * @param agentName name of agent
     * @param version   version of this endpoint
     * @param address   address for this endpoint
     * @param port      port of this endpoint
     * @throws NacosException if request parameter is invalid or handle error or agent not found
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void deregisterAgentEndpoint(String agentName, String version, String address, int port)
        throws NacosException {
        AgentEndpoint agentEndpoint = new AgentEndpoint();
        agentEndpoint.setAddress(address);
        agentEndpoint.setPort(port);
        agentEndpoint.setVersion(version);
        deregisterAgentEndpoint(agentName, agentEndpoint);
    }
    
    /**
     * Deregister endpoint from agent card which registered by this client.
     *
     * <p>
     * Only endpoint registered by this client can be deregistered. Other endpoint registered by other clients, call
     * this API will no any effect.
     * </p>
     *
     * @param agentName name of agent
     * @param endpoint  endpoint info
     * @throws NacosException if request parameter is invalid or handle error or agent not found
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void deregisterAgentEndpoint(String agentName, AgentEndpoint endpoint)
        throws NacosException {
        agent().deregisterAgentEndpoint(agentName, endpoint);
    }
    
    /**
     * Subscribe agent card.
     *
     * @param agentName         name of agent
     * @param agentCardListener the callback listener for agent card
     * @return current agent card when subscribe success
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default AgentCardDetailInfo subscribeAgentCard(String agentName,
        AbstractNacosAgentCardListener agentCardListener)
        throws NacosException {
        return subscribeAgentCard(agentName, StringUtils.EMPTY, agentCardListener);
    }
    
    /**
     * Subscribe agent card.
     *
     * @param agentName         name of agent
     * @param version           version of agent, if empty or null, means subscribe latest version
     * @param agentCardListener the callback listener for agent card
     * @return current agent card when subscribe success, nullable if agent card not found
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default AgentCardDetailInfo subscribeAgentCard(String agentName, String version,
        AbstractNacosAgentCardListener agentCardListener) throws NacosException {
        return agent().subscribeAgentCard(agentName, version, agentCardListener);
    }
    
    /**
     * Unsubscribe agent card.
     *
     * @param agentName         name of agent
     * @param agentCardListener the callback listener for agent card
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void unsubscribeAgentCard(String agentName,
        AbstractNacosAgentCardListener agentCardListener)
        throws NacosException {
        unsubscribeAgentCard(agentName, StringUtils.EMPTY, agentCardListener);
    }
    
    /**
     * Unsubscribe agent card.
     *
     * @param agentName         name of agent
     * @param version           version of agent, if empty or null, means unsubscribe latest version
     * @param agentCardListener the callback listener for agent card
     * @throws NacosException if request parameter is invalid or handle error
     * @deprecated Use {@link #agent()} instead.
     */
    @Deprecated
    @Override
    @Since("3.1.0")
    default void unsubscribeAgentCard(String agentName, String version,
        AbstractNacosAgentCardListener agentCardListener)
        throws NacosException {
        agent().unsubscribeAgentCard(agentName, version, agentCardListener);
    }
    
    /**
     * Shutdown the AI service and close resources.
     *
     * @throws NacosException exception.
     */
    @Since("3.0.3")
    void shutdown() throws NacosException;
}
