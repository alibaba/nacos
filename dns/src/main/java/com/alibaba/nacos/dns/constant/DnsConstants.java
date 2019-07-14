/*
 * Copyright (C) 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.nacos.dns.constant;

import com.alibaba.nacos.naming.misc.UtilsAndCommons;

public class DnsConstants {

    public static final String DEFAULT_CACHE_TIME_KEY = "defaultCacheTime";
    public static final String UPSTREAM_SERVERS_FOR_DOMAIN_SUFFIX_MAP_KEY = "upstreamServersForDomainSuffixMap";
    public static final String DEFAULT_UPSTREAM_SERVER_KEY = "defaultUpstreamServer";
    public static final String EDNS_ENABLED_KEY = "ednsEnabled";
    public static final String CNAME_KEY = "CNAME";
    /**
     * the url for dns
     */
    public static final String NACOS_DNS_CONTEXT =
        UtilsAndCommons.NACOS_SERVER_CONTEXT + UtilsAndCommons.NACOS_SERVER_VERSION + "/dns";
}
