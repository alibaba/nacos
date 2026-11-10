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

package com.alibaba.nacos.copilot.connection;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.function.Supplier;

/**
 * Validates connections at load time and marks the unusable ones with a diagnostic reason.
 *
 * <p>A broken connection never hides the healthy ones: validation returns a copy carrying the
 * result, and the caller keeps every connection in the table.
 *
 * @author nacos
 */
@Component
public class CopilotConnectionValidator {
    
    /**
     * Comma separated host suffixes that are allowed to use http or resolve to a private address.
     */
    public static final String ALLOWED_BASE_URLS_PROPERTY = "nacos.copilot.base-url.allowlist";
    
    private static final String HTTPS_SCHEME = "https";
    
    private static final String HTTP_SCHEME = "http";
    
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final String METADATA_ADDRESS = "100.100.100.200";
    
    private final CopilotModelResolver modelResolver;
    
    private final Supplier<String> allowlistSupplier;
    
    private final HostAddressResolver addressResolver;
    
    /**
     * Create the validator with the deployment configuration as the allowlist source.
     *
     * @param modelResolver resolver used to probe a connection
     */
    @Autowired
    public CopilotConnectionValidator(CopilotModelResolver modelResolver) {
        this(modelResolver, () -> EnvUtil.getProperty(ALLOWED_BASE_URLS_PROPERTY, ""),
            InetAddress::getAllByName);
    }
    
    CopilotConnectionValidator(CopilotModelResolver modelResolver,
        Supplier<String> allowlistSupplier, HostAddressResolver addressResolver) {
        this.modelResolver = modelResolver;
        this.allowlistSupplier = allowlistSupplier;
        this.addressResolver = addressResolver;
    }
    
    /**
     * Validate a connection without throwing.
     *
     * @param connection connection read from a configuration source
     * @return connection carrying the validation result
     */
    public CopilotModelConnection validate(CopilotModelConnection connection) {
        if (StringUtils.isBlank(connection.getProviderId())) {
            return unavailable(connection, "providerId is required");
        }
        if (StringUtils.isBlank(connection.getModel())) {
            return unavailable(connection, "model is required");
        }
        // The legacy configuration has always disabled Copilot without an explicit key, and
        // that must not change into an implicit upstream environment fallback.
        if (ConnectionSource.LEGACY == connection.getSource()
            && StringUtils.isBlank(connection.getApiKey())) {
            return unavailable(connection, "apiKey is not configured");
        }
        if (StringUtils.isNotBlank(connection.getBaseUrl())) {
            String baseUrlError = validateBaseUrl(connection.getBaseUrl());
            if (baseUrlError != null) {
                return unavailable(connection, baseUrlError);
            }
        }
        if (!modelResolver.canResolve(connection)) {
            return unavailable(connection, "no model provider for " + connection.getModelId()
                + " on the classpath");
        }
        try {
            modelResolver.resolve(connection);
        } catch (RuntimeException e) {
            String reason = StringUtils.isBlank(e.getMessage())
                ? e.getClass().getSimpleName() : e.getMessage();
            return unavailable(connection, reason);
        }
        return connection.withAvailability(true, null);
    }
    
    private String validateBaseUrl(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            return "baseUrl is not a valid URI";
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (StringUtils.isBlank(scheme) || StringUtils.isBlank(host)) {
            return "baseUrl must contain a scheme and a host";
        }
        if (!HTTPS_SCHEME.equalsIgnoreCase(scheme) && !HTTP_SCHEME.equalsIgnoreCase(scheme)) {
            return "baseUrl scheme must be http or https";
        }
        if (isAllowlisted(host)) {
            return null;
        }
        if (!HTTPS_SCHEME.equalsIgnoreCase(scheme)) {
            return "baseUrl must use https unless its host is added to "
                + ALLOWED_BASE_URLS_PROPERTY;
        }
        InetAddress[] addresses;
        try {
            addresses = addressResolver.resolve(host);
        } catch (UnknownHostException e) {
            return "baseUrl host [" + host + "] can not be resolved";
        }
        for (InetAddress address : addresses) {
            if (isPrivateOrLocal(address)) {
                return "baseUrl host [" + host + "] resolves to a private or local address; "
                    + "add it to " + ALLOWED_BASE_URLS_PROPERTY;
            }
        }
        return null;
    }
    
    private boolean isAllowlisted(String host) {
        String allowlist = allowlistSupplier.get();
        if (StringUtils.isBlank(allowlist)) {
            return false;
        }
        for (String entry : allowlist.split(",")) {
            String candidate = entry.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (host.equalsIgnoreCase(candidate)
                || host.toLowerCase().endsWith("." + candidate.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isPrivateOrLocal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) {
            return true;
        }
        return METADATA_ADDRESS.equals(address.getHostAddress());
    }
    
    private CopilotModelConnection unavailable(CopilotModelConnection connection, String reason) {
        return connection.withAvailability(false, reason);
    }
    
    /**
     * Resolves a host to all of its addresses, abstracted for testing.
     */
    @FunctionalInterface
    interface HostAddressResolver {
        
        /**
         * Resolve a host name.
         *
         * @param host host name
         * @return resolved addresses
         * @throws UnknownHostException when the host can not be resolved
         */
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
