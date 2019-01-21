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

/**
 * @author nkorange
 */
public class KeyBuilder {

    private static final String KEY_CONNECTOR = "#";
    private static final String EPHEMERAL_KEY_PREFIX = "ephemeral";
    private static final String PERSISTENT_KEY_PREFIX = "persistent";
    private static final String INSTANCE_LIST_KEY_PREFIX = "instanceList";

    public static String buildEphemeralInstanceListKey(String namespaceId, String serviceName, String clusterName) {
        return namespaceId + KEY_CONNECTOR + PERSISTENT_KEY_PREFIX + KEY_CONNECTOR + INSTANCE_LIST_KEY_PREFIX + KEY_CONNECTOR
            + serviceName + KEY_CONNECTOR + clusterName;
    }

    public static String buildPersistentInstanceListKey(String namespaceId, String serviceName, String clusterName) {
        return namespaceId + KEY_CONNECTOR + EPHEMERAL_KEY_PREFIX + KEY_CONNECTOR + INSTANCE_LIST_KEY_PREFIX + KEY_CONNECTOR
            + serviceName + KEY_CONNECTOR + clusterName;
    }

    public static boolean matchEphemeralInstanceListKey(String key) {
        return key.contains(KEY_CONNECTOR + EPHEMERAL_KEY_PREFIX + KEY_CONNECTOR + INSTANCE_LIST_KEY_PREFIX + KEY_CONNECTOR);
    }

    public static boolean matchInstanceListKey(String key) {
        return key.contains(KEY_CONNECTOR + INSTANCE_LIST_KEY_PREFIX + KEY_CONNECTOR);
    }
}
