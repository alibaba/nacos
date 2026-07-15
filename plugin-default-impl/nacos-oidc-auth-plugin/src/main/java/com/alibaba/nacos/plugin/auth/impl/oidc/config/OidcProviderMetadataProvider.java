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

package com.alibaba.nacos.plugin.auth.impl.oidc.config;

import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Lazily discovers and caches OIDC Provider metadata.
 *
 * @author Nacos
 */
public class OidcProviderMetadataProvider {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(OidcProviderMetadataProvider.class);
    
    private static final Duration DISCOVERY_TIMEOUT = Duration.ofSeconds(5);
    
    private final OidcAuthPluginConfig config;
    
    private final HttpClient httpClient;
    
    private volatile OidcProviderMetadata metadata;
    
    public OidcProviderMetadataProvider(OidcAuthPluginConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(DISCOVERY_TIMEOUT).build());
    }
    
    OidcProviderMetadataProvider(OidcAuthPluginConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }
    
    /**
     * Get cached metadata or perform discovery. Failed discovery is not cached.
     *
     * @return discovered provider metadata
     * @throws IOException if discovery fails
     */
    public OidcProviderMetadata getMetadata() throws IOException {
        OidcProviderMetadata result = metadata;
        if (result == null) {
            synchronized (this) {
                result = metadata;
                if (result == null) {
                    result = discover();
                    metadata = result;
                }
            }
        }
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private OidcProviderMetadata discover() throws IOException {
        String issuerUri = config.getIssuerUri();
        if (StringUtils.isBlank(issuerUri)) {
            throw new IOException("Issuer URI is not configured");
        }
        String discoveryUrl = trimTrailingSlash(issuerUri)
            + OidcProtocolConstants.WELL_KNOWN_PATH;
        LOGGER.info("Discovering OIDC configuration from: {}", discoveryUrl);
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(discoveryUrl))
                .header("Accept", "application/json").timeout(DISCOVERY_TIMEOUT).GET().build();
            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != OidcProtocolConstants.HTTP_STATUS_OK) {
                throw new IOException("Failed to discover OIDC configuration, status: "
                    + response.statusCode());
            }
            Map<String, Object> values = JsonUtils.toObj(response.body(), Map.class);
            if (values == null) {
                throw new IOException("OIDC discovery response is empty");
            }
            OidcProviderMetadata result = new OidcProviderMetadata(
                stringValue(values, OidcProtocolConstants.DISCOVERY_AUTHORIZATION_ENDPOINT),
                stringValue(values, OidcProtocolConstants.DISCOVERY_TOKEN_ENDPOINT),
                stringValue(values, OidcProtocolConstants.DISCOVERY_USERINFO_ENDPOINT),
                stringValue(values, OidcProtocolConstants.DISCOVERY_END_SESSION_ENDPOINT),
                stringValue(values, OidcProtocolConstants.DISCOVERY_JWKS_URI));
            LOGGER.info("OIDC configuration discovered: jwksUri={}", result.getJwksUri());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OIDC discovery interrupted", e);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Failed to parse OIDC configuration", e);
        }
    }
    
    private String stringValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }
    
    private String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
