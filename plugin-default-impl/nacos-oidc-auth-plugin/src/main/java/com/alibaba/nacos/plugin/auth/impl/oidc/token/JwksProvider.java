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

package com.alibaba.nacos.plugin.auth.impl.oidc.token;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcProviderMetadataProvider;
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
 * Provider for fetching and caching JWKS from an OIDC Provider.
 *
 * @author WangzJi
 */
public class JwksProvider {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(JwksProvider.class);
    
    private static final String CACHE_KEY = "jwks";
    
    private final OidcProviderMetadataProvider metadataProvider;
    
    private final HttpClient httpClient;
    
    private final Cache<String, JWKSet> jwksCache;
    
    public JwksProvider(OidcAuthPluginConfig config,
        OidcProviderMetadataProvider metadataProvider) {
        this(config, metadataProvider,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }
    
    JwksProvider(OidcAuthPluginConfig config, OidcProviderMetadataProvider metadataProvider,
        HttpClient httpClient) {
        this.metadataProvider = metadataProvider;
        this.httpClient = httpClient;
        this.jwksCache = Caffeine.newBuilder()
            .expireAfterWrite(config.getJwksCacheTtlSeconds(), TimeUnit.SECONDS)
            .maximumSize(1).build();
    }
    
    /**
     * Get JWKS from cache or fetch it from the Provider.
     *
     * @return JWK set
     * @throws IOException if fetching fails
     */
    public JWKSet getJwkSet() throws IOException {
        JWKSet cached = jwksCache.getIfPresent(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = jwksCache.getIfPresent(CACHE_KEY);
            if (cached == null) {
                cached = fetchJwkSet();
                jwksCache.put(CACHE_KEY, cached);
            }
            return cached;
        }
    }
    
    /**
     * Force a JWKS refresh for key rotation recovery.
     *
     * @return refreshed JWK set
     * @throws IOException if fetching fails
     */
    public JWKSet refreshJwkSet() throws IOException {
        jwksCache.invalidateAll();
        return getJwkSet();
    }
    
    private JWKSet fetchJwkSet() throws IOException {
        String jwksUri = metadataProvider.getMetadata().getJwksUri();
        if (StringUtils.isBlank(jwksUri)) {
            throw new IOException("JWKS URI is not configured or discovered");
        }
        LOGGER.info("Fetching JWKS from: {}", jwksUri);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(jwksUri))
            .header("Accept", "application/json").GET().build();
        try {
            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != OidcProtocolConstants.HTTP_STATUS_OK) {
                throw new IOException("Failed to fetch JWKS, status: " + response.statusCode());
            }
            JWKSet result = JWKSet.parse(response.body());
            LOGGER.info("Successfully fetched JWKS with {} keys", result.getKeys().size());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("JWKS fetch interrupted", e);
        } catch (ParseException e) {
            throw new IOException("Failed to parse JWKS", e);
        }
    }
    
    /**
     * Clear the cached JWK set.
     */
    public void clearCache() {
        jwksCache.invalidateAll();
    }
}
