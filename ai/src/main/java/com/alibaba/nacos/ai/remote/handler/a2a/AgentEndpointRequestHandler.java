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

package com.alibaba.nacos.ai.remote.handler.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.identity.AgentIdCodecHolder;
import com.alibaba.nacos.ai.utils.AgentRequestUtil;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.AgentRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Register or Deregister endpoint for agent to nacos AI module request handler.
 *
 * @author xiweng.yy
 */
@Component
public class AgentEndpointRequestHandler extends RequestHandler<AgentEndpointRequest, AgentEndpointResponse> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentEndpointRequestHandler.class);
    
    private final EphemeralClientOperationServiceImpl clientOperationService;
    
    private final AgentIdCodecHolder agentIdCodecHolder;
    
    public AgentEndpointRequestHandler(EphemeralClientOperationServiceImpl clientOperationService,
            AgentIdCodecHolder agentIdCodecHolder) {
        this.clientOperationService = clientOperationService;
        this.agentIdCodecHolder = agentIdCodecHolder;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = AgentRequestParamExtractor.class)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI)
    public AgentEndpointResponse handle(AgentEndpointRequest request, RequestMeta meta) throws NacosException {
        AgentEndpointResponse response = new AgentEndpointResponse();
        response.setType(request.getType());
        AgentRequestUtil.fillNamespaceId(request);
        try {
            validateRequest(request);
            Instance instance = transferInstance(request);
            String serviceName =
                    agentIdCodecHolder.encode(request.getAgentName()) + "::" + request.getEndpoint().getVersion();
            Service service = Service.newService(request.getNamespaceId(), Constants.A2A.AGENT_ENDPOINT_GROUP,
                    serviceName);
            switch (request.getType()) {
                case AiRemoteConstants.REGISTER_ENDPOINT:
                    doRegisterEndpoint(service, instance, meta);
                    break;
                case AiRemoteConstants.DE_REGISTER_ENDPOINT:
                    doDeregisterEndpoint(service, instance, meta);
                    break;
                default:
                    throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                            String.format("parameter `type` should be %s or %s, but was %s",
                                    AiRemoteConstants.REGISTER_ENDPOINT, AiRemoteConstants.DE_REGISTER_ENDPOINT,
                                    request.getType()));
            }
        } catch (NacosApiException e) {
            response.setErrorInfo(e.getErrCode(), e.getErrMsg());
            LOGGER.error("[{}] Register agent endpoint to agent {} error: {}", meta.getConnectionId(),
                    request.getAgentName(), e.getErrMsg());
        }
        return response;
    }
    
    private Instance transferInstance(AgentEndpointRequest request) throws NacosApiException {
        Instance instance = new Instance();
        AgentEndpoint endpoint = request.getEndpoint();
        instance.setIp(endpoint.getAddress());
        instance.setPort(endpoint.getPort());
        String path = StringUtils.isBlank(endpoint.getPath()) ? StringUtils.EMPTY : endpoint.getPath();
        String protocol = StringUtils.isBlank(endpoint.getProtocol()) ? StringUtils.EMPTY : endpoint.getProtocol();
        String query = StringUtils.isBlank(endpoint.getQuery()) ? StringUtils.EMPTY : endpoint.getQuery();
        Map<String, String> metadata = Map.of(Constants.A2A.AGENT_ENDPOINT_PATH_KEY, path,
                Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY, endpoint.getTransport(),
                Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS, String.valueOf(endpoint.isSupportTls()),
                Constants.A2A.NACOS_AGENT_ENDPOINT_PROTOCOL_KEY, protocol,
                Constants.A2A.NACOS_AGENT_ENDPOINT_QUERY_KEY, query);
        instance.setMetadata(metadata);
        instance.validate();
        return instance;
    }
    
    private void validateRequest(AgentEndpointRequest request) throws NacosApiException {
        if (StringUtils.isBlank(request.getAgentName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `agentName` can't be empty or null");
        }
        if (null == request.getEndpoint()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `endpoint` can't be null");
        }
        if (StringUtils.isBlank(request.getEndpoint().getVersion())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `endpoint.version` can't be empty or null");
        }
    }
    
    private void doRegisterEndpoint(Service service, Instance instance, RequestMeta meta) throws NacosException {
        clientOperationService.registerInstance(service, instance, meta.getConnectionId());
        
    }
    
    private void doDeregisterEndpoint(Service service, Instance instance, RequestMeta meta) {
        clientOperationService.deregisterInstance(service, instance, meta.getConnectionId());
    }
}
