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

package com.alibaba.nacos.api.ai.utils;

import com.alibaba.nacos.api.ai.model.agent.Endpoint;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonicalizes public Endpoint values without mutating caller-owned objects.
 *
 * @author Nacos
 */
public final class EndpointCanonicalizer {
    
    private static final int MAX_URI_LENGTH = 2048;
    
    private static final int MIN_PORT = 1;
    
    private static final int MAX_PORT = 65535;
    
    private static final int DEFAULT_PRIORITY = 0;
    
    private static final double DEFAULT_WEIGHT = 1D;
    
    private EndpointCanonicalizer() {
    }
    
    /**
     * Return a canonical copy of an Endpoint. Defaults are materialized and metadata keys are sorted.
     *
     * @param endpoint source Endpoint
     * @return canonical Endpoint copy
     * @throws IllegalArgumentException when the Endpoint is invalid
     */
    public static Endpoint canonicalize(Endpoint endpoint) {
        if (endpoint == null) {
            throw new IllegalArgumentException("Endpoint must not be null");
        }
        CanonicalEndpointUri canonicalUri = parseUri(endpoint.getUri());
        AgentValidationUtils.validateTransport(endpoint.getTransport());
        
        Integer priority = endpoint.getPriority();
        if (priority == null) {
            priority = DEFAULT_PRIORITY;
        } else if (priority < 0) {
            throw new IllegalArgumentException("Endpoint priority must not be negative");
        }
        Double weight = endpoint.getWeight();
        if (weight == null) {
            weight = DEFAULT_WEIGHT;
        } else if (weight.isNaN() || weight.isInfinite() || weight < 0D || weight > 10000D) {
            throw new IllegalArgumentException("Endpoint weight must be between 0 and 10000");
        }
        AgentValidationUtils.validateEndpointMetadata(endpoint.getMetadata());
        
        Endpoint result = new Endpoint();
        result.setUri(canonicalUri.getUri());
        result.setTransport(endpoint.getTransport());
        result.setPriority(priority);
        result.setWeight(weight);
        result.setHealthy(endpoint.getHealthy());
        if (endpoint.getMetadata() != null && !endpoint.getMetadata().isEmpty()) {
            Map<String, String> sorted = new TreeMap<String, String>(endpoint.getMetadata());
            result.setMetadata(new LinkedHashMap<String, String>(sorted));
        }
        return result;
    }
    
    /**
     * Canonicalize an Endpoint URI.
     *
     * @param uri Endpoint URI
     * @return URI with canonical scheme and host and an explicit effective port
     * @throws IllegalArgumentException when the URI is invalid
     */
    public static String canonicalizeUri(String uri) {
        return parseUri(uri).getUri();
    }
    
    /**
     * Return the canonical host of an Endpoint URI, without IPv6 brackets.
     *
     * @param uri Endpoint URI
     * @return canonical host
     * @throws IllegalArgumentException when the URI is invalid
     */
    public static String normalizedHost(String uri) {
        return parseUri(uri).getHost();
    }
    
    /**
     * Return the explicit or inferred effective port of an Endpoint URI.
     *
     * @param uri Endpoint URI
     * @return effective port
     * @throws IllegalArgumentException when the URI is invalid
     */
    public static int effectivePort(String uri) {
        return parseUri(uri).getPort();
    }
    
