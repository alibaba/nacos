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

package com.alibaba.nacos.plugin.auth.impl.oidc.token;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.jwk.JWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Provider for fetching and caching JWKS (JSON Web Key Set) from OIDC provider.
 *
 * @author WangzJi
 */
public class JwksProvider {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(JwksProvider.class);
    
    private static final String CACHE_KEY = "jwks";
    
    private static volatile JwksProvider instance;
    
    private final OidcAuthConfig config;
    
    private final HttpClient httpClient;
    
    private final Cache<String, JWKSet> jwksCache;
    
    private volatile String jwksUri;
    
    private JwksProvider() {
        this.config = OidcAuthConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.jwksCache = Caffeine.newBuilder()
            .expireAfterWrite(config.getJwksCacheTtlSeconds(), TimeUnit.SECONDS)
            .maximumSize(1)
            .build();
    }
    
    /**
     * Get singleton instance.
     *
     * @return JwksProvider instance
     */
    public static JwksProvider getInstance() {
        if (instance == null) {
            synchronized (JwksProvider.class) {
                if (instance == null) {
                    instance = new JwksProvider();
                }
            }
        }
        return instance;
    }
    
    /**
     * Get JWKS from cache or fetch from provider.
     *
     * @return JWKSet
     * @throws IOException if fetching fails
     */
    public JWKSet getJwkSet() throws IOException {
        JWKSet cached = jwksCache.getIfPresent(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        
        synchronized (this) {
            cached = jwksCache.getIfPresent(CACHE_KEY);
            if (cached != null) {
                return cached;
            }
            
            JWKSet jwkSet = fetchJwkSet();
            jwksCache.put(CACHE_KEY, jwkSet);
            return jwkSet;
        }
    }
    
    /**
     * Force refresh JWKS cache.
     *
     * @return refreshed JWKSet
     * @throws IOException if fetching fails
     */
    public JWKSet refreshJwkSet() throws IOException {
        jwksCache.invalidateAll();
        return getJwkSet();
    }
    
    /**
     * Fetch JWKS from the provider's JWKS endpoint.
     *
     * @return JWKSet
     * @throws IOException if fetching fails
     */
    private JWKSet fetchJwkSet() throws IOException {
        String uri = getJwksUri();
        if (StringUtils.isBlank(uri)) {
            throw new IOException("JWKS URI is not configured or discovered");
        }
        
        LOGGER.info("Fetching JWKS from: {}", uri);
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Accept", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != OidcProtocolConstants.HTTP_STATUS_OK) {
                throw new IOException("Failed to fetch JWKS, status: " + response.statusCode());
            }
            
            JWKSet jwkSet = JWKSet.parse(response.body());
            LOGGER.info("Successfully fetched JWKS with {} keys", jwkSet.getKeys().size());
            return jwkSet;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("JWKS fetch interrupted", e);
        } catch (ParseException e) {
            throw new IOException("Failed to parse JWKS", e);
        }
    }
    
    /**
     * Get JWKS URI, discovering from OIDC configuration if needed.
     *
     * @return JWKS URI
     * @throws IOException if discovery fails
     */
    private String getJwksUri() throws IOException {
        // Check if already discovered or configured
        if (StringUtils.isNotBlank(jwksUri)) {
            return jwksUri;
        }
        
        // Check if directly configured
        String configuredJwksUri = config.getJwksUri();
        if (StringUtils.isNotBlank(configuredJwksUri)) {
            this.jwksUri = configuredJwksUri;
            return jwksUri;
        }
        
        // Discover from OIDC well-known configuration
        String issuerUri = config.getIssuerUri();
        if (StringUtils.isBlank(issuerUri)) {
            throw new IOException("Issuer URI is not configured");
        }
        
        discoverOidcConfiguration(issuerUri);
        return jwksUri;
    }
    
    /**
     * Discover OIDC configuration from well-known endpoint.
     *
     * @param issuerUri OIDC issuer URI
     * @throws IOException if discovery fails
     */
    private void discoverOidcConfiguration(String issuerUri) throws IOException {
        String discoveryUrl = issuerUri.endsWith("/")
            ? issuerUri + ".well-known/openid-configuration"
            : issuerUri + OidcProtocolConstants.WELL_KNOWN_PATH;
        
        LOGGER.info("Discovering OIDC configuration from: {}", discoveryUrl);
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(discoveryUrl))
                .header("Accept", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != OidcProtocolConstants.HTTP_STATUS_OK) {
                throw new IOException(
                    "Failed to discover OIDC configuration, status: " + response.statusCode());
            }
            
            JsonNode root = JacksonUtils.toObj(response.body());
            if (root != null) {
                if (root.has(OidcProtocolConstants.DISCOVERY_JWKS_URI)) {
                    this.jwksUri = root.get(OidcProtocolConstants.DISCOVERY_JWKS_URI).asText();
                    config.setJwksUri(jwksUri);
                }
                if (root.has(OidcProtocolConstants.DISCOVERY_AUTHORIZATION_ENDPOINT)) {
                    config.setAuthorizationEndpoint(
                        root.get(OidcProtocolConstants.DISCOVERY_AUTHORIZATION_ENDPOINT).asText());
                }
                if (root.has(OidcProtocolConstants.DISCOVERY_TOKEN_ENDPOINT)) {
                    config.setTokenEndpoint(
                        root.get(OidcProtocolConstants.DISCOVERY_TOKEN_ENDPOINT).asText());
                }
                if (root.has(OidcProtocolConstants.DISCOVERY_USERINFO_ENDPOINT)) {
                    config.setUserinfoEndpoint(
                        root.get(OidcProtocolConstants.DISCOVERY_USERINFO_ENDPOINT).asText());
                }
                if (root.has(OidcProtocolConstants.DISCOVERY_END_SESSION_ENDPOINT)) {
                    config.setEndSessionEndpoint(
                        root.get(OidcProtocolConstants.DISCOVERY_END_SESSION_ENDPOINT).asText());
                }
            }
            
            LOGGER.info("OIDC configuration discovered: jwksUri={}", jwksUri);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OIDC discovery interrupted", e);
        } catch (Exception e) {
            LOGGER.error("Failed to parse OIDC configuration", e);
            throw new IOException("Failed to parse OIDC configuration", e);
        }
    }
    
    /**
     * Clear the JWKS cache.
     */
    public void clearCache() {
        jwksCache.invalidateAll();
        jwksUri = null;
    }
}
