/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.runtime;

import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.HttpConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.HttpConnectionBasedClientManager;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Owns the shared Naming HTTP Client lifecycle used by AI runtime publishers.
 *
 * <p>Agent and MCP adapters pass only their domain operation. Identity binding, namespace
 * binding, liveness renewal, and empty-client cleanup stay centralized so both modules share the
 * same external Client id without implementing competing lifecycle rules.</p>
 *
 * @author Nacos
 */
@Service
public class AiHttpClientLifecycleService {
    
    static final String IDENTITY_ATTRIBUTE = "httpClientIdentity";
    
    static final String NAMESPACE_ATTRIBUTE = "httpClientNamespace";
    
    private static final int MAX_EXTERNAL_CLIENT_ID_LENGTH = 256;
    
    private static final Pattern EXTERNAL_CLIENT_ID_PATTERN =
        Pattern.compile("[A-Za-z0-9._:-]+");
    
    private final HttpConnectionBasedClientManager clientManager;
    
    public AiHttpClientLifecycleService(HttpConnectionBasedClientManager clientManager) {
        this.clientManager = clientManager;
    }
    
    /**
     * Refresh an existing HTTP Client without changing Publisher liveness.
     *
     * @param externalClientId optional external HTTP Client id
     * @param namespaceId request namespace
     * @throws NacosApiException when the id or existing binding is invalid
     */
    public void renewForQuery(String externalClientId, String namespaceId)
        throws NacosApiException {
        if (StringUtils.isBlank(externalClientId)) {
            return;
        }
        validateExternalClientId(externalClientId);
        HttpConnectionBasedClient client = getClient(externalClientId);
        if (client == null) {
            return;
        }
        validateExistingBinding(client, namespaceId);
        clientManager.renewClient(client.getClientId());
    }
    
    /**
     * Validate a stateful HTTP request and renew an existing bound Client.
     *
     * <p>The operation does not create a Client or Publisher. It is used by long-lived read
     * operations such as Agent Watch, which may share a Client id with Agent or MCP Endpoint
     * publications.</p>
     *
     * @param externalClientId external HTTP Client id
     * @param requestModule Request-Module header
     * @param namespaceId request namespace
     * @throws NacosApiException when the headers or an existing binding are invalid
     */
    public void renewForWatch(String externalClientId, String requestModule, String namespaceId)
        throws NacosApiException {
        validateStatefulHeaders(externalClientId, requestModule);
        HttpConnectionBasedClient client = getClient(externalClientId);
        if (client == null) {
            return;
        }
        validateExistingBinding(client, namespaceId);
        clientManager.renewClient(client.getClientId());
    }
    
    /**
     * Execute a stateful publication write and renew every Publisher owned by the shared Client.
     *
     * @param externalClientId external HTTP Client id
     * @param requestModule Request-Module header
     * @param namespaceId publication namespace
     * @param operation domain publication operation
     * @return server liveness intervals
     * @throws NacosException when validation or publication fails
     */
    public ClientLivenessInfo register(String externalClientId, String requestModule,
        String namespaceId, StatefulOperation operation) throws NacosException {
        validateStatefulHeaders(externalClientId, requestModule);
        String internalClientId = ensureBoundClient(externalClientId, namespaceId);
        try {
            operation.execute(internalClientId);
            if (!clientManager.renewPublisher(internalClientId)) {
                throw clientNotFound(
                    "HTTP Client does not exist or owns no AI Endpoint publication.");
            }
            return buildLivenessInfo();
        } catch (NacosException | RuntimeException e) {
            clientManager.disconnectIfEmpty(internalClientId);
            throw e;
        }
    }
    
    /**
     * Execute a stateful publication removal and remove the shared Client when it becomes empty.
     *
     * @param externalClientId external HTTP Client id
     * @param requestModule Request-Module header
     * @param namespaceId publication namespace
     * @param operation domain publication operation
     * @throws NacosException when validation or removal fails
     */
    public void deregister(String externalClientId, String requestModule, String namespaceId,
        StatefulOperation operation) throws NacosException {
        validateStatefulHeaders(externalClientId, requestModule);
        HttpConnectionBasedClient client = getClient(externalClientId);
        if (client == null) {
            return;
        }
        validateExistingBinding(client, namespaceId);
        operation.execute(client.getClientId());
        clientManager.disconnectIfEmpty(client.getClientId());
    }
    
    /**
     * Refresh the shared HTTP Client and all Agent and MCP publications it owns.
     *
     * @param externalClientId external HTTP Client id
     * @param requestModule Request-Module header
     * @return server liveness intervals
     * @throws NacosApiException when the Client or publication does not exist
     */
    public ClientLivenessInfo heartbeat(String externalClientId, String requestModule)
        throws NacosApiException {
        validateStatefulHeaders(externalClientId, requestModule);
        HttpConnectionBasedClient client = getClient(externalClientId);
        if (client == null) {
            throw clientNotFound();
        }
        validateIdentity(client);
        if (!clientManager.renewPublisher(client.getClientId())) {
            throw clientNotFound();
        }
        return buildLivenessInfo();
    }
    
