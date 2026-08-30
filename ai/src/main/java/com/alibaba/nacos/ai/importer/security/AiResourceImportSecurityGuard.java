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
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Central guard for import artifacts crossing the plugin boundary.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
public class AiResourceImportSecurityGuard {
    
    private static final String HTTPS_SCHEME = "https";
    
    private static final String HTTP_SCHEME = "http";
    
    private static final String LOCALHOST = "localhost";
    
    private static final String LOCALHOST_SUFFIX = ".localhost";
    
    /**
     * Check artifact type and size before validation or import.
     *
     * @param maxArtifactSize maximum accepted payload size
     * @param expectedResourceType expected resource type
     * @param artifact fetched artifact
     * @throws NacosException if the artifact violates the import boundary
     */
    public void checkArtifact(long maxArtifactSize, String expectedResourceType,
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
        if (maxArtifactSize > 0 && payloadSize > maxArtifactSize) {
            throw invalid("AI resource import artifact size exceeds plugin limit.");
        }
    }
    
    /**
     * Check a user provided URL before the server fetches it, e.g. through the
     * legacy MCP import path.
     *
     * <p>The legacy path has no operator configured source to hold per-source
     * network policies, so hosts resolving to private or local targets are
     * always rejected; internal registries should be imported through
     * configured import sources instead.
     *
     * @param endpoint user provided URL
     * @throws NacosException if the endpoint violates the import boundary
     */
    public void checkUserEndpoint(String endpoint) throws NacosException {
        if (StringUtils.isBlank(endpoint)) {
            return;
        }
        URI parsed;
        try {
            parsed = URI.create(endpoint.trim());
        } catch (IllegalArgumentException e) {
            throw invalid("AI resource import request URL is invalid.");
        }
        String scheme = parsed.getScheme() == null ? null
            : parsed.getScheme().toLowerCase(Locale.ENGLISH);
        if (!HTTPS_SCHEME.equals(scheme) && !HTTP_SCHEME.equals(scheme)) {
            throw invalid("AI resource import request URL must use http or https.");
        }
        if (StringUtils.isBlank(parsed.getHost())) {
            throw invalid("AI resource import request URL host must not be empty.");
        }
        if (isUnsafeHost(parsed.getHost())) {
            throw invalid(
                "AI resource import request URL resolves to a private or local target.");
        }
    }
    
    private boolean isUnsafeHost(String host) throws NacosException {
        String normalized = InternetAddressUtil.removeBrackets(host).toLowerCase(Locale.ENGLISH);
        if (LOCALHOST.equals(normalized) || normalized.endsWith(LOCALHOST_SUFFIX)) {
            return true;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(normalized);
        } catch (UnknownHostException e) {
            throw invalid("AI resource import request URL host can not be resolved.");
        }
        for (InetAddress each : addresses) {
            if (each.isAnyLocalAddress() || each.isLoopbackAddress()
                || each.isLinkLocalAddress() || each.isSiteLocalAddress()
                || each.isMulticastAddress() || isUniqueLocalIpv6Address(each)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isUniqueLocalIpv6Address(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
    
    private NacosException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
}
