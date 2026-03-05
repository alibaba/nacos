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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.RemoteServerConnector;
import com.alibaba.nacos.core.cluster.Member;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Nacos skill upload service for remote server via HTTP multipart POST.
 *
 * <p>In console deployment mode, the maintainer client cannot properly send multipart file uploads.
 * This service directly constructs HTTP multipart requests to forward zip files to the remote admin server,
 * similar to {@link com.alibaba.nacos.console.handler.impl.remote.config.ConfigImportAndExportService}.</p>
 *
 * @author nacos
 */
@Service
@EnabledRemoteHandler
public class SkillUploadService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillUploadService.class);
    
    private static final String REMOTE_SKILL_UPLOAD_URL = "http://%s%s/v3/admin/ai/skills/upload";
    
    private final RemoteServerConnector remoteServerConnector;
    
    public SkillUploadService(RemoteServerConnector remoteServerConnector) {
        this.remoteServerConnector = remoteServerConnector;
    }
    
    /**
     * Upload skill zip to remote server via HTTP multipart POST.
     *
     * @param namespaceId namespace ID
     * @param zipBytes    zip file bytes
     * @return skill name from server response
     * @throws NacosException if upload fails
     */
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        String serverContextPath = remoteServerConnector.getServerContextPath();
        Member serverMember = remoteServerConnector.randomOneHealthyMember();
        String url = String.format(REMOTE_SKILL_UPLOAD_URL, serverMember.getAddress(), serverContextPath);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Query query = Query.newInstance().addParam("namespaceId", namespaceId);
            URI uri = HttpUtils.buildUri(url, query);
            HttpPost httpPost = new HttpPost(uri);
            remoteServerConnector.addAuthIdentity(httpPost);
            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
            multipartEntityBuilder.addBinaryBody("file", new ByteArrayInputStream(zipBytes),
                    ContentType.APPLICATION_OCTET_STREAM, "skill.zip");
            HttpEntity entity = multipartEntityBuilder.build();
            httpPost.setEntity(entity);
            String executeResult = httpClient.execute(httpPost, new BasicHttpClientResponseHandler());
            Result<String> result = JacksonUtils.toObj(executeResult, new TypeReference<>() {
            });
            return result.getData();
        } catch (HttpResponseException responseException) {
            LOGGER.error("Upload skill zip to server {} failed with code {}: ", serverMember.getAddress(),
                    responseException.getStatusCode());
            throw new NacosRuntimeException(responseException.getStatusCode(), responseException.getMessage());
        } catch (IOException | URISyntaxException e) {
            LOGGER.error("Upload skill zip to server {} failed: ", serverMember.getAddress(), e);
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, "Upload skill zip to server failed.");
        }
    }
    
}