    static CanonicalEndpointUri parseUri(String value) {
        if (value == null || value.isEmpty()
            || value.codePointCount(0, value.length()) > MAX_URI_LENGTH) {
            throw invalidUri(value);
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw invalidUri(value, e);
        }
        if (!uri.isAbsolute() || uri.isOpaque() || uri.getScheme() == null
            || uri.getRawAuthority() == null
            || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            throw invalidUri(value);
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        Authority authority = parseAuthority(uri.getRawAuthority(), value);
        String host = authority.ipv6 ? normalizeIpv6(authority.host, value)
            : normalizeNonIpv6(authority.host, value);
        int port =
            authority.port == null ? defaultPort(scheme, value) : parsePort(authority.port, value);
        String formattedHost = authority.ipv6 ? '[' + host + ']' : host;
        StringBuilder canonical = new StringBuilder(value.length() + 8).append(scheme).append("://")
            .append(formattedHost).append(':').append(port);
        if (uri.getRawPath() != null) {
            canonical.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            canonical.append('?').append(uri.getRawQuery());
        }
        if (canonical.codePointCount(0, canonical.length()) > MAX_URI_LENGTH) {
            throw invalidUri(value);
        }
        return new CanonicalEndpointUri(canonical.toString(), host, port);
    }
    
    private static Authority parseAuthority(String rawAuthority, String originalUri) {
        if (rawAuthority.isEmpty() || rawAuthority.indexOf('@') >= 0) {
            throw invalidUri(originalUri);
        }
        if (rawAuthority.charAt(0) == '[') {
            int closingBracket = rawAuthority.indexOf(']');
            if (closingBracket < 0 || closingBracket == 1) {
                throw invalidUri(originalUri);
            }
            String suffix = rawAuthority.substring(closingBracket + 1);
            String port = null;
            if (!suffix.isEmpty()) {
                if (suffix.charAt(0) != ':' || suffix.length() == 1) {
                    throw invalidUri(originalUri);
                }
                port = suffix.substring(1);
            }
            return new Authority(rawAuthority.substring(1, closingBracket), port, true);
        }
        int colon = rawAuthority.lastIndexOf(':');
        if (colon >= 0) {
            if (colon == 0 || colon != rawAuthority.indexOf(':')
                || colon == rawAuthority.length() - 1) {
                throw invalidUri(originalUri);
            }
            return new Authority(rawAuthority.substring(0, colon),
                rawAuthority.substring(colon + 1), false);
        }
        return new Authority(rawAuthority, null, false);
    }
    
    private static String normalizeNonIpv6(String host, String originalUri) {
        if (isIpv4Candidate(host)) {
            return normalizeIpv4(host, originalUri);
        }
        try {
            String asciiHost = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
            if (asciiHost.isEmpty()) {
                throw invalidUri(originalUri);
            }
            return asciiHost.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw invalidUri(originalUri, e);
        }
    }
    
    private static boolean isIpv4Candidate(String host) {
        int dots = 0;
        for (int i = 0; i < host.length(); i++) {
            char current = host.charAt(i);
            if (current == '.') {
                dots++;
            } else if (current < '0' || current > '9') {
                return false;
            }
        }
        return dots == 3;
    }
    
    private static String normalizeIpv4(String host, String originalUri) {
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4) {
            throw invalidUri(originalUri);
        }
        StringBuilder result = new StringBuilder(host.length());
        for (int i = 0; i < octets.length; i++) {
            if (octets[i].isEmpty() || octets[i].length() > 3) {
                throw invalidUri(originalUri);
            }
            int value;
            try {
                value = Integer.parseInt(octets[i]);
            } catch (NumberFormatException e) {
                throw invalidUri(originalUri, e);
            }
            if (value > 255) {
                throw invalidUri(originalUri);
            }
            if (i > 0) {
                result.append('.');
            }
            result.append(value);
        }
        return result.toString();
    }
    
    private static String normalizeIpv6(String host, String originalUri) {
        if (host.indexOf('%') >= 0) {
            throw invalidUri(originalUri);
        }
        int doubleColon = host.indexOf("::");
        if (doubleColon >= 0 && doubleColon != host.lastIndexOf("::")) {
            throw invalidUri(originalUri);
        }
        List<Integer> left;
        List<Integer> right;
        if (doubleColon >= 0) {
            left = parseIpv6Section(host.substring(0, doubleColon), originalUri);
            right = parseIpv6Section(host.substring(doubleColon + 2), originalUri);
            int zeroCount = 8 - left.size() - right.size();
            if (zeroCount < 1) {
                throw invalidUri(originalUri);
            }
            for (int i = 0; i < zeroCount; i++) {
                left.add(0);
            }
            left.addAll(right);
        } else {
            left = parseIpv6Section(host, originalUri);
            if (left.size() != 8) {
                throw invalidUri(originalUri);
            }
        }
        return formatIpv6(left);
    }
    
