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

import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosSkillListener;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock Naming Service for test {@link com.alibaba.nacos.api.ai.AiFactory}.
 *
 * @author xiweng.yy
 */
public class NacosAiService implements AiService {
    
    public static final AtomicBoolean IS_THROW_EXCEPTION = new AtomicBoolean(false);
    
    public NacosAiService(Properties properties) throws NacosException {
        if (IS_THROW_EXCEPTION.get()) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "mock exception");
        }
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String mcpName, String version) throws NacosException {
        return null;
    }
    
    @Override
    public String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        return "";
    }
    
    @Override
    public String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification)
        throws NacosException {
        return "";
    }
    
    @Override
    public void registerMcpServerEndpoint(String mcpName, String address, int port, String version)
        throws NacosException {
        
    }
    
    @Override
    public void deregisterMcpServerEndpoint(String mcpName, String address, int port)
        throws NacosException {
        
    }
    
    @Override
    public McpServerDetailInfo subscribeMcpServer(String mcpName, String version,
        AbstractNacosMcpServerListener mcpServerListener) throws NacosException {
        return null;
    }
    
    @Override
    public void unsubscribeMcpServer(String mcpName, String version,
        AbstractNacosMcpServerListener mcpServerListener)
        throws NacosException {
        
    }
    
    @Override
    public void shutdown() throws NacosException {
        
    }
    
    @Override
    public AgentCardDetailInfo getAgentCard(String agentName, String version,
        String registrationType)
        throws NacosException {
        return null;
    }
    
    @Override
    public void releaseAgentCard(AgentCard agentCard, String registrationType, boolean setAsLatest)
        throws NacosException {
        
    }
    
    @Override
    public void registerAgentEndpoint(String agentName, AgentEndpoint endpoint)
        throws NacosException {
        
    }
    
    @Override
    public void registerAgentEndpoint(String agentName, Collection<AgentEndpoint> endpoints)
        throws NacosException {
        
    }
    
    @Override
    public void deregisterAgentEndpoint(String agentName, AgentEndpoint endpoint)
        throws NacosException {
        
    }
    
    @Override
    public AgentCardDetailInfo subscribeAgentCard(String agentName, String version,
        AbstractNacosAgentCardListener agentCardListener) throws NacosException {
        return null;
    }
    
    @Override
    public void unsubscribeAgentCard(String agentName, String version,
        AbstractNacosAgentCardListener agentCardListener)
        throws NacosException {
        
    }
    
    @Override
    public byte[] downloadSkillZip(String skillName) throws NacosException {
        return new byte[0];
    }
    
    @Override
    public byte[] downloadSkillZipByVersion(String skillName, String version)
        throws NacosException {
        return new byte[0];
    }
    
    @Override
    public byte[] downloadSkillZipByLabel(String skillName, String label) throws NacosException {
        return new byte[0];
    }
    
    @Override
    public AgentSpec loadAgentSpec(String agentSpecName) throws NacosException {
        return null;
    }
    
    @Override
    public AgentSpec subscribeAgentSpec(String agentSpecName,
        AbstractNacosAgentSpecListener agentSpecListener)
        throws NacosException {
        return null;
    }
    
    @Override
    public void unsubscribeAgentSpec(String agentSpecName,
        AbstractNacosAgentSpecListener agentSpecListener)
        throws NacosException {
    }
    
    @Override
    public Prompt getPrompt(String promptKey) throws NacosException {
        return null;
    }
    
    @Override
    public Prompt getPromptByVersion(String promptKey, String version) throws NacosException {
        return null;
    }
    
    @Override
    public Prompt getPromptByLabel(String promptKey, String label) throws NacosException {
        return null;
    }
    
    @Override
    public Prompt subscribePrompt(String promptKey, String version, String label,
        AbstractNacosPromptListener promptListener) throws NacosException {
        return null;
    }
    
    @Override
    public void unsubscribePrompt(String promptKey, String version, String label,
        AbstractNacosPromptListener promptListener) throws NacosException {
    }
    
    @Override
    public byte[] subscribeSkill(String skillName, String version, String label,
        AbstractNacosSkillListener skillListener) throws NacosException {
        return new byte[0];
    }
    
    @Override
    public void unsubscribeSkill(String skillName, String version, String label,
        AbstractNacosSkillListener skillListener) throws NacosException {
    }
}
