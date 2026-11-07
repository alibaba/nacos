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

package com.alibaba.nacos.test.nativeimage;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The credentials of one Server: the administrator, the identities that the integration suites
 * log in with, and the secrets of the Server itself. The properties of the suites are the same
 * ones that {@code native-ci.sh} and {@code auth-it-identity.sh} used, so that the suites run
 * unchanged. A value that a system property names already stays; the rest is generated.
 *
 * @author Nacos
 */
public final class NativeCredentials {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, String> suiteProperties = new LinkedHashMap<>();

    private final String tokenSecret;

    private final String identityKey;

    private final String identityValue;

    private NativeCredentials() {
        suiteProperties.put("nacos.test.auth.enabled", "true");
        identity("admin", "nacos", "A1!");
        identity("client", "nacos_it_client_rw", "C1!");
        identity("readonly", "nacos_it_client_readonly", "R1!");
        identity("no-permission", "nacos_it_client_no_permission", "N1!");
        tokenSecret = Base64.getEncoder().encodeToString(random(48));
        identityKey = "nacos-it-" + hex(8);
        identityValue = hex(32);
        suiteProperties.put("nacos.test.auth.token.secret", tokenSecret);
    }

    /**
     * Credentials of a new Server.
     *
     * @return credentials, generated where no system property gives a value
     */
    public static NativeCredentials create() {
        return new NativeCredentials();
    }

    private void identity(String name, String defaultUsername, String suffix) {
        suiteProperties.put("nacos.test.auth." + name + ".username",
                System.getProperty("nacos.test.auth." + name + ".username", defaultUsername));
        suiteProperties.put("nacos.test.auth." + name + ".password",
                System.getProperty("nacos.test.auth." + name + ".password",
                        "NacosIt-" + hex(16) + '-' + suffix));
    }

    /**
     * The value of a property of the suites.
     *
     * @param name for example {@code nacos.test.auth.admin.password}
     * @return the value
     */
    public String get(String name) {
        return suiteProperties.get(name);
    }

    /**
     * The user name of an identity.
     *
     * @param name admin, client, readonly or no-permission
     * @return the user name
     */
    public String username(String name) {
        return get("nacos.test.auth." + name + ".username");
    }

    /**
     * The password of an identity.
     *
     * @param name admin, client, readonly or no-permission
     * @return the password
     */
    public String password(String name) {
        return get("nacos.test.auth." + name + ".password");
    }

    /**
     * The properties that the Server needs to accept these credentials.
     *
     * @return properties of {@code conf/application.properties}
     */
    public Map<String, String> serverProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("nacos.plugin.auth.nacos.token.secret.key", tokenSecret);
        properties.put("nacos.core.auth.server.identity.key", identityKey);
        properties.put("nacos.core.auth.server.identity.value", identityValue);
        return properties;
    }

    /**
     * Publishes the credentials as system properties, where the integration suites read them
     * before they look at the environment.
     */
    public void exportSystemProperties() {
        suiteProperties.forEach(System::setProperty);
    }

    private static byte[] random(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String hex(int bytes) {
        StringBuilder result = new StringBuilder();
        for (byte each : random(bytes)) {
            result.append(String.format("%02x", each));
        }
        return result.toString();
    }
}
