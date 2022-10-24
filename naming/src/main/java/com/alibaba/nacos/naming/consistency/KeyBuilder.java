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

/**
 * Key operations for data.
 *
 * @author nkorange
 * @since 1.0.0
 */
public class KeyBuilder {
    
    public static final String NAMESPACE_KEY_CONNECTOR = "##";
    
    public static final String SERVICE_META_KEY_PREFIX = "com.alibaba.nacos.naming.domains.meta.";
    
    public static String buildServiceMetaKey(String namespaceId, String serviceName) {
        return SERVICE_META_KEY_PREFIX + namespaceId + NAMESPACE_KEY_CONNECTOR + serviceName;
    }
    
    public static String getSwitchDomainKey() {
        return SERVICE_META_KEY_PREFIX + UtilsAndCommons.SWITCH_DOMAIN_NAME;
    }
    
    public static boolean matchSwitchKey(String key) {
        return key.endsWith(UtilsAndCommons.SWITCH_DOMAIN_NAME);
    }
}
