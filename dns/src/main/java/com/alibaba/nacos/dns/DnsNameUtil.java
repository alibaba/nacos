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

package com.alibaba.nacos.dns;

import java.util.Locale;

/**
 * DNS name utilities shared between the query handler and forwarder.
 *
 * <p>DNS names are case-insensitive for comparison purposes (RFC 4343), but the
 * original case must be preserved when the name is used to look up Nacos
 * services/groups, since Nacos service names may have their own case semantics.
 */
final class DnsNameUtil {
    
    private DnsNameUtil() {
    }
    
    /**
     * Test whether a fully-qualified or relative domain name belongs to the given
     * suffix, using case-insensitive comparison per RFC 4343.
     *
     * <p>Comparison uses {@link Locale#ROOT} to avoid JVM default-locale effects
     * (e.g. Turkish dotted-I). Matches both the bare suffix (e.g. {@code nacos})
     * and subdomains (e.g. {@code svc.nacos}). The original domain string is not
     * modified.
     *
     * @param domain the domain name (may or may not have a trailing dot)
     * @param suffix the configured Nacos DNS suffix (no leading dot, no trailing dot)
     * @return true if the domain equals the suffix or is a subdomain of it
     */
    static boolean matchesSuffix(String domain, String suffix) {
        if (domain == null || suffix == null || suffix.isEmpty()) {
            return false;
        }
        String d = domain;
        if (d.endsWith(".")) {
            d = d.substring(0, d.length() - 1);
        }
        String lowerDomain = d.toLowerCase(Locale.ROOT);
        String lowerSuffix = suffix.toLowerCase(Locale.ROOT);
        return lowerDomain.equals(lowerSuffix) || lowerDomain.endsWith("." + lowerSuffix);
    }
}
