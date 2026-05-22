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

package com.alibaba.nacos.ai.importer.security;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * Central guard for import artifacts crossing the plugin boundary.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
public class AiResourceImportSecurityGuard {
    
    public static final String PROPERTY_ALLOW_HTTP = "allow-http";
    
    public static final String PROPERTY_ALLOW_HTTP_CAMEL = "allowHttp";
    
    public static final String PROPERTY_ALLOW_PRIVATE_NETWORK = "allow-private-network";
    
    public static final String PROPERTY_ALLOW_PRIVATE_NETWORK_CAMEL = "allowPrivateNetwork";
    
    private static final String HTTPS_SCHEME = "https";
    
    private static final String HTTP_SCHEME = "http";
    
    private static final String LOCALHOST = "localhost";
    
    private static final String LOCALHOST_SUFFIX = ".localhost";
    
    /**
     * Check artifact type and size before validation or import.
     *
     * @param source resolved source
     * @param expectedResourceType expected resource type
     * @param artifact fetched artifact
     * @throws NacosException if the artifact violates the import boundary
     */
    public void checkArtifact(AiResourceImportSource source, String expectedResourceType,
        AiResourceImportArtifact artifact) throws NacosException {
        if (artifact == null) {
            throw invalid("AI resource import artifact must not be null.");
        }
        if (!StringUtils.equals(expectedResourceType, artifact.getResourceType())) {
            throw invalid("AI resource import artifact resource type mismatch.");
        }
        long payloadSize = 0;
        if (artifact.getPayload() != null) {
            payloadSize += artifact.getPayload().length;
        }
        if (artifact.getPayloadJson() != null) {
            payloadSize += artifact.getPayloadJson().length();
        }
        if (source.getMaxArtifactSize() > 0 && payloadSize > source.getMaxArtifactSize()) {
            throw invalid("AI resource import artifact size exceeds source limit.");
        }
    }
    
    /**
     * Check source endpoint before an importer makes network requests.
     *
     * @param source resolved source
     * @throws NacosException if the source endpoint violates the import boundary
     */
    public void checkSourceEndpoint(AiResourceImportSource source) throws NacosException {
        if (source == null || StringUtils.isBlank(source.getEndpoint())) {
            return;
        }
        URI endpoint = parseEndpoint(source.getEndpoint());
        String scheme = endpoint.getScheme() == null ? null
            : endpoint.getScheme().toLowerCase(Locale.ENGLISH);
        if (!HTTPS_SCHEME.equals(scheme) && !HTTP_SCHEME.equals(scheme)) {
            throw invalid("AI resource import source endpoint must use http or https.");
        }
        if (HTTP_SCHEME.equals(scheme) && !isSourcePropertyEnabled(source, PROPERTY_ALLOW_HTTP,
            PROPERTY_ALLOW_HTTP_CAMEL)) {
            throw invalid(
                "AI resource import source endpoint must use https unless allow-http is enabled.");
        }
        if (StringUtils.isBlank(endpoint.getHost())) {
            throw invalid("AI resource import source endpoint host must not be empty.");
        }
        if (isUnsafeHost(endpoint.getHost()) && !isSourcePropertyEnabled(source,
            PROPERTY_ALLOW_PRIVATE_NETWORK, PROPERTY_ALLOW_PRIVATE_NETWORK_CAMEL)) {
            throw invalid(
                "AI resource import source endpoint resolves to a private or local target.");
        }
    }
    
    private URI parseEndpoint(String endpoint) throws NacosException {
        try {
            URI result = URI.create(endpoint.trim());
            if (!result.isAbsolute()) {
                throw invalid("AI resource import source endpoint must be an absolute URL.");
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw invalid("AI resource import source endpoint is invalid.");
        }
    }
    
    private boolean isUnsafeHost(String host) throws NacosException {
        String normalized = InternetAddressUtil.removeBrackets(host).toLowerCase(Locale.ENGLISH);
        if (LOCALHOST.equals(normalized) || normalized.endsWith(LOCALHOST_SUFFIX)) {
            return true;
        }
        if (!InternetAddressUtil.isIp(normalized)) {
            return false;
        }
        try {
            return isUnsafeAddress(InetAddress.getByName(normalized));
        } catch (Exception e) {
            throw invalid("AI resource import source endpoint host is invalid.");
        }
    }
    
    private boolean isUnsafeAddress(InetAddress address) {
        return address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress() || isUniqueLocalIpv6Address(address);
    }
    
    private boolean isUniqueLocalIpv6Address(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
    
    private boolean isSourcePropertyEnabled(AiResourceImportSource source, String kebabKey,
        String camelKey) {
        Map<String, String> properties = source.getProperties();
        if (properties == null || properties.isEmpty()) {
            return false;
        }
        return Boolean.parseBoolean(properties.get(kebabKey))
            || Boolean.parseBoolean(properties.get(camelKey));
    }
    
    private NacosException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
}
