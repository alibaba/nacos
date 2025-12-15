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

package com.alibaba.nacos.console.handler.impl.remote.config;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.console.config.NacosConsoleAuthConfig;
import com.alibaba.nacos.console.handler.core.ClusterHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NacosMemberManager;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Nacos config import and export service.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class ConfigImportAndExportService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigImportAndExportService.class);
    
    private static final String REMOTE_CONFIG_IMPORT_URL = "http://%s%s/v3/admin/cs/config/import";
    
    private static final String REMOTE_CONFIG_EXPORT_URL = "http://%s%s/v3/admin/cs/config/export";
    
    private final NacosMemberManager memberManager;

    private final ClusterHandler remoteClusterHandler;
    
    public ConfigImportAndExportService(NacosMemberManager memberManager, ClusterHandler remoteClusterHandler) {
        this.memberManager = memberManager;
        this.remoteClusterHandler = remoteClusterHandler;
    }
    
    /**
     * Do import config to remote server.
     *
     * @param sourceUser    source user from console request
     * @param namespaceId   namespace id from console request
     * @param policy        conflict policy
     * @param importFile    imported config file
     * @param sourceIp      source ip from console request
     * @param sourceApp     source app from console request
     * @return Maps of import success and failed count
     */
    public Result<Map<String, Object>> importConfig(String sourceUser, String namespaceId, SameConfigPolicy policy,
            MultipartFile importFile, String sourceIp, String sourceApp) throws NacosException {
        String serverContextPath = getServerContextPath();
        Member serverMember = randomOneHealthyMember();
        String url = String.format(REMOTE_CONFIG_IMPORT_URL, serverMember.getAddress(), serverContextPath);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Query query = Query.newInstance().addParam("namespaceId", namespaceId).addParam("srcUser", sourceUser);
            URI uri = HttpUtils.buildUri(url, query);
            HttpPost httpPost = new HttpPost(uri);
            httpPost.setHeader(WebUtils.X_FORWARDED_FOR, sourceIp);
            httpPost.setHeader(RequestUtil.CLIENT_APPNAME_HEADER, sourceApp);
            addAuthIdentity(httpPost);
            String contentTypeString = null == importFile.getContentType() ? MediaType.MULTIPART_FORM_DATA_VALUE
                    : importFile.getContentType();
            ContentType contentType = ContentType.create(contentTypeString, Constants.ENCODE);
            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
            multipartEntityBuilder.addBinaryBody("file", importFile.getInputStream(), contentType,
                    importFile.getOriginalFilename());
            multipartEntityBuilder.addTextBody("policy", policy.name(), contentType);
            HttpEntity entity = multipartEntityBuilder.build();
            httpPost.setEntity(entity);
            String executeResult = httpClient.execute(httpPost, new BasicHttpClientResponseHandler());
            return JacksonUtils.toObj(executeResult, new TypeReference<>() {
            });
        } catch (HttpResponseException responseException) {
            LOGGER.error("Import config to server {} failed with code {}: ", serverMember.getAddress(),
                    responseException.getStatusCode());
            throw new NacosRuntimeException(responseException.getStatusCode(), responseException.getMessage());
        } catch (IOException | URISyntaxException e) {
            LOGGER.error("Import config to server {} failed: ", serverMember.getAddress(), e);
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, "Import config to server failed.");
        }
    }
    
    /**
     * Do export config to from server.
     *
     * @param dataId        data id of export config
     * @param group         group name of export config
     * @param namespaceId   namespace of export config
     * @param appName       app name of export config
     * @param ids           storage id of export config
     * @return export file entity
     * @throws Exception    any exception during export config
     */
    public ResponseEntity<byte[]> exportConfig(String dataId, String group, String namespaceId, String appName,
            List<Long> ids) throws Exception {
        String serverContextPath = getServerContextPath();
        Member serverMember = randomOneHealthyMember();
        String url = String.format(REMOTE_CONFIG_EXPORT_URL, serverMember.getAddress(), serverContextPath);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Query query = Query.newInstance().addParam("namespaceId", namespaceId).addParam("dataId", dataId)
                    .addParam("groupName", group).addParam("ids", StringUtils.join(ids, ","));
            URI uri = HttpUtils.buildUri(url, query);
            HttpGet httpGet = new HttpGet(uri);
            addAuthIdentity(httpGet);
            return httpClient.execute(httpGet, new ExportHttpClientResponseHandler());
        } catch (HttpResponseException responseException) {
            LOGGER.error("Export config from server {} failed with code {}: ", serverMember.getAddress(),
                    responseException.getStatusCode());
            throw new NacosRuntimeException(responseException.getStatusCode(), responseException.getMessage());
        } catch (IOException | URISyntaxException e) {
            LOGGER.error("Export config from server {} failed: ", serverMember.getAddress(), e);
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, "Export config to server failed.");
        }
    }
    
    private void addAuthIdentity(HttpRequest request) {
        NacosAuthConfig authConfig = NacosAuthConfigHolder.getInstance()
                .getNacosAuthConfigByScope(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE);
        if (StringUtils.isNotBlank(authConfig.getServerIdentityKey())) {
            request.setHeader(authConfig.getServerIdentityKey(), authConfig.getServerIdentityValue());
        }
    }
    
    private String getServerContextPath() {
        return EnvUtil.getProperty("nacos.console.remote.server.context-path", "/nacos");
    }
    
    private Member randomOneHealthyMember() throws NacosException {
        // all nodes in cluster.conf
        Collection<Member> allMembers = memberManager.allMembers();
        // nodes with state  in cluster
        Collection<? extends NacosMember> membersWithState = remoteClusterHandler.getNodeList("");
        Map<String, NodeState> nodeStateMap = membersWithState
                .stream().collect(Collectors.toMap(NacosMember::getAddress, NacosMember::getState));
        // remove unhealthy nodes
        allMembers.removeIf(node -> !NodeState.UP.equals(nodeStateMap.get(node.getAddress())));
        if (CollectionUtils.isEmpty(allMembers)) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, "No healthy server node found.");
        }

        return allMembers.parallelStream().findAny().orElseThrow();
    }
    
    static class ExportHttpClientResponseHandler
            extends AbstractHttpClientResponseHandler<ResponseEntity<byte[]>> {
        
        private String contentDisposition;
        
        @Override
        public ResponseEntity<byte[]> handleResponse(ClassicHttpResponse response) throws IOException {
            try {
                contentDisposition = response.getHeader("Content-Disposition").getValue();
            } catch (ProtocolException e) {
                throw new NacosRuntimeException(NacosException.SERVER_ERROR,
                        "Export config from server, parse response file name failed; ", e);
            }
            return super.handleResponse(response);
        }
        
        @Override
        public ResponseEntity<byte[]> handleEntity(HttpEntity entity) throws IOException {
            InputStream inputStream = entity.getContent();
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                IoUtils.copy(inputStream, outputStream);
                byte[] responseBody = outputStream.toByteArray();
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", contentDisposition).body(responseBody);
            }
        }
    }
}
