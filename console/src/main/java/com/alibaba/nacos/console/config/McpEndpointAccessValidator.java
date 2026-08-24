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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Validates the network target used when the console imports tools from an MCP endpoint.
 *
 * @author Nacos
 */
@Component
public class McpEndpointAccessValidator {
    
    public static final String IMPORT_ENABLED_PROPERTY =
        "nacos.console.ai.mcp.import.enabled";
    
    public static final String ALLOWED_PRIVATE_ADDRESSES_PROPERTY =
        "nacos.console.ai.mcp.import.allowed-private-addresses";
    
    private static final String ALLOWLIST_EXAMPLE = "192.168.0.0/16";
    
    private static final String HTTP_SCHEME = "http";
    
    private static final String HTTPS_SCHEME = "https";
    
    private final BooleanSupplier enabledSupplier;
    
    private final Supplier<String> privateAllowlistSupplier;
    
    private final HostAddressResolver addressResolver;
    
    public McpEndpointAccessValidator() {
        this(() -> EnvUtil.getProperty(IMPORT_ENABLED_PROPERTY, Boolean.class, true),
            () -> EnvUtil.getProperty(ALLOWED_PRIVATE_ADDRESSES_PROPERTY, ""),
            InetAddress::getAllByName);
    }
    
    McpEndpointAccessValidator(BooleanSupplier enabledSupplier,
        Supplier<String> privateAllowlistSupplier,
        HostAddressResolver addressResolver) {
        this.enabledSupplier = Objects.requireNonNull(enabledSupplier, "enabledSupplier");
        this.privateAllowlistSupplier = Objects.requireNonNull(privateAllowlistSupplier,
            "privateAllowlistSupplier");
        this.addressResolver = Objects.requireNonNull(addressResolver, "addressResolver");
    }
    
    /**
     * Validate that the requested MCP endpoint is safe to access under the outbound policy.
     *
     * @param baseUrl  MCP base URL
     * @param endpoint MCP transport endpoint
     * @throws SecurityException if import is disabled, the allowlist is invalid, or a private
     *                           target is not allowed
     * @throws IllegalArgumentException if the requested URL or endpoint is invalid
     */
    public void validate(String baseUrl, String endpoint) {
        if (!enabledSupplier.getAsBoolean()) {
            throw new SecurityException("MCP tool import is disabled by '"
                + IMPORT_ENABLED_PROPERTY + "'. A Nacos cluster administrator must enable "
                + "this setting before importing tools.");
        }
        List<IpAddressRange> allowedPrivateRanges = parsePrivateAllowlist(
            privateAllowlistSupplier.get());
        URI baseUri = parseBaseUri(baseUrl);
        validateEndpoint(endpoint);
        InetAddress[] resolvedAddresses = resolveAddresses(baseUri.getHost());
        for (InetAddress resolvedAddress : resolvedAddresses) {
            if (isPrivateOrLocalAddress(resolvedAddress)
                && !isAllowed(resolvedAddress, allowedPrivateRanges)) {
                throw new SecurityException("MCP endpoint host '" + baseUri.getHost()
                    + "' resolves to private or local address '"
                    + resolvedAddress.getHostAddress() + "'. Public addresses are allowed by "
                    + "default; ask a Nacos cluster administrator to add this trusted private "
                    + "address or CIDR range to '" + ALLOWED_PRIVATE_ADDRESSES_PROPERTY + "'.");
            }
        }
    }
    
    private List<IpAddressRange> parsePrivateAllowlist(String configuredAllowlist) {
        if (StringUtils.isBlank(configuredAllowlist)) {
            return new ArrayList<>();
        }
        List<IpAddressRange> result = new ArrayList<>();
        for (String configuredEntry : configuredAllowlist.split(",", -1)) {
            String entry = configuredEntry.trim();
            if (entry.isEmpty()) {
                throw invalidAllowlistEntry(configuredEntry);
            }
            try {
                result.add(IpAddressRange.parse(entry));
            } catch (IllegalArgumentException e) {
                throw invalidAllowlistEntry(entry);
            }
        }
        return result;
    }
    
    private SecurityException invalidAllowlistEntry(String entry) {
        return new SecurityException("MCP tool import is blocked because '"
            + ALLOWED_PRIVATE_ADDRESSES_PROPERTY + "' contains invalid entry '" + entry
            + "'. Configure only private IP addresses or CIDR ranges (for example, '"
            + ALLOWLIST_EXAMPLE + "') and restart the Nacos Console process.");
    }
    
