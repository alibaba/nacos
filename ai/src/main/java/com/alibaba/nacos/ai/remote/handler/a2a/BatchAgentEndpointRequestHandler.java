/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.ai.remote.handler.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.identity.AgentIdCodecHolder;
import com.alibaba.nacos.ai.utils.AgentEndpointUtil;
import com.alibaba.nacos.ai.utils.AgentRequestUtil;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.BatchAgentEndpointRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.naming.BatchRegisterInstanceTraceEvent;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.AgentRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import com.alibaba.nacos.naming.utils.NamingRequestUtil;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Batch Register endpoints for agent to nacos AI module request handler.
 *
 * @author xiweng.yy
 */
@Component
public class BatchAgentEndpointRequestHandler extends RequestHandler<BatchAgentEndpointRequest, AgentEndpointResponse> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchAgentEndpointRequestHandler.class);
    
    private final EphemeralClientOperationServiceImpl clientOperationService;
    
    private final AgentIdCodecHolder agentIdCodecHolder;
    
    public BatchAgentEndpointRequestHandler(EphemeralClientOperationServiceImpl clientOperationService,
            AgentIdCodecHolder agentIdCodecHolder) {
        this.clientOperationService = clientOperationService;
        this.agentIdCodecHolder = agentIdCodecHolder;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = AgentRequestParamExtractor.class)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI)
    public AgentEndpointResponse handle(BatchAgentEndpointRequest request, RequestMeta meta) throws NacosException {
        AgentEndpointResponse response = new AgentEndpointResponse();
        response.setType(AiRemoteConstants.BATCH_REGISTER_ENDPOINT);
        AgentRequestUtil.fillNamespaceId(request);
        try {
            validateRequest(request);
            List<Instance> instances = AgentEndpointUtil.transferToInstances(request.getEndpoints());
            String version = request.getEndpoints().stream().findFirst().get().getVersion();
            String serviceName = agentIdCodecHolder.encode(request.getAgentName()) + "::" + version;
            Service service = Service.newService(request.getNamespaceId(), Constants.A2A.AGENT_ENDPOINT_GROUP,
                    serviceName);
            clientOperationService.batchRegisterInstance(service, instances, meta.getConnectionId());
            publishBatchRegisterInstanceTraceEvent(service, instances, meta);
        } catch (NacosApiException e) {
            response.setErrorInfo(e.getErrCode(), e.getErrMsg());
            LOGGER.error("[{}] Batch Register agent endpoints to agent {} error: {}", meta.getConnectionId(),
                    request.getAgentName(), e.getErrMsg());
        }
        return response;
    }
    
    private void validateRequest(BatchAgentEndpointRequest request) throws NacosApiException {
        if (StringUtils.isBlank(request.getAgentName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `agentName` can't be empty or null");
        }
        if (null == request.getEndpoints() || request.getEndpoints().isEmpty()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `endpoints` can't be empty or null, if want to deregister, please use deregister API.");
        }
        Collection<AgentEndpoint> endpoints = request.getEndpoints();
        Set<String> versions = new HashSet<>();
        for (AgentEndpoint each : endpoints) {
            if (StringUtils.isBlank(each.getVersion())) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                        "Required parameter `endpoint.version` can't be empty or null.");
            }
            versions.add(each.getVersion());
        }
        if (versions.size() > 1) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    String.format("Required parameter `endpoint.version` can't be different, current includes: %s.",
                            String.join(",", versions)));
        }
    }
    
    private void publishBatchRegisterInstanceTraceEvent(Service service, List<Instance> instances, RequestMeta meta) {
        long eventTime = System.currentTimeMillis();
        String clientIp = NamingRequestUtil.getSourceIpForGrpcRequest(meta);
        instances.forEach(instance -> NotifyCenter.publishEvent(
                new BatchRegisterInstanceTraceEvent(eventTime, clientIp, true, service.getNamespace(),
                        service.getGroup(), service.getName(), instance.getIp(), instance.getPort())));
    }
}