    private static List<Integer> parseIpv6Section(String section, String originalUri) {
        List<Integer> words = new ArrayList<Integer>();
        if (section.isEmpty()) {
            return words;
        }
        String[] parts = section.split(":", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                throw invalidUri(originalUri);
            }
            if (part.indexOf('.') >= 0) {
                if (i != parts.length - 1) {
                    throw invalidUri(originalUri);
                }
                String ipv4 = normalizeIpv4(part, originalUri);
                String[] octets = ipv4.split("\\.");
                words.add(Integer.parseInt(octets[0]) << 8 | Integer.parseInt(octets[1]));
                words.add(Integer.parseInt(octets[2]) << 8 | Integer.parseInt(octets[3]));
            } else {
                if (part.length() > 4 || !isHex(part)) {
                    throw invalidUri(originalUri);
                }
                words.add(Integer.parseInt(part, 16));
            }
        }
        return words;
    }
    
    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!((current >= '0' && current <= '9') || (current >= 'a' && current <= 'f')
                || (current >= 'A' && current <= 'F'))) {
                return false;
            }
        }
        return true;
    }
    
    private static String formatIpv6(List<Integer> words) {
        int bestStart = -1;
        int bestLength = 0;
        for (int i = 0; i < words.size();) {
            if (words.get(i) != 0) {
                i++;
                continue;
            }
            int end = i;
            while (end < words.size() && words.get(end) == 0) {
                end++;
            }
            int length = end - i;
            if (length >= 2 && length > bestLength) {
                bestStart = i;
                bestLength = length;
            }
            i = end;
        }
        if (bestStart < 0) {
            return joinIpv6Words(words, 0, words.size());
        }
        String prefix = joinIpv6Words(words, 0, bestStart);
        String suffix = joinIpv6Words(words, bestStart + bestLength, words.size());
        return prefix + "::" + suffix;
    }
    
    private static String joinIpv6Words(List<Integer> words, int start, int end) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (result.length() > 0) {
                result.append(':');
            }
            result.append(Integer.toHexString(words.get(i)));
        }
        return result.toString();
    }
    
    private static int defaultPort(String scheme, String originalUri) {
        if ("http".equals(scheme) || "ws".equals(scheme)) {
            return 80;
        }
        if ("https".equals(scheme) || "wss".equals(scheme)) {
            return 443;
        }
        throw invalidUri(originalUri);
    }
    
    private static int parsePort(String value, String originalUri) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current < '0' || current > '9') {
                throw invalidUri(originalUri);
            }
        }
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw invalidUri(originalUri, e);
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            throw invalidUri(originalUri);
        }
        return port;
    }
    
    private static IllegalArgumentException invalidUri(String value) {
        return new IllegalArgumentException("Invalid Endpoint URI: " + value);
    }
    
    private static IllegalArgumentException invalidUri(String value, Exception cause) {
        return new IllegalArgumentException("Invalid Endpoint URI: " + value, cause);
    }
    
    static final class CanonicalEndpointUri {
        
        private final String uri;
        
        private final String host;
        
        private final int port;
        
        private CanonicalEndpointUri(String uri, String host, int port) {
            this.uri = uri;
            this.host = host;
            this.port = port;
        }
        
        String getUri() {
            return uri;
        }
        
        String getHost() {
            return host;
        }
        
        int getPort() {
            return port;
        }
    }
    
    private static final class Authority {
        
        private final String host;
        
        private final String port;
        
        private final boolean ipv6;
        
        private Authority(String host, String port, boolean ipv6) {
            this.host = host;
            this.port = port;
            this.ipv6 = ipv6;
        }
    }
}
