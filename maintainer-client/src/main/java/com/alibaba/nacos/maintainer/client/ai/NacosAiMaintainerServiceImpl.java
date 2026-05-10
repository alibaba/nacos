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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;

/**
 * Nacos AI module maintainer service implementation.
 *
 * @author xiweng.yy
 */
public class NacosAiMaintainerServiceImpl implements AiMaintainerService {
    
    private final SkillMaintainerService skillMaintainerService;
    
    private final AgentSpecMaintainerService agentSpecMaintainerService;
    
    private final McpMaintainerService mcpMaintainerService;
    
    private final A2aMaintainerService a2aMaintainerService;
    
    private final PromptMaintainerService promptMaintainerService;
    
    private final PipelineMaintainerService pipelineMaintainerService;
    
    public NacosAiMaintainerServiceImpl(Properties properties) throws NacosException {
        AiMaintainerHttpContext context = new AiMaintainerHttpContext(properties);
        SkillMaintainerService skillDelegate = new SkillMaintainerServiceImpl(context);
        AgentSpecMaintainerService agentSpecDelegate = new AgentSpecMaintainerServiceImpl(context);
        this.mcpMaintainerService = new McpMaintainerServiceImpl(context);
        this.a2aMaintainerService = new A2aMaintainerServiceImpl(context);
        this.promptMaintainerService = new PromptMaintainerServiceImpl(context);
        this.pipelineMaintainerService = new PipelineMaintainerServiceImpl(context);
        this.skillMaintainerService = skillDelegate;
        this.agentSpecMaintainerService = agentSpecDelegate;
    }
    
    @Override
    public SkillMaintainerService skill() {
        return skillMaintainerService;
    }
    
    @Override
    public AgentSpecMaintainerService agentSpec() {
        return agentSpecMaintainerService;
    }
    
    @Override
    public McpMaintainerService mcp() {
        return mcpMaintainerService;
    }
    
    @Override
    public A2aMaintainerService a2a() {
        return a2aMaintainerService;
    }
    
    @Override
    public PromptMaintainerService prompt() {
        return promptMaintainerService;
    }
    
    @Override
    public PipelineMaintainerService pipeline() {
        return pipelineMaintainerService;
    }
}
