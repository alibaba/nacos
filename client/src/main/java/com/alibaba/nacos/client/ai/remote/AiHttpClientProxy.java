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

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.tls.TlsSystemConfig;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
    
    private static final int MAX_RETRY = 3;
    
    private static final boolean ENABLE_HTTPS = Boolean.getBoolean(TlsSystemConfig.TLS_ENABLE);
    
    private final String namespaceId;
    
    private final NacosRestTemplate nacosRestTemplate;
    
    private final NamingServerListManager serverListManager;
    
    private final SecurityProxy securityProxy;
    
    private final ScheduledThreadPoolExecutor executorService;
    
    AiHttpClientProxy() {
        this.namespaceId = null;
        this.nacosRestTemplate = null;
        this.serverListManager = null;
        this.securityProxy = null;
        this.executorService = null;
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
            JacksonUtils.toObj(responseBody, new TypeReference<Result<Prompt>>() {
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
            JacksonUtils.toObj(responseBody, new TypeReference<Result<AgentSpec>>() {
            });
        String publishedMd5 = restResult.getHeader().getValue("X-Nacos-AgentSpec-Md5");
        String resolvedVersion = restResult.getHeader()
            .getValue("X-Nacos-AgentSpec-Resolved-Version");
        return new AgentSpecQueryResponse(result.getData(), publishedMd5,
            resolvedVersion);
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
}