    private String ensureBoundClient(String externalClientId, String namespaceId)
        throws NacosApiException {
        String internalClientId = HttpConnectionBasedClient.getInternalClientId(externalClientId);
        HttpConnectionBasedClient client = getClient(externalClientId);
        if (client == null) {
            ClientAttributes attributes = new ClientAttributes();
            attributes.addClientAttribute(IDENTITY_ATTRIBUTE,
                VisibilityHelper.resolveCurrentIdentity());
            attributes.addClientAttribute(NAMESPACE_ATTRIBUTE, namespaceId);
            if (!clientManager.clientConnected(internalClientId, attributes)) {
                throw clientNotFound("HTTP Client could not be created on the responsible server.");
            }
            client = getClient(externalClientId);
        }
        if (client == null) {
            throw clientNotFound("HTTP Client is not ready after creation.");
        }
        bindMissingAttributes(client, namespaceId);
        validateExistingBinding(client, namespaceId);
        return internalClientId;
    }
    
    private void bindMissingAttributes(HttpConnectionBasedClient client, String namespaceId)
        throws NacosApiException {
        ClientAttributes attributes = client.getClientAttributes();
        if (attributes == null) {
            throw clientNotFound("HTTP Client binding is not ready.");
        }
        Object identity = attributes.getClientAttribute(IDENTITY_ATTRIBUTE);
        Object namespace = attributes.getClientAttribute(NAMESPACE_ATTRIBUTE);
        if (identity == null && namespace == null) {
            attributes.addClientAttribute(IDENTITY_ATTRIBUTE,
                VisibilityHelper.resolveCurrentIdentity());
            attributes.addClientAttribute(NAMESPACE_ATTRIBUTE, namespaceId);
        }
    }
    
    private void validateExistingBinding(HttpConnectionBasedClient client, String namespaceId)
        throws NacosApiException {
        validateIdentity(client);
        Object boundNamespace =
            client.getClientAttributes().getClientAttribute(NAMESPACE_ATTRIBUTE);
        if (!Objects.equals(boundNamespace, namespaceId)) {
            throw accessDenied("HTTP Client namespace does not match its initial binding.");
        }
    }
    
    private void validateIdentity(HttpConnectionBasedClient client) throws NacosApiException {
        ClientAttributes attributes = client.getClientAttributes();
        Object boundIdentity =
            attributes == null ? null : attributes.getClientAttribute(IDENTITY_ATTRIBUTE);
        if (!Objects.equals(boundIdentity, VisibilityHelper.resolveCurrentIdentity())) {
            throw accessDenied("HTTP Client identity does not match its initial binding.");
        }
    }
    
    private HttpConnectionBasedClient getClient(String externalClientId) {
        String internalClientId = HttpConnectionBasedClient.getInternalClientId(externalClientId);
        Client client = clientManager.getClient(internalClientId);
        return client instanceof HttpConnectionBasedClient
            ? (HttpConnectionBasedClient) client : null;
    }
    
    private void validateStatefulHeaders(String externalClientId, String requestModule)
        throws NacosApiException {
        validateExternalClientId(externalClientId);
        if (!Constants.AI.AI_MODULE.equalsIgnoreCase(requestModule)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Request header `" + HttpHeaderConsts.REQUEST_MODULE + "` must be `AI`.");
        }
    }
    
    private void validateExternalClientId(String externalClientId) throws NacosApiException {
        if (StringUtils.isBlank(externalClientId)
            || externalClientId.length() > MAX_EXTERNAL_CLIENT_ID_LENGTH
            || !EXTERNAL_CLIENT_ID_PATTERN.matcher(externalClientId).matches()) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Request header `" + ClientConstants.HTTP_CLIENT_ID_HEADER
                    + "` must match `[A-Za-z0-9._:-]+` and contain 1 to 256 characters.");
        }
    }
    
    private ClientLivenessInfo buildLivenessInfo() {
        ClientLivenessInfo result = new ClientLivenessInfo();
        result.setHeartbeatIntervalMillis(Constants.DEFAULT_HEART_BEAT_INTERVAL);
        result.setUnhealthyTimeoutMillis(Constants.DEFAULT_HEART_BEAT_TIMEOUT);
        result.setExpireTimeoutMillis(Constants.DEFAULT_IP_DELETE_TIMEOUT);
        return result;
    }
    
    private NacosApiException clientNotFound() {
        return clientNotFound("HTTP Client does not exist or owns no AI Endpoint publication.");
    }
    
    private NacosApiException clientNotFound(String message) {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.HTTP_CLIENT_NOT_FOUND,
            message);
    }
    
    private NacosApiException accessDenied(String message) {
        return new NacosApiException(NacosException.NO_RIGHT, ErrorCode.ACCESS_DENIED, message);
    }
    
    /**
     * One domain mutation executed with the resolved Naming internal Client id.
     */
    @FunctionalInterface
    public interface StatefulOperation {
        
        /**
         * Execute the domain mutation.
         *
         * @param internalClientId Naming internal Client id
         * @throws NacosException when the mutation fails
         */
        void execute(String internalClientId) throws NacosException;
    }
}
