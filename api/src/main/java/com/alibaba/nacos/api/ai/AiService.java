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

/**
 * Nacos AI client service interface.
 *
 * @author xiweng.yy
 */
public interface AiService extends A2aService {
    
    /**
     * Get mcp server detail info for latest version.
     *
     * @param mcpName name of mcp server
     * @return detail information of MCP server
     * @throws NacosException if request parameter is invalid or mcp server not found or handle error
     */
    @Since("3.0.3")
    default McpServerDetailInfo getMcpServer(String mcpName) throws NacosException {
        return getMcpServer(mcpName, null);
    }
    
    /**
     * Get mcp server detail info.
     *
     * @param mcpName name of MCP name
     * @param version version of MCP, if null, will get the latest version
     * @return detail information of MCP server
     * @throws NacosException if request parameter is invalid or mcp server not found or handle error
     */
    @Since("3.0.3")
    McpServerDetailInfo getMcpServer(String mcpName, String version) throws NacosException;
    
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
     */
    @Since("3.0.3")
    default String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification)
        throws NacosException {
        return releaseMcpServer(serverSpecification, toolSpecification, (McpEndpointSpec) null);
    }
    
    /**
     * Release new mcp server or release new version of exist mcp server request.
     *
     * @param serverSpecification mcp server specification
     * @param toolSpecification mcp server tool specification
     * @param resourceSpecification mcp server resource specification
     * @return mcp id
     * @throws NacosException if request parameter is invalid or handle error
     */
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
     */
    @Since("3.0.3")
    String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException;
    
    /**
     * Release new mcp server or release new version of exist mcp server request.
     *
     * @param serverSpecification mcp server specification
     * @param toolSpecification mcp server tool specification
     * @param resourceSpecification mcp server resource specification
     * @param endpointSpecification mcp server endpoint specification, optional, if null, will create ref service auto.
     * @return mcp id
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.1")
    String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification)
        throws NacosException;
    
    /**
     * Register an endpoint into target mcp server for all version.
     *
     * @param mcpName   name of mcp server
     * @param address   address of endpoint
     * @param port      port of endpoint
     * @throws NacosException if request parameter is invalid or handle error
     */
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
     */
    @Since("3.0.3")
    void registerMcpServerEndpoint(String mcpName, String address, int port, String version)
        throws NacosException;
    
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
     */
    @Since("3.0.3")
    void deregisterMcpServerEndpoint(String mcpName, String address, int port)
        throws NacosException;
    
    /**
     * Subscribe mcp server.
     *
     * @param mcpName           name of mcp server
     * @param mcpServerListener listener of mcp server, callback when mcp server is changed
     * @return The detail info of mcp server at current time
     * @throws NacosException if request parameter is invalid or handle error
     */
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
     */
    @Since("3.0.3")
    McpServerDetailInfo subscribeMcpServer(String mcpName, String version,
        AbstractNacosMcpServerListener mcpServerListener) throws NacosException;
    
    /**
     * Un-subscribe mcp server.
     *
     * @param mcpName           name of mcp server
     * @param mcpServerListener listener of mcp server
     * @throws NacosException if request parameter is invalid or handle error
     */
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
     */
    @Since("3.0.3")
    void unsubscribeMcpServer(String mcpName, String version,
        AbstractNacosMcpServerListener mcpServerListener)
        throws NacosException;
    
    /**
     * Download skill as ZIP byte array by skill name. Defaults to latest version.
     *
     * <p>The ZIP contains the skill directory structure: SKILL.md and all resource files.
     * Binary resources are decoded from Base64 back to raw bytes.</p>
     *
     * @param skillName skill name (unique identifier)
     * @return ZIP file as byte array
     * @throws NacosException if skill not found or query error
     */
    @Since("3.2.0")
    byte[] downloadSkillZip(String skillName) throws NacosException;
    
    /**
     * Download skill as ZIP byte array by skill name and target version.
     *
     * @param skillName skill name (unique identifier)
     * @param version   target skill version, if null, will get latest version
     * @return ZIP file as byte array
     * @throws NacosException if skill not found or query error
     */
    @Since("3.2.0")
    byte[] downloadSkillZipByVersion(String skillName, String version) throws NacosException;
    
    /**
     * Download skill as ZIP byte array by skill name and target label.
     *
     * @param skillName skill name (unique identifier)
     * @param label     target skill label (e.g. "latest", "stable")
     * @return ZIP file as byte array
     * @throws NacosException if skill not found or query error
     */
    @Since("3.2.0")
    byte[] downloadSkillZipByLabel(String skillName, String label) throws NacosException;
    
    // ==================== AgentSpec Management APIs ====================
    
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
     */
    @Since("3.2.0")
    AgentSpec loadAgentSpec(String agentSpecName) throws NacosException;
    
    /**
     * Subscribe agent spec.
     *
     * @param agentSpecName       name of agent spec
     * @param agentSpecListener   listener of agent spec, callback when agent spec configuration is changed
     * @return The agent spec object at current time, nullable if agent spec not found
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.0")
    AgentSpec subscribeAgentSpec(String agentSpecName,
        AbstractNacosAgentSpecListener agentSpecListener)
        throws NacosException;
    
    /**
     * Un-subscribe agent spec.
     *
     * @param agentSpecName       name of agent spec
     * @param agentSpecListener   listener of agent spec
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.0")
    void unsubscribeAgentSpec(String agentSpecName,
        AbstractNacosAgentSpecListener agentSpecListener)
        throws NacosException;
    
    // ==================== Prompt Management APIs ====================
    
    /**
     * Get prompt by prompt key.
     *
     * @param promptKey prompt key (unique identifier)
     * @return prompt object with current version
     * @throws NacosException if prompt not found or query error
     */
    @Since("3.2.0")
    Prompt getPrompt(String promptKey) throws NacosException;
    
    /**
     * Get prompt by prompt key and target version.
     *
     * @param promptKey prompt key (unique identifier)
     * @param version target prompt version, if null, will get latest version
     * @return prompt object with target version
     * @throws NacosException if prompt not found or query error
     */
    @Since("3.2.0")
    Prompt getPromptByVersion(String promptKey, String version) throws NacosException;
    
    /**
     * Get prompt by prompt key and target label.
     *
     * @param promptKey prompt key (unique identifier)
     * @param label target prompt label
     * @return prompt object with target label
     * @throws NacosException if prompt not found or query error
     */
    @Since("3.2.0")
    Prompt getPromptByLabel(String promptKey, String label) throws NacosException;
    
    /**
     * Subscribe prompt changes.
     *
     * @param promptKey      prompt key
     * @param version        target prompt version, optional
     * @param label          target prompt label, optional
     * @param promptListener listener for prompt changes
     * @return current prompt object, may be null if prompt not found
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.0")
    Prompt subscribePrompt(String promptKey, String version, String label,
        AbstractNacosPromptListener promptListener) throws NacosException;
    
    /**
     * Un-subscribe prompt changes.
     *
     * @param promptKey      prompt key
     * @param version        target prompt version, optional
     * @param label          target prompt label, optional
     * @param promptListener listener for prompt changes
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.0")
    void unsubscribePrompt(String promptKey, String version, String label,
        AbstractNacosPromptListener promptListener) throws NacosException;
    
    /**
     * Subscribe skill changes.
     *
     * @param skillName     skill name
     * @param version       target skill version, optional
     * @param label         target skill label, optional
     * @param skillListener listener for skill changes
     * @return current skill ZIP bytes, may be {@code null} when the skill is not found
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.2")
    byte[] subscribeSkill(String skillName, String version, String label,
        AbstractNacosSkillListener skillListener) throws NacosException;
    
    /**
     * Un-subscribe skill changes.
     *
     * @param skillName     skill name
     * @param version       target skill version, optional
     * @param label         target skill label, optional
     * @param skillListener listener previously registered via
     *                      {@link #subscribeSkill(String, String, String, AbstractNacosSkillListener)}
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.2")
    void unsubscribeSkill(String skillName, String version, String label,
        AbstractNacosSkillListener skillListener) throws NacosException;
    
    /**
     * Shutdown the AI service and close resources.
     *
     * @throws NacosException exception.
     */
    @Since("3.0.3")
    void shutdown() throws NacosException;
    
}
