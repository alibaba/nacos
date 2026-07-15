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

import com.alibaba.nacos.common.utils.RandomUtils;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

/**
 * Manages the OIDC Access Token lifecycle using the Client Credentials Grant.
 *
 * <p>Responsible for:
 * <ul>
 *   <li>Fetching access tokens from the IdP token endpoint</li>
 *   <li>Tracking token expiration</li>
 *   <li>Determining when a proactive refresh is needed (within 6.7%-10% of expiry window)</li>
 * </ul>
 *
 * @author wangzji
 */
public class OidcTokenHolder {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcTokenHolder.class);
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private volatile String accessToken;
    
    private volatile long expiresInSeconds;
    
    private volatile long obtainedAtMs;
    
    /**
     * Fetch a new access token using Client Credentials Grant.
     *
     * @param context the OIDC client context with configuration
     * @return true if the token was fetched successfully
     */
    public boolean fetchToken(OidcClientContext context) {
        String tokenEndpoint = context.getTokenEndpoint();
        if (tokenEndpoint == null) {
            LOGGER.error("[OIDC-CLIENT] Token endpoint is not available");
            return false;
        }
        
        LOGGER.debug("[OIDC-CLIENT] Requesting access token from: {}", tokenEndpoint);
        
        HttpURLConnection connection = null;
        try {
            // Build form-encoded request body
            String requestBody = buildTokenRequestBody(context);
            
            connection = (HttpURLConnection) new URL(tokenEndpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(OidcProtocolConstants.DEFAULT_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(OidcProtocolConstants.DEFAULT_READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", OidcProtocolConstants.CONTENT_TYPE_FORM);
            connection.setRequestProperty("Accept", OidcProtocolConstants.CONTENT_TYPE_JSON);
            
            byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode != OidcProtocolConstants.HTTP_STATUS_OK) {
                String errorBody = "";
                try (InputStream errorStream = connection.getErrorStream()) {
                    if (errorStream != null) {
                        errorBody = OidcClientContext.readInputStreamAsString(errorStream);
                    }
                }
                LOGGER.error("[OIDC-CLIENT] Token request failed, HTTP status: {}, body: {}",
                    responseCode, errorBody);
                return false;
            }
            
            String responseBody;
            try (InputStream responseStream = connection.getInputStream()) {
                responseBody = OidcClientContext.readInputStreamAsString(responseStream);
            }
            
            return parseTokenResponse(responseBody);
            
        } catch (IOException e) {
            LOGGER.error("[OIDC-CLIENT] Token request failed", e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Parse the token response JSON and update internal state.
     *
     * @param responseBody the JSON response body
     * @return true if parsing succeeds
     */
    private boolean parseTokenResponse(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            
            JsonNode accessTokenNode = root.get(OidcProtocolConstants.TOKEN_RESPONSE_ACCESS_TOKEN);
            if (accessTokenNode == null || accessTokenNode.isNull()) {
                LOGGER.error("[OIDC-CLIENT] Token response missing 'access_token'");
                return false;
            }
            
            JsonNode expiresInNode = root.get(OidcProtocolConstants.TOKEN_RESPONSE_EXPIRES_IN);
            long newExpiresIn = 300; // default 5 minutes if not provided
            if (expiresInNode != null && !expiresInNode.isNull()) {
                newExpiresIn = expiresInNode.asLong(300);
            }
            
            this.accessToken = accessTokenNode.asText();
            this.expiresInSeconds = newExpiresIn;
            this.obtainedAtMs = System.currentTimeMillis();
            
            LOGGER.info("[OIDC-CLIENT] Access token obtained successfully, expires_in: {}s",
                newExpiresIn);
            return true;
            
        } catch (IOException e) {
            LOGGER.error("[OIDC-CLIENT] Failed to parse token response", e);
            return false;
        }
    }
    
    /**
     * Build the form-encoded request body for Client Credentials Grant.
     *
     * @param context the OIDC client context
     * @return the URL-encoded form body
     */
    private String buildTokenRequestBody(OidcClientContext context) {
        try {
            String charsetName = StandardCharsets.UTF_8.name();
            StringJoiner joiner = new StringJoiner("&");
            joiner.add(OidcProtocolConstants.GRANT_TYPE + "="
                + OidcProtocolConstants.GRANT_TYPE_CLIENT_CREDENTIALS);
            joiner.add(OidcProtocolConstants.PARAM_CLIENT_ID + "="
                + URLEncoder.encode(context.getClientId(), charsetName));
            joiner.add(OidcProtocolConstants.PARAM_CLIENT_SECRET + "="
                + URLEncoder.encode(context.getClientSecret(), charsetName));
            if (context.getScope() != null && !context.getScope().isEmpty()) {
                joiner.add(OidcProtocolConstants.PARAM_SCOPE + "="
                    + URLEncoder.encode(context.getScope(), charsetName));
            }
            return joiner.toString();
        } catch (UnsupportedEncodingException e) {
            // Should never happen since UTF-8 is always supported
            throw new IllegalStateException("UTF-8 encoding not supported", e);
        }
    }
    
    /**
     * Check whether the token is expired or needs proactive refresh.
     *
     * <p>Proactive refresh occurs within a random window of [tokenTtl/15, tokenTtl/10]
     * before expiration (i.e., approximately 6.7%-10% of the token TTL before expiry).
     * This is consistent with the refresh strategy used by {@code NacosClientAuthServiceImpl}.
     *
     * @return true if the token should be refreshed
     */
    public boolean isExpiredOrNeedRefresh() {
        if (accessToken == null) {
            return true;
        }
        
        long elapsedMs = System.currentTimeMillis() - obtainedAtMs;
        long elapsedSeconds = elapsedMs / 1000;
        long refreshWindow = generateTokenRefreshWindow(expiresInSeconds);
        
        return elapsedSeconds >= (expiresInSeconds - refreshWindow);
    }
    
    /**
     * Generate a random refresh window, consistent with NacosClientAuthServiceImpl.
     *
     * @param tokenTtl the token TTL in seconds
     * @return the refresh window in seconds, range [tokenTtl/15, tokenTtl/10]
     */
    long generateTokenRefreshWindow(long tokenTtl) {
        if (tokenTtl <= 0) {
            return 0;
        }
        long startNumber = tokenTtl / 15;
        long endNumber = tokenTtl / 10;
        if (startNumber >= endNumber) {
            return startNumber;
        }
        return RandomUtils.nextLong(startNumber, endNumber);
    }
    
    /**
     * Get the current access token.
     *
     * @return the access token, or null if not yet obtained
     */
    public String getAccessToken() {
        return accessToken;
    }
    
    /**
     * Get the token TTL in seconds.
     *
     * @return expires_in value from the token response
     */
    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
    
    /**
     * Get the timestamp when the token was obtained.
     *
     * @return time in milliseconds
     */
    public long getObtainedAtMs() {
        return obtainedAtMs;
    }
}
