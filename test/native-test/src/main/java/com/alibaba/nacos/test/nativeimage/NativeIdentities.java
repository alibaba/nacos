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

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bootstraps the administrator of a new Server and prepares the identities that the integration
 * suites log in with: a read-write client, a read-only client and a client without permission.
 * It does what {@code test/scripts/auth-it-identity.sh} did.
 *
 * @author Nacos
 */
public final class NativeIdentities {

    private static final String CLIENT_ROLE = "NACOS_IT_CLIENT_RW";

    private static final String READONLY_ROLE = "NACOS_IT_CLIENT_READONLY";

    private static final String NO_PERMISSION_ROLE = "NACOS_IT_CLIENT_NO_PERMISSION";

    private static final String[] CLIENT_RESOURCES = {"*:*:config/*", "*:*:naming/*", "*:*:ai/*"};

    private static final Duration CONVERGENCE = Duration.ofSeconds(20);

    private final NativeHttp http = new NativeHttp();

    private final String apiUrl;

    private final NativeCredentials credentials;

    /**
     * Creates the helper of a Server.
     *
     * @param apiUrl      the base URL of the API, for example {@code http://127.0.0.1:8848}
     * @param credentials the credentials to set up
     */
    public NativeIdentities(String apiUrl, NativeCredentials credentials) {
        this.apiUrl = apiUrl + "/nacos/v3";
        this.credentials = credentials;
    }

    /**
     * Sets the password of the administrator of an empty database, and checks that a second
     * attempt is refused.
     */
    public void bootstrapAdmin() {
        Map<String, String> form = Map.of("password", credentials.password("admin"));
        NativeHttp.Response first = http.form("POST", apiUrl + "/auth/user/admin", null, form);
        require(first.ok(), "administrator bootstrap", first);
        NativeHttp.Response second = http.form("POST", apiUrl + "/auth/user/admin", null, form);
        require(second.status() == 200 && second.resultCode() == 409,
                "repeated administrator bootstrap", second);
    }

    /**
     * Logs in, and waits until the Server knows the user. The user cache of a Server refreshes
     * every few seconds.
     *
     * @param username the user name
     * @param password the password
     * @return the access token
     */
    public String login(String username, String password) {
        String[] token = new String[1];
        await("login of " + username, () -> {
            NativeHttp.Response response = http.form("POST", apiUrl + "/auth/user/login", null,
                    Map.of("username", username, "password", password));
            token[0] = response.accessToken();
            return response.status() == 200 && !token[0].isEmpty();
        });
        return token[0];
    }

    /**
     * Logs in as the administrator.
     *
     * @return the access token
     */
    public String adminToken() {
        return login(credentials.username("admin"), credentials.password("admin"));
    }

    /**
     * Creates the three client identities, and checks what each of them may do.
     */
    public void prepare() {
        String admin = adminToken();
        deleteIdentity(admin, credentials.username("client"), CLIENT_ROLE, "rw");
        deleteIdentity(admin, credentials.username("readonly"), READONLY_ROLE, "r");
        deleteIdentity(admin, credentials.username("no-permission"), NO_PERMISSION_ROLE, "r");

        createIdentity(admin, credentials.username("client"), credentials.password("client"),
                CLIENT_ROLE);
        createIdentity(admin, credentials.username("readonly"), credentials.password("readonly"),
                READONLY_ROLE);
        createIdentity(admin, credentials.username("no-permission"),
                credentials.password("no-permission"), NO_PERMISSION_ROLE);

        String client = login(credentials.username("client"), credentials.password("client"));
        String readonly = login(credentials.username("readonly"),
                credentials.password("readonly"));
        String noPermission = login(credentials.username("no-permission"),
                credentials.password("no-permission"));

        assertDenied(client, "client before permission grant");
        grant(admin, CLIENT_ROLE, "rw");
        await("client read-write permission", () -> probe(client).ok());
        grant(admin, READONLY_ROLE, "r");
        await("client read-only permission", () -> probe(readonly).ok());
        assertDenied(noPermission, "client without permission");
    }

    private void deleteIdentity(String admin, String username, String role, String action) {
        adminRequest("DELETE", "/auth/permission?role=" + role + "&resource=%2A&action=" + action,
                admin, Map.of());
        for (String resource : CLIENT_RESOURCES) {
            adminRequest("DELETE", "/auth/permission?role=" + role, admin,
                    Map.of("resource", resource, "action", action));
        }
        adminRequest("DELETE", "/auth/role?role=" + role + "&username=" + username, admin,
                Map.of());
        adminRequest("DELETE", "/auth/user?username=" + username, admin, Map.of());
    }

    private void createIdentity(String admin, String username, String password, String role) {
        adminRequest("POST", "/auth/user", admin, Map.of("username", username, "password",
                password));
        adminRequest("POST", "/auth/role", admin, Map.of("role", role, "username", username));
    }

    private void grant(String admin, String role, String action) {
        for (String resource : CLIENT_RESOURCES) {
            adminRequest("POST", "/auth/permission", admin,
                    Map.of("role", role, "resource", resource, "action", action));
        }
    }

    private void adminRequest(String method, String path, String admin,
            Map<String, String> form) {
        NativeHttp.Response response = http.form(method, apiUrl + path, admin, form);
        require(response.ok(), method + ' ' + path, response);
    }

    private NativeHttp.Response probe(String token) {
        return http.get(apiUrl + "/client/ns/instance/list?namespaceId=public"
                + "&groupName=DEFAULT_GROUP&serviceName=nacos-it-auth-identity-probe", token);
    }

    private void assertDenied(String token, String scenario) {
        NativeHttp.Response response = probe(token);
        require(response.status() == 403 && response.resultCode() == 10001, scenario, response);
    }

    private static void require(boolean condition, String scenario, NativeHttp.Response response) {
        if (!condition) {
            throw new IllegalStateException(scenario + " failed: " + response.describe());
        }
    }

    private static void await(String scenario, Supplier<Boolean> condition) {
        Duration convergence = NativeTestSettings.scaled(CONVERGENCE);
        long deadline = System.nanoTime() + convergence.toNanos();
        do {
            if (condition.get()) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException(scenario + " failed to converge within "
                + convergence.toSeconds() + " seconds");
    }
}
