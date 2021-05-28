/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.consistency;

import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.lang3.StringUtils;

import static com.alibaba.nacos.naming.misc.UtilsAndCommons.RAFT_CACHE_FILE_PREFIX;

/**
 * Key operations for data.
 *
 * @author nkorange
 * @since 1.0.0
 */
public class KeyBuilder {
    
    public static final String NAMESPACE_KEY_CONNECTOR = "##";
    
    private static final String EPHEMERAL_KEY_PREFIX = "ephemeral.";
    
    public static final String SERVICE_META_KEY_PREFIX = "com.alibaba.nacos.naming.domains.meta.";
    
    public static final String INSTANCE_LIST_KEY_PREFIX = "com.alibaba.nacos.naming.iplist.";
    
    public static final String BRIEF_SERVICE_META_KEY_PREFIX = "meta.";
    
    public static final String BRIEF_INSTANCE_LIST_KEY_PREFIX = "iplist.";
    
    public static final String RESOURCE_KEY_SNAPSHOT = "snapshot";
    
    public static final String RESOURCE_KEY_CHECKSUM = "checksum";
    
    private static String buildEphemeralInstanceListKey(String namespaceId, String serviceName) {
        return INSTANCE_LIST_KEY_PREFIX + EPHEMERAL_KEY_PREFIX + namespaceId + NAMESPACE_KEY_CONNECTOR + serviceName;
    }
    
    private static String buildPersistentInstanceListKey(String namespaceId, String serviceName) {
        return INSTANCE_LIST_KEY_PREFIX + namespaceId + NAMESPACE_KEY_CONNECTOR + serviceName;
    }
    
    public static String buildInstanceListKey(String namespaceId, String serviceName, boolean ephemeral) {
        return ephemeral ? buildEphemeralInstanceListKey(namespaceId, serviceName)
                : buildPersistentInstanceListKey(namespaceId, serviceName);
    }
    
    public static String buildServiceMetaKey(String namespaceId, String serviceName) {
        return SERVICE_META_KEY_PREFIX + namespaceId + NAMESPACE_KEY_CONNECTOR + serviceName;
    }
    
    public static String  getSwitchDomainKey() {
        return SERVICE_META_KEY_PREFIX + UtilsAndCommons.SWITCH_DOMAIN_NAME;
    }
    
    public static boolean matchEphemeralInstanceListKey(String key) {
        return key.startsWith(INSTANCE_LIST_KEY_PREFIX + EPHEMERAL_KEY_PREFIX);
    }
    
    public static boolean matchInstanceListKey(String key) {
        return key.startsWith(INSTANCE_LIST_KEY_PREFIX) || key.startsWith(BRIEF_INSTANCE_LIST_KEY_PREFIX);
    }
    
    public static boolean matchInstanceListKey(String key, String namespaceId, String serviceName) {
        return matchInstanceListKey(key) && matchServiceName(key, namespaceId, serviceName);
    }
    
    public static boolean matchServiceMetaKey(String key) {
        return key.startsWith(SERVICE_META_KEY_PREFIX) || key.startsWith(BRIEF_SERVICE_META_KEY_PREFIX);
    }
    
    public static boolean matchServiceMetaKey(String key, String namespaceId, String serviceName) {
        return matchServiceMetaKey(key) && matchServiceName(key, namespaceId, serviceName);
    }
    
    public static boolean matchSwitchKey(String key) {
        return key.endsWith(UtilsAndCommons.SWITCH_DOMAIN_NAME);
    }
    
    public static boolean matchServiceName(String key, String namespaceId, String serviceName) {
        return key.endsWith(namespaceId + NAMESPACE_KEY_CONNECTOR + serviceName);
    }
    
    public static boolean matchEphemeralKey(String key) {
        // currently only instance list has ephemeral type:
        return matchEphemeralInstanceListKey(key);
    }
    
    public static boolean matchPersistentKey(String key) {
        return !matchEphemeralKey(key);
    }
    
    public static String briefInstanceListkey(String key) {
        return BRIEF_INSTANCE_LIST_KEY_PREFIX + key.split(INSTANCE_LIST_KEY_PREFIX)[1];
    }
    
    public static String briefServiceMetaKey(String key) {
        return BRIEF_SERVICE_META_KEY_PREFIX + key.split(SERVICE_META_KEY_PREFIX)[1];
    }
    
    public static String detailInstanceListkey(String key) {
        return INSTANCE_LIST_KEY_PREFIX.substring(0, INSTANCE_LIST_KEY_PREFIX.indexOf(BRIEF_INSTANCE_LIST_KEY_PREFIX))
                + key;
    }
    
    public static String detailServiceMetaKey(String key) {
        return SERVICE_META_KEY_PREFIX.substring(0, SERVICE_META_KEY_PREFIX.indexOf(BRIEF_SERVICE_META_KEY_PREFIX))
                + key;
    }
    
    public static String getNamespace(String key) {
        
        if (matchSwitchKey(key)) {
            return StringUtils.EMPTY;
        }
        
        if (matchServiceMetaKey(key)) {
            return key.split(NAMESPACE_KEY_CONNECTOR)[0].substring(SERVICE_META_KEY_PREFIX.length());
        }
        
        if (matchEphemeralInstanceListKey(key)) {
            return key.split(NAMESPACE_KEY_CONNECTOR)[0]
                    .substring(INSTANCE_LIST_KEY_PREFIX.length() + EPHEMERAL_KEY_PREFIX.length());
        }
        
        if (matchInstanceListKey(key)) {
            return key.split(NAMESPACE_KEY_CONNECTOR)[0].substring(INSTANCE_LIST_KEY_PREFIX.length());
        }
        
        return StringUtils.EMPTY;
    }
    
    public static String getServiceName(String key) {
        return key.split(NAMESPACE_KEY_CONNECTOR)[1];
    }
    
    public static boolean isDatumCacheFile(String key) {
        return key.startsWith(RAFT_CACHE_FILE_PREFIX);
    }
}
