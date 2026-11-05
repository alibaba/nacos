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

package com.alibaba.nacos.airegistry.service.ard;

import com.alibaba.nacos.common.utils.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks configured trust declarations without resolving identities or verifying ownership.
 *
 * @author nacos
 */
final class ArdTrustIdentityValidator {
    
    private static final Pattern DNS_LABEL =
        Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    
    private static final Pattern SPIFFE_DOMAIN = Pattern.compile("[a-z0-9._-]+");
    
    private static final Pattern DID_WEB_SEGMENT =
        Pattern.compile("(?:[A-Za-z0-9._-]|%[0-9A-Fa-f]{2})+");
    
    private ArdTrustIdentityValidator() {
    }
    
    static String normalizePublisher(String publisher) {
        String domain = withoutRootDot(publisher).toLowerCase(Locale.ROOT);
        String[] labels = domain.split("\\.", -1);
        boolean valid = domain.length() <= 253 && labels.length >= 2
            && !labels[labels.length - 1].matches("[0-9]+");
        for (String label : labels) {
            valid &= DNS_LABEL.matcher(label).matches();
        }
        if (!valid) {
            throw new IllegalStateException("ARD Registry publisher must be an ASCII FQDN");
        }
        return domain;
    }
    
    static void validate(String publisher, String identity) {
        if (StringUtils.isBlank(identity)) {
            return;
        }
        String lower = identity.strip().toLowerCase(Locale.ROOT);
        String authority;
        boolean dnsHost = true;
        try {
            if (lower.startsWith("https:")) {
                authority = httpsHost(new URI(identity));
            } else if (lower.startsWith("spiffe:")) {
                URI uri = new URI(identity);
                authority = uri.getRawAuthority();
                if (authority == null || !SPIFFE_DOMAIN.matcher(authority).matches()
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                    throw invalidIdentity();
                }
                dnsHost = false;
            } else if (lower.equals("did:web") || lower.startsWith("did:web:")) {
                if (!identity.startsWith("did:web:")) {
                    throw invalidIdentity();
                }
                String[] segments = identity.substring("did:web:".length()).split(":", -1);
                for (String segment : segments) {
                    if (!DID_WEB_SEGMENT.matcher(segment).matches()) {
                        throw invalidIdentity();
                    }
                }
                String domain = segments[0].replaceAll("(?i)%3a", ":");
                authority = httpsHost(new URI("https://" + domain));
                if (authority.matches("[0-9.]+")) {
                    throw invalidIdentity();
                }
            } else {
                // The optional identityType hint does not establish a verification method.
                return;
            }
        } catch (URISyntaxException e) {
            throw invalidIdentity();
        }
        String expected = publisher.toLowerCase(Locale.ROOT);
        String actual = authority.toLowerCase(Locale.ROOT);
        if (dnsHost) {
            expected = withoutRootDot(expected);
            actual = withoutRootDot(actual);
        }
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                "ARD configured trust identity authority does not match the publisher");
        }
    }
    
    private static String httpsHost(URI uri) {
        String host = uri.getHost();
        String authority = uri.getRawAuthority();
        if (host == null || authority == null || uri.getRawUserInfo() != null
            || uri.getPort() > 65535 || authority.endsWith(":")) {
            throw invalidIdentity();
        }
        return host;
    }
    
    private static String withoutRootDot(String host) {
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }
    
    private static IllegalStateException invalidIdentity() {
        return new IllegalStateException("ARD configured trust identity has an invalid authority");
    }
}