    private URI parseBaseUri(String baseUrl) {
        final URI result;
        try {
            result = new URI(baseUrl);
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException(
                "MCP baseUrl must be an absolute HTTP or HTTPS URL with a valid host.", e);
        }
        String scheme = result.getScheme();
        if (!result.isAbsolute() || StringUtils.isBlank(result.getHost())
            || !HTTP_SCHEME.equalsIgnoreCase(scheme) && !HTTPS_SCHEME.equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                "MCP baseUrl must be an absolute HTTP or HTTPS URL with a valid host.");
        }
        if (result.getRawUserInfo() != null) {
            throw new IllegalArgumentException("MCP baseUrl must not contain user information.");
        }
        if (result.getPort() > 65535) {
            throw new IllegalArgumentException("MCP baseUrl contains an invalid port.");
        }
        return result;
    }
    
    private void validateEndpoint(String endpoint) {
        if (StringUtils.isBlank(endpoint)) {
            throw new IllegalArgumentException("MCP endpoint must not be empty.");
        }
        final URI endpointUri;
        try {
            endpointUri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("MCP endpoint must be a valid relative URI path.",
                e);
        }
        if (endpointUri.isAbsolute() || endpointUri.getRawAuthority() != null) {
            throw new IllegalArgumentException("MCP endpoint must be a relative URI path and must "
                + "not override the baseUrl scheme or host.");
        }
    }
    
    private InetAddress[] resolveAddresses(String host) {
        final InetAddress[] result;
        try {
            result = addressResolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("MCP endpoint host '" + host
                + "' could not be resolved. No connection was attempted.", e);
        }
        if (result == null || result.length == 0) {
            throw new IllegalArgumentException("MCP endpoint host '" + host
                + "' did not resolve to any IP address. No connection was attempted.");
        }
        return result;
    }
    
    private boolean isAllowed(InetAddress address, List<IpAddressRange> allowedRanges) {
        for (IpAddressRange allowedRange : allowedRanges) {
            if (allowedRange.contains(address)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isPrivateOrLocalAddress(InetAddress address) {
        return address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress() || isUniqueLocalIpv6Address(address);
    }
    
    private boolean isUniqueLocalIpv6Address(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
    
    @FunctionalInterface
    interface HostAddressResolver {
        
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
    
    private static final class IpAddressRange {
        
        private final byte[] networkAddress;
        
        private final int prefixLength;
        
        private IpAddressRange(byte[] networkAddress, int prefixLength) {
            this.networkAddress = networkAddress;
            this.prefixLength = prefixLength;
        }
        
        private static IpAddressRange parse(String value) {
            String[] parts = value.split("/", -1);
            if (parts.length > 2 || StringUtils.isBlank(parts[0])) {
                throw new IllegalArgumentException("Invalid IP address range: " + value);
            }
            String addressValue = removeIpv6Brackets(parts[0]);
            if (addressValue.contains("%") || !InternetAddressUtil.isIp(addressValue)) {
                throw new IllegalArgumentException("Invalid IP address range: " + value);
            }
            byte[] address = parseAddress(addressValue);
            int maxPrefixLength = address.length * Byte.SIZE;
            int prefixLength = parts.length == 1
                ? maxPrefixLength : parsePrefixLength(parts[1], maxPrefixLength, value);
            return new IpAddressRange(address, prefixLength);
        }
        
        private static String removeIpv6Brackets(String value) {
            if (value.startsWith("[") && value.endsWith("]")) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
        
        private static byte[] parseAddress(String value) {
            try {
                return InetAddress.getByName(value).getAddress();
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid IP address: " + value, e);
            }
        }
        
        private static int parsePrefixLength(String value, int maxPrefixLength, String range) {
            final int result;
            try {
                result = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid prefix length: " + range, e);
            }
            if (result < 0 || result > maxPrefixLength) {
                throw new IllegalArgumentException("Invalid prefix length: " + range);
            }
            return result;
        }
        
        private boolean contains(InetAddress candidate) {
            byte[] candidateAddress = candidate.getAddress();
            if (candidateAddress.length != networkAddress.length) {
                return false;
            }
            int fullBytes = prefixLength / Byte.SIZE;
            for (int i = 0; i < fullBytes; i++) {
                if (candidateAddress[i] != networkAddress[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % Byte.SIZE;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (Byte.SIZE - remainingBits)) & 0xFF;
            return (candidateAddress[fullBytes] & mask) == (networkAddress[fullBytes] & mask);
        }
    }
}
