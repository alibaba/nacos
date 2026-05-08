/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.oidc;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * OIDC client context that holds configuration and performs OIDC Discovery.
 *
 * <p>Reads OIDC configuration from {@link Properties} and optionally performs
 * OIDC Discovery to resolve the token endpoint from the issuer's
 * {@code .well-known/openid-configuration}.
 *
 * @author wangzji
 */
public class OidcClientContext {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcClientContext.class);
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private String issuerUri;
    
    private String clientId;
    
    private String clientSecret;
    
    private String scope;
    
    private volatile String tokenEndpoint;
    
    private volatile boolean discovered;
    
    /**
     * Initialize context from properties.
     *
     * @param properties the client properties
     * @return true if OIDC is configured (client-id and client-secret are present)
     */
    public boolean init(Properties properties) {
        this.issuerUri = properties.getProperty(OidcClientConstants.PROP_ISSUER_URI);
        this.clientId = properties.getProperty(OidcClientConstants.PROP_CLIENT_ID);
        this.clientSecret = properties.getProperty(OidcClientConstants.PROP_CLIENT_SECRET);
        this.scope = properties.getProperty(OidcClientConstants.PROP_SCOPE,
            OidcClientConstants.DEFAULT_SCOPE);
        
        // Allow direct token endpoint override, skipping discovery
        String directTokenEndpoint =
            properties.getProperty(OidcClientConstants.PROP_TOKEN_ENDPOINT);
        if (StringUtils.isNotBlank(directTokenEndpoint)) {
            this.tokenEndpoint = directTokenEndpoint;
            this.discovered = true;
        }
        
        return isConfigured();
    }
    
    /**
     * Check if OIDC is configured with sufficient credentials.
     *
     * @return true if client-id, client-secret and (issuer-uri or token-endpoint) are present
     */
    public boolean isConfigured() {
        return StringUtils.isNotBlank(clientId) && StringUtils.isNotBlank(clientSecret)
            && (StringUtils.isNotBlank(issuerUri) || StringUtils.isNotBlank(tokenEndpoint));
    }
    
    /**
     * Perform OIDC Discovery to retrieve the token endpoint.
     *
     * <p>Fetches {@code {issuer-uri}/.well-known/openid-configuration} and
     * extracts the {@code token_endpoint} field.
     *
     * @return true if discovery succeeds
     */
    public boolean discover() {
        if (discovered) {
            return true;
        }
        
        if (StringUtils.isBlank(issuerUri)) {
            LOGGER.warn(
                "[OIDC-CLIENT] issuer-uri is not configured, cannot perform OIDC Discovery");
            return false;
        }
        
        String discoveryUrl = issuerUri.endsWith("/")
            ? issuerUri + ".well-known/openid-configuration"
            : issuerUri + OidcProtocolConstants.WELL_KNOWN_PATH;
        
        LOGGER.info("[OIDC-CLIENT] Performing OIDC Discovery from: {}", discoveryUrl);
        
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(discoveryUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(OidcProtocolConstants.DEFAULT_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(OidcProtocolConstants.DEFAULT_READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", OidcProtocolConstants.CONTENT_TYPE_JSON);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != OidcProtocolConstants.HTTP_STATUS_OK) {
                LOGGER.error("[OIDC-CLIENT] OIDC Discovery failed, HTTP status: {}", responseCode);
                return false;
            }
            
            String responseBody;
            try (InputStream responseStream = connection.getInputStream()) {
                responseBody = readInputStreamAsString(responseStream);
            }
            
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode tokenEndpointNode = root.get(OidcProtocolConstants.DISCOVERY_TOKEN_ENDPOINT);
            
            if (tokenEndpointNode == null || tokenEndpointNode.isNull()) {
                LOGGER.error("[OIDC-CLIENT] OIDC Discovery response missing 'token_endpoint'");
                return false;
            }
            
            this.tokenEndpoint = tokenEndpointNode.asText();
            this.discovered = true;
            LOGGER.info("[OIDC-CLIENT] OIDC Discovery success, token_endpoint: {}",
                this.tokenEndpoint);
            return true;
            
        } catch (IOException e) {
            LOGGER.error("[OIDC-CLIENT] OIDC Discovery failed", e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    public String getIssuerUri() {
        return issuerUri;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public String getScope() {
        return scope;
    }
    
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }
    
    public boolean isDiscovered() {
        return discovered;
    }
    
    /**
     * Read an InputStream fully into a String (Java 8 compatible).
     *
     * @param inputStream the input stream to read
     * @return the string content
     * @throws IOException if reading fails
     */
    static String readInputStreamAsString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
}
