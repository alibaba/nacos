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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.tls.TlsSystemConfig;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.client.constant.Constants.Security.SECURITY_INFO_REFRESH_INTERVAL_MILLS;
import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTPS_PREFIX;
import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;

/**
 * AI HTTP client proxy for AI operations over HTTP transport.
 *
 * <p>Provides HTTP-based implementation of {@link AiClientProxy}, enabling AI operations
 * to go through HTTP instead of gRPC. This is useful when a gateway sits between client
 * and server that cannot handle gRPC traffic.</p>
 *
 * <p>Currently supports Prompt operations; extensible for Skill and other capabilities.</p>
 *
 * @author nacos
 */
public class AiHttpClientProxy implements AiClientProxy {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiHttpClientProxy.class);
    
    private static final String PROMPT_CLIENT_PATH = "/v3/client/ai/prompt";
    
    private static final String SKILL_DOWNLOAD_PATH = "/v3/client/ai/skills";
    
    private static final String AGENTSPEC_CLIENT_PATH = "/v3/client/ai/agentspecs";
    
    private static final String AGENT_CLIENT_PATH = "/v3/client/ai/agents";
    
    private static final String AGENT_SEARCH_PATH = AGENT_CLIENT_PATH + "/search";
    
    private static final String AGENT_ENDPOINT_PATH = AGENT_CLIENT_PATH + "/endpoints";
    
    private static final String AGENT_ENDPOINT_HEARTBEAT_PATH =
        AGENT_ENDPOINT_PATH + "/heartbeat";
    
    private static final String HTTP_CLIENT_ID_HEADER = "X-Nacos-Client-Id";
    
    private static final int MAX_RETRY = 3;
    
    private static final boolean ENABLE_HTTPS = Boolean.getBoolean(TlsSystemConfig.TLS_ENABLE);
    
    private final String namespaceId;
    
    private final NacosRestTemplate nacosRestTemplate;
    
    private final NamingServerListManager serverListManager;
    
    private final SecurityProxy securityProxy;
    
    private final ScheduledThreadPoolExecutor executorService;
    
    private final String httpClientId = UUID.randomUUID().toString();
    
    AiHttpClientProxy() {
        this.namespaceId = null;
        this.nacosRestTemplate = null;
        this.serverListManager = null;
        this.securityProxy = null;
        this.executorService = null;
    }
    
    @Override
    public AgentVersionDetail publishAgent(AgentPublishRequest request) throws NacosException {
        Map<String, String> form = new HashMap<String, String>();
        form.put("namespaceId", namespaceId);
        form.put("agentName", request.getAgentName());
        putOptional(form, "displayName", request.getDisplayName());
        putOptional(form, "description", request.getDescription());
        putOptional(form, "iconUrl", request.getIconUrl());
        putJson(form, "provider", request.getProvider());
        putJson(form, "tags", request.getTags());
        putJson(form, "extensions", request.getExtensions());
        form.put("version", request.getVersion());
        putJson(form, "callInterfaces", request.getCallInterfaces());
        putOptional(form, "author", request.getAuthor());
        putOptional(form, "changeDescription", request.getChangeDescription());
        putOptional(form, "basedOnVersion", request.getBasedOnVersion());
        form.put("autoSubmit", String.valueOf(request.isAutoSubmit()));
        String response = requestAgentApi(AGENT_CLIENT_PATH, AgentHttpMethod.POST,
            Collections.<QueryParameter>emptyList(), form,
            buildAgentResource(request.getAgentName()));
        Result<AgentVersionDetail> result = JsonUtils.toObj(response,
            new NacosTypeReference<Result<AgentVersionDetail>>() {
            });
        return requireSuccess(result);
    }
    
    public AiHttpClientProxy(String namespaceId, NacosClientProperties properties)
        throws NacosException {
        this.namespaceId = namespaceId;
        this.nacosRestTemplate = NamingHttpClientManager.getInstance().getNacosRestTemplate();
        this.serverListManager = new NamingServerListManager(properties, namespaceId);
        this.serverListManager.start();
        this.securityProxy = new SecurityProxy(this.serverListManager, this.nacosRestTemplate);
        this.executorService = new ScheduledThreadPoolExecutor(1,
            new NameThreadFactory("com.alibaba.nacos.client.ai.http.security"));
        final Properties nacosClientPropertiesView = properties.asProperties();
        this.securityProxy.login(nacosClientPropertiesView);
        this.executorService.scheduleWithFixedDelay(
            () -> securityProxy.login(nacosClientPropertiesView), 0,
            SECURITY_INFO_REFRESH_INTERVAL_MILLS, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public Page<AgentCatalogEntry> searchAgents(AgentSearchRequest request)
        throws NacosException {
        List<QueryParameter> parameters = new ArrayList<QueryParameter>();
        addParameter(parameters, "namespaceId", request.getNamespaceId());
        addParameter(parameters, "agentNameContains", request.getAgentNameContains());
        addParameters(parameters, "tagsAll", request.getTagsAll());
        addParameters(parameters, "protocolsAny", request.getProtocolsAny());
        addParameter(parameters, "pageNo", request.getPageNo());
        addParameter(parameters, "pageSize", request.getPageSize());
        String response = requestAgentApi(AGENT_SEARCH_PATH, AgentHttpMethod.GET, parameters,
            Collections.<String, String>emptyMap(), buildAgentResource(null));
        Result<Page<AgentCatalogEntry>> result = JsonUtils.toObj(response,
            new NacosTypeReference<Result<Page<AgentCatalogEntry>>>() {
            });
        return requireSuccess(result);
    }
    
    @Override
    public AgentDiscoveryResult discoverAgent(AgentDiscoveryRequest request)
        throws NacosException {
        AgentReference reference = request.getReference();
        List<QueryParameter> parameters = new ArrayList<QueryParameter>();
        addParameter(parameters, "namespaceId", request.getNamespaceId());
        addParameter(parameters, "agentName", reference.getAgentName());
        addParameter(parameters, "version", reference.getVersion());
        addParameter(parameters, "label", reference.getLabel());
        addDiscoveryFilter(parameters, request.getFilter());
        String response = requestAgentApi(AGENT_CLIENT_PATH, AgentHttpMethod.GET, parameters,
            Collections.<String, String>emptyMap(),
            buildAgentResource(reference.getAgentName()));
        Result<AgentDiscoveryResult> result = JsonUtils.toObj(response,
            new NacosTypeReference<Result<AgentDiscoveryResult>>() {
            });
        return requireSuccess(result);
    }
    
    @Override
    public ClientLivenessInfo registerAgentEndpoints(AgentEndpointRegistrationBatch batch)
        throws NacosException {
        Map<String, String> form = new HashMap<String, String>();
        form.put("namespaceId", batch.getNamespaceId());
        form.put("agentName", batch.getAgentName());
        form.put("runtimeVersion", batch.getRuntimeVersion());
        if (batch.getVersionRange() != null) {
            form.put("versionRange", batch.getVersionRange());
        }
        form.put("protocol", batch.getProtocol());
        form.put("endpoints", JsonUtils.toJson(batch.getEndpoints()));
        String response = requestAgentApi(AGENT_ENDPOINT_PATH, AgentHttpMethod.POST,
            Collections.<QueryParameter>emptyList(), form,
            buildAgentResource(batch.getAgentName()));
        Result<ClientLivenessInfo> result = JsonUtils.toObj(response,
            new NacosTypeReference<Result<ClientLivenessInfo>>() {
            });
        return requireSuccess(result);
    }
    
    @Override
    public void deregisterAgentEndpoints(String namespaceId, String agentName, String protocol)
        throws NacosException {
        Map<String, String> form = new HashMap<String, String>();
        form.put("namespaceId", namespaceId);
        form.put("agentName", agentName);
        form.put("protocol", protocol);
        String response = requestAgentApi(AGENT_ENDPOINT_PATH, AgentHttpMethod.DELETE,
            Collections.<QueryParameter>emptyList(), form, buildAgentResource(agentName));
        Result<Void> result = JsonUtils.toObj(response, new NacosTypeReference<Result<Void>>() {
        });
        requireSuccess(result);
    }
    
    @Override
    public ClientLivenessInfo heartbeatAgentEndpoints() throws NacosException {
        String response = requestAgentApi(AGENT_ENDPOINT_HEARTBEAT_PATH, AgentHttpMethod.PUT,
            Collections.<QueryParameter>emptyList(), Collections.<String, String>emptyMap(),
            buildAgentResource(null));
        Result<ClientLivenessInfo> result = JsonUtils.toObj(response,
            new NacosTypeReference<Result<ClientLivenessInfo>>() {
            });
        return requireSuccess(result);
    }
    
    @Override
    public Prompt queryPrompt(String promptKey, String version, String label, String md5)
        throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("promptKey", promptKey);
        if (StringUtils.isNotBlank(version)) {
            params.put("version", version);
        }
        if (StringUtils.isNotBlank(label)) {
            params.put("label", label);
        }
        if (StringUtils.isNotBlank(md5)) {
            params.put("md5", md5);
        }
        
        RequestResource resource = RequestResource.aiBuilder().setNamespace(namespaceId)
            .setGroup(com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP)
            .setResource(null == promptKey ? StringUtils.EMPTY : promptKey).build();
        
        String responseBody = reqApi(PROMPT_CLIENT_PATH, params, resource);
        Result<Prompt> result =
            JsonUtils.toObj(responseBody, new NacosTypeReference<Result<Prompt>>() {
            });
        return result.getData();
    }
    
    /**
     * Download skill as ZIP byte array via HTTP REST API.
     *
     * @param skillName skill name
     * @param version   explicit version (optional)
     * @param label     route label, e.g. latest/stable (optional)
     * @return ZIP file as byte array
     * @throws NacosException if request fails
     */
    public byte[] downloadSkillZip(String skillName, String version, String label)
        throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("name", skillName);
        if (StringUtils.isNotBlank(version)) {
            params.put("version", version);
        }
        if (StringUtils.isNotBlank(label)) {
            params.put("label", label);
        }
        
        RequestResource resource = RequestResource.aiBuilder().setNamespace(namespaceId)
            .setGroup(com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP)
            .setResource(null == skillName ? StringUtils.EMPTY : skillName).build();
        
        byte[] zipBytes = reqApiBytes(SKILL_DOWNLOAD_PATH, params, resource);
        SkillUtils.validateZipBytes(zipBytes);
        try {
            SkillUtils.validateZipEntryPaths(zipBytes);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Downloaded ZIP contains unsafe entry paths: " + e.getMessage(), e);
        }
        return zipBytes;
    }
    
    @Override
    public SkillQueryResponse querySkill(String skillName, String version, String label, String md5)
        throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("name", skillName);
        if (StringUtils.isNotBlank(version)) {
            params.put("version", version);
        }
        if (StringUtils.isNotBlank(label)) {
            params.put("label", label);
        }
        if (StringUtils.isNotBlank(md5)) {
            params.put("md5", md5);
        }
        
        RequestResource resource = RequestResource.aiBuilder().setNamespace(namespaceId)
            .setGroup(com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP)
            .setResource(null == skillName ? StringUtils.EMPTY : skillName).build();
        
        HttpRestResult<byte[]> restResult = reqApiBytesWithHeader(SKILL_DOWNLOAD_PATH, params,
            resource);
        byte[] zipBytes = restResult.getData();
        SkillUtils.validateZipBytes(zipBytes);
        try {
            SkillUtils.validateZipEntryPaths(zipBytes);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Downloaded ZIP contains unsafe entry paths: " + e.getMessage(), e);
        }
        String publishedMd5 = restResult.getHeader().getValue("X-Nacos-Skill-Md5");
        String resolvedVersion = restResult.getHeader()
            .getValue("X-Nacos-Skill-Resolved-Version");
        return new SkillQueryResponse(zipBytes, publishedMd5, resolvedVersion);
    }
    
    @Override
    public AgentSpecQueryResponse queryAgentSpec(String agentSpecName, String version,
        String label, String md5) throws NacosException {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", namespaceId);
        params.put("name", agentSpecName);
        if (StringUtils.isNotBlank(version)) {
            params.put("version", version);
        }
        if (StringUtils.isNotBlank(label)) {
            params.put("label", label);
        }
        if (StringUtils.isNotBlank(md5)) {
            params.put("md5", md5);
        }
        
        RequestResource resource = RequestResource.aiBuilder().setNamespace(namespaceId)
            .setGroup(com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP)
            .setResource(
                null == agentSpecName ? StringUtils.EMPTY : agentSpecName)
            .build();
        
        HttpRestResult<String> restResult = reqApiStringWithHeader(
            AGENTSPEC_CLIENT_PATH, params, resource);
        String responseBody = restResult.getData();
        Result<AgentSpec> result =
            JsonUtils.toObj(responseBody, new NacosTypeReference<Result<AgentSpec>>() {
            });
        String publishedMd5 = restResult.getHeader().getValue("X-Nacos-AgentSpec-Md5");
        String resolvedVersion = restResult.getHeader()
            .getValue("X-Nacos-AgentSpec-Resolved-Version");
        return new AgentSpecQueryResponse(result.getData(), publishedMd5,
            resolvedVersion);
    }
    
    private void addDiscoveryFilter(List<QueryParameter> parameters,
        AgentDiscoveryFilter filter) {
        if (filter == null) {
            return;
        }
        addParameters(parameters, "protocol", filter.getProtocols());
        addParameter(parameters, "protocolVersion", filter.getProtocolVersion());
        addParameters(parameters, "transport", filter.getTransports());
        if (filter.getEndpointSources() != null) {
            for (EndpointSource source : filter.getEndpointSources()) {
                addParameter(parameters, "endpointSource", source.name());
            }
        }
        if (filter.getMetadataSelector() != null) {
            addParameter(parameters, "metadataSelector",
                JsonUtils.toJson(filter.getMetadataSelector()));
        }
    }
    
    private void addParameters(List<QueryParameter> parameters, String name,
        List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addParameter(parameters, name, value);
        }
    }
    
    private void addParameter(List<QueryParameter> parameters, String name, Object value) {
        if (value != null) {
            parameters.add(new QueryParameter(name, String.valueOf(value)));
        }
    }
    
    private void putOptional(Map<String, String> form, String name, String value) {
        if (value != null) {
            form.put(name, value);
        }
    }
    
    private void putJson(Map<String, String> form, String name, Object value) {
        if (value != null) {
            form.put(name, JsonUtils.toJson(value));
        }
    }
    
    private RequestResource buildAgentResource(String agentName) {
        return RequestResource.aiBuilder().setNamespace(namespaceId)
            .setGroup(Constants.DEFAULT_GROUP)
            .setResource(agentName == null ? StringUtils.EMPTY : agentName).build();
    }
    
    private String requestAgentApi(String api, AgentHttpMethod method,
        List<QueryParameter> parameters, Map<String, String> form, RequestResource resource)
        throws NacosException {
        List<String> servers = serverListManager.getServerList();
        if (servers.isEmpty()) {
            throw new NacosException(NacosException.INVALID_PARAM, "no server available");
        }
        NacosException exception = new NacosException();
        int index = ThreadLocalRandom.current().nextInt(servers.size());
        for (int i = 0; i < Math.max(servers.size(), MAX_RETRY); i++) {
            String server = servers.get(index % servers.size());
            try {
                return callAgentServer(api, method, parameters, form, server, resource);
            } catch (NacosException e) {
                if (isPublicationCapacityRejected(e)) {
                    throw e;
                }
                exception = e;
            }
            index = (index + 1) % servers.size();
        }
        throw new NacosException(exception.getErrCode(),
            "Failed to request API: " + api + " after all servers(" + servers + ") tried: "
                + exception.getMessage());
    }
    
    private String callAgentServer(String api, AgentHttpMethod method,
        List<QueryParameter> parameters, Map<String, String> form, String server,
        RequestResource resource) throws NacosException {
        Header header = Header.newInstance();
        header.addAll(securityProxy.getIdentityContext(resource));
        header.addParam(HTTP_CLIENT_ID_HEADER, httpClientId);
        if (AgentHttpMethod.GET != method) {
            header.addParam(HttpHeaderConsts.REQUEST_MODULE, Constants.AI.AI_MODULE);
        }
        String url = appendQuery(buildUrl(server, api), parameters);
        try {
            HttpRestResult<String> restResult;
            if (AgentHttpMethod.GET == method) {
                restResult = nacosRestTemplate.get(url, header, Query.EMPTY, String.class);
            } else if (AgentHttpMethod.POST == method) {
                restResult = nacosRestTemplate.postForm(url, header, form, String.class);
            } else if (AgentHttpMethod.DELETE == method) {
                restResult = nacosRestTemplate.delete(url, header,
                    Query.newInstance().initParams(form), String.class);
            } else {
                restResult = nacosRestTemplate.put(url, header, Query.EMPTY, null, String.class);
            }
            return resolveAgentResponse(restResult);
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[AI-HTTP] Failed to request {}", url, e);
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    private String resolveAgentResponse(HttpRestResult<String> restResult)
        throws NacosException {
        if (restResult.ok()) {
            return restResult.getData();
        }
        if (HttpURLConnection.HTTP_FORBIDDEN == restResult.getCode()) {
            securityProxy.reLogin();
        }
        String errorBody = restResult.getMessage();
        try {
            Result<Object> result = JsonUtils.toObj(errorBody,
                new NacosTypeReference<Result<Object>>() {
                });
            if (result != null && result.getCode() != null
                && !ErrorCode.SUCCESS.getCode().equals(result.getCode())) {
                String errorMessage = result.getData() == null ? result.getMessage()
                    : String.valueOf(result.getData());
                if (ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT.getCode()
                    .equals(result.getCode())) {
                    throw new NacosApiException(NacosException.OVER_THRESHOLD,
                        ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT, errorMessage);
                }
                int errorCode = ErrorCode.HTTP_CLIENT_NOT_FOUND.getCode().equals(result.getCode())
                    ? result.getCode() : restResult.getCode();
                throw new NacosException(errorCode, errorMessage);
            }
        } catch (NacosException e) {
            throw e;
        } catch (RuntimeException ignored) {
            LOGGER.debug("Agent error response is not a v3 Result.", ignored);
            throw new NacosException(restResult.getCode(), restResult.getMessage());
        }
        throw new NacosException(restResult.getCode(), restResult.getMessage());
    }
    
    private boolean isPublicationCapacityRejected(NacosException exception) {
        return exception instanceof NacosApiException
            && ((NacosApiException) exception)
                .getDetailErrCode() == ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT.getCode();
    }
    
    private <T> T requireSuccess(Result<T> result) throws NacosException {
        if (result == null || result.getCode() == null) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Agent API returned an invalid response.");
        }
        if (!ErrorCode.SUCCESS.getCode().equals(result.getCode())) {
            throw new NacosException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
    
    private String appendQuery(String url, List<QueryParameter> parameters)
        throws NacosException {
        if (parameters.isEmpty()) {
            return url;
        }
        StringBuilder result = new StringBuilder(url).append('?');
        try {
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) {
                    result.append('&');
                }
                QueryParameter parameter = parameters.get(i);
                result.append(URLEncoder.encode(parameter.name, Constants.ENCODE)).append('=')
                    .append(URLEncoder.encode(parameter.value, Constants.ENCODE));
            }
            return result.toString();
        } catch (UnsupportedEncodingException e) {
            throw new NacosException(NacosException.CLIENT_ERROR, e);
        }
    }
    
    private String reqApi(String api, Map<String, String> params, RequestResource resource)
        throws NacosException {
        List<String> servers = serverListManager.getServerList();
        if (servers.isEmpty()) {
            throw new NacosException(NacosException.INVALID_PARAM, "no server available");
        }
        
        NacosException exception = new NacosException();
        int index = ThreadLocalRandom.current().nextInt(servers.size());
        
        for (int i = 0; i < Math.max(servers.size(), MAX_RETRY); i++) {
            String server = servers.get(index % servers.size());
            try {
                return callServer(api, params, server, resource);
            } catch (NacosException e) {
                exception = e;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Request {} to server {} failed.", api, server, e);
                }
            }
            index = (index + 1) % servers.size();
        }
        
        LOGGER.error("Request: {} failed, servers: {}, code: {}, msg: {}", api, servers,
            exception.getErrCode(),
            exception.getErrMsg());
        throw new NacosException(exception.getErrCode(),
            "Failed to request API: " + api + " after all servers(" + servers + ") tried: "
                + exception.getMessage());
    }
    
    private byte[] reqApiBytes(String api, Map<String, String> params, RequestResource resource)
        throws NacosException {
        List<String> servers = serverListManager.getServerList();
        if (servers.isEmpty()) {
            throw new NacosException(NacosException.INVALID_PARAM, "no server available");
        }
        
        NacosException exception = new NacosException();
        int index = ThreadLocalRandom.current().nextInt(servers.size());
        
        for (int i = 0; i < Math.max(servers.size(), MAX_RETRY); i++) {
            String server = servers.get(index % servers.size());
            try {
                return callServerBytes(api, params, server, resource);
            } catch (NacosException e) {
                exception = e;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Request {} to server {} failed.", api, server, e);
                }
            }
            index = (index + 1) % servers.size();
        }
        
        LOGGER.error("Request: {} failed, servers: {}, code: {}, msg: {}", api, servers,
            exception.getErrCode(),
            exception.getErrMsg());
        throw new NacosException(exception.getErrCode(),
            "Failed to request API: " + api + " after all servers(" + servers + ") tried: "
                + exception.getMessage());
    }
    
    private HttpRestResult<byte[]> reqApiBytesWithHeader(String api, Map<String, String> params,
        RequestResource resource) throws NacosException {
        List<String> servers = serverListManager.getServerList();
        if (servers.isEmpty()) {
            throw new NacosException(NacosException.INVALID_PARAM, "no server available");
        }
        
        NacosException exception = new NacosException();
        int index = ThreadLocalRandom.current().nextInt(servers.size());
        
        for (int i = 0; i < Math.max(servers.size(), MAX_RETRY); i++) {
            String server = servers.get(index % servers.size());
            try {
                return callServerBytesWithHeader(api, params, server, resource);
            } catch (NacosException e) {
                if (NacosException.NOT_MODIFIED == e.getErrCode()) {
                    throw e;
                }
                exception = e;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Request {} to server {} failed.", api, server, e);
                }
            }
            index = (index + 1) % servers.size();
        }
        
        LOGGER.error("Request: {} failed, servers: {}, code: {}, msg: {}", api, servers,
            exception.getErrCode(),
            exception.getErrMsg());
        throw new NacosException(exception.getErrCode(),
            "Failed to request API: " + api + " after all servers(" + servers + ") tried: "
                + exception.getMessage());
    }
    
    private String callServer(String api, Map<String, String> params, String server,
        RequestResource resource)
        throws NacosException {
        Map<String, String> securityHeaders = securityProxy.getIdentityContext(resource);
        Header header = Header.newInstance();
        header.addAll(securityHeaders);
        
        String url = buildUrl(server, api);
        
        try {
            HttpRestResult<String> restResult = nacosRestTemplate.get(url, header,
                Query.newInstance().initParams(params), String.class);
            
            if (restResult.ok()) {
                return restResult.getData();
            }
            if (HttpURLConnection.HTTP_NOT_MODIFIED == restResult.getCode()) {
                throw new NacosException(NacosException.NOT_MODIFIED, "not modified");
            }
            if (HttpURLConnection.HTTP_FORBIDDEN == restResult.getCode()) {
                securityProxy.reLogin();
            }
            throw new NacosException(restResult.getCode(), restResult.getMessage());
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[AI-HTTP] Failed to request {}", url, e);
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    private byte[] callServerBytes(String api, Map<String, String> params, String server,
        RequestResource resource)
        throws NacosException {
        Map<String, String> securityHeaders = securityProxy.getIdentityContext(resource);
        Header header = Header.newInstance();
        header.addAll(securityHeaders);
        
        String url = buildUrl(server, api);
        
        try {
            HttpRestResult<byte[]> restResult = nacosRestTemplate.get(url, header,
                Query.newInstance().initParams(params), byte[].class);
            
            if (restResult.ok()) {
                return restResult.getData();
            }
            if (HttpURLConnection.HTTP_NOT_MODIFIED == restResult.getCode()) {
                throw new NacosException(NacosException.NOT_MODIFIED, "not modified");
            }
            if (HttpURLConnection.HTTP_FORBIDDEN == restResult.getCode()) {
                securityProxy.reLogin();
            }
            throw new NacosException(restResult.getCode(), restResult.getMessage());
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[AI-HTTP] Failed to request {}", url, e);
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    /**
     * Variant of {@link #callServerBytes} that exposes the raw {@link HttpRestResult} so callers
     * can inspect response headers (e.g. {@code X-Nacos-Skill-Md5}). Status code translation rules
     * mirror {@link #callServerBytes}: 304 raises {@link NacosException#NOT_MODIFIED}, 403
     * triggers a security re-login before bubbling the original status code up.
     */
    private HttpRestResult<byte[]> callServerBytesWithHeader(String api,
        Map<String, String> params, String server, RequestResource resource)
        throws NacosException {
        Map<String, String> securityHeaders = securityProxy.getIdentityContext(resource);
        Header header = Header.newInstance();
        header.addAll(securityHeaders);
        
        String url = buildUrl(server, api);
        
        try {
            HttpRestResult<byte[]> restResult = nacosRestTemplate.get(url, header,
                Query.newInstance().initParams(params), byte[].class);
            
            if (restResult.ok()) {
                return restResult;
            }
            if (HttpURLConnection.HTTP_NOT_MODIFIED == restResult.getCode()) {
                throw new NacosException(NacosException.NOT_MODIFIED, "not modified");
            }
            if (HttpURLConnection.HTTP_FORBIDDEN == restResult.getCode()) {
                securityProxy.reLogin();
            }
            throw new NacosException(restResult.getCode(), restResult.getMessage());
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[AI-HTTP] Failed to request {}", url, e);
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    private String buildUrl(String serverAddr, String relativePath) {
        if (!serverAddr.startsWith(HTTP_PREFIX) && !serverAddr.startsWith(HTTPS_PREFIX)) {
            serverAddr = (ENABLE_HTTPS ? HTTPS_PREFIX : HTTP_PREFIX) + serverAddr;
        }
        String contextPath = serverListManager.getContextPath();
        return serverAddr + ContextPathUtil.normalizeContextPath(contextPath) + relativePath;
    }
    
    /**
     * Request API returning String body with headers exposed, propagating 304 immediately.
     */
    private HttpRestResult<String> reqApiStringWithHeader(String api,
        Map<String, String> params, RequestResource resource) throws NacosException {
        List<String> servers = serverListManager.getServerList();
        if (servers.isEmpty()) {
            throw new NacosException(NacosException.INVALID_PARAM, "no server available");
        }
        
        NacosException exception = new NacosException();
        int index = ThreadLocalRandom.current().nextInt(servers.size());
        
        for (int i = 0; i < Math.max(servers.size(), MAX_RETRY); i++) {
            String server = servers.get(index % servers.size());
            try {
                return callServerStringWithHeader(api, params, server, resource);
            } catch (NacosException e) {
                if (NacosException.NOT_MODIFIED == e.getErrCode()) {
                    throw e;
                }
                exception = e;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Request {} to server {} failed.", api, server, e);
                }
            }
            index = (index + 1) % servers.size();
        }
        
        LOGGER.error("Request: {} failed, servers: {}, code: {}, msg: {}", api, servers,
            exception.getErrCode(),
            exception.getErrMsg());
        throw new NacosException(exception.getErrCode(),
            "Failed to request API: " + api + " after all servers(" + servers + ") tried: "
                + exception.getMessage());
    }
    
    private HttpRestResult<String> callServerStringWithHeader(String api,
        Map<String, String> params, String server, RequestResource resource)
        throws NacosException {
        Map<String, String> securityHeaders = securityProxy.getIdentityContext(resource);
        Header header = Header.newInstance();
        header.addAll(securityHeaders);
        
        String url = buildUrl(server, api);
        
        try {
            HttpRestResult<String> restResult = nacosRestTemplate.get(url, header,
                Query.newInstance().initParams(params), String.class);
            
            if (restResult.ok()) {
                return restResult;
            }
            if (HttpURLConnection.HTTP_NOT_MODIFIED == restResult.getCode()) {
                throw new NacosException(NacosException.NOT_MODIFIED, "not modified");
            }
            if (HttpURLConnection.HTTP_FORBIDDEN == restResult.getCode()) {
                securityProxy.reLogin();
            }
            throw new NacosException(restResult.getCode(), restResult.getMessage());
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[AI-HTTP] Failed to request {}", url, e);
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        serverListManager.shutdown();
        if (securityProxy != null) {
            securityProxy.shutdown();
        }
        if (executorService != null) {
            ThreadUtils.shutdownThreadPool(executorService, LOGGER);
        }
    }
    
    private enum AgentHttpMethod {
        
        GET,
        
        POST,
        
        DELETE,
        
        PUT
    }
    
    private static final class QueryParameter {
        
        private final String name;
        
        private final String value;
        
        private QueryParameter(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
