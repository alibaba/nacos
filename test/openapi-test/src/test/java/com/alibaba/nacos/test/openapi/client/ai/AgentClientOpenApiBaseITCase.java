/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.openapi.client.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.core5.http.ClassicHttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Shared helpers for Agent Client OpenAPI integration tests.
 *
 * @author xiweng.yy
 */
public abstract class AgentClientOpenApiBaseITCase extends AiAdminApiBaseITCase {
    
    protected static final String AGENT_CLIENT_PATH = nacosPath(Constants.Agent.CLIENT_PATH);
    
    protected static final String AGENT_SEARCH_PATH = AGENT_CLIENT_PATH + "/search";
    
    protected static final String AGENT_ENDPOINT_PATH = AGENT_CLIENT_PATH + "/endpoints";
    
    protected static final String AGENT_ENDPOINT_HEARTBEAT_PATH =
            AGENT_ENDPOINT_PATH + "/heartbeat";
    
    protected void publishAgent(String agentName, String version) throws Exception {
        postFormOk(ADMIN_AGENT_PATH + "/draft",
                agentForm(agentInitialDraftRequest(null, agentName, version)));
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, agentName));
        postFormOk(ADMIN_AGENT_PATH + "/force-publish",
                agentForm(agentVersionCommand(null, agentName, version)));
    }
    
    protected String randomHttpClientId() {
        return "http-openapi-it-" + UUID.randomUUID();
    }
    
    protected HttpResponse getWithClientId(String path, Query query, String clientId)
            throws Exception {
        HttpGet request = new HttpGet(requestUrl(path + "?" + query.toQueryUrl()));
        addClientId(request, clientId);
        return executeRaw(request);
    }
    
    protected HttpResponse postEndpointForm(String clientId, String requestModule,
            Map<String, String> form) throws Exception {
        HttpPost request = new HttpPost(requestUrl(AGENT_ENDPOINT_PATH));
        addStatefulHeaders(request, clientId, requestModule);
        HttpUtils.initRequestFromEntity(request, form, StandardCharsets.UTF_8.name());
        return executeRaw(request);
    }
    
    protected HttpResponse deleteEndpointForm(String clientId, String requestModule, Query form)
            throws Exception {
        HttpDelete request =
                new HttpDelete(requestUrl(AGENT_ENDPOINT_PATH + "?" + form.toQueryUrl()));
        addStatefulHeaders(request, clientId, requestModule);
        return executeRaw(request);
    }
    
    protected HttpResponse heartbeat(String clientId, String requestModule) throws Exception {
        HttpPut request = new HttpPut(requestUrl(AGENT_ENDPOINT_HEARTBEAT_PATH));
        addStatefulHeaders(request, clientId, requestModule);
        return executeRaw(request);
    }
    
    private void addStatefulHeaders(ClassicHttpRequest request, String clientId,
            String requestModule) {
        addClientId(request, clientId);
        if (null != requestModule) {
            request.setHeader(HttpHeaderConsts.REQUEST_MODULE, requestModule);
        }
    }
    
    private void addClientId(ClassicHttpRequest request, String clientId) {
        if (null != clientId) {
            request.setHeader(ClientConstants.HTTP_CLIENT_ID_HEADER, clientId);
        }
    }
}
