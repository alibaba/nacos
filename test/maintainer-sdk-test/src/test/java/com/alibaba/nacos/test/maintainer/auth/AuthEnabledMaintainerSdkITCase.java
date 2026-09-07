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

package com.alibaba.nacos.test.maintainer.auth;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.client.auth.impl.NacosAuthLoginConstant;
import com.alibaba.nacos.client.auth.impl.NacosClientAuthServiceImpl;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.maintainer.client.core.AbstractCoreMaintainerService;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerService;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.spi.client.ClientAuthPluginManager;
import com.alibaba.nacos.test.maintainer.MaintainerSdkBaseITCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Auth-enabled integration tests for Maintainer SDK identity, permission, token lifecycle, and
 * directed real-server recovery without duplicate non-idempotent writes.
 *
 * @author Nacos
 */
public class AuthEnabledMaintainerSdkITCase extends MaintainerSdkBaseITCase {

    private static final String NAMESPACE_ID = Constants.DEFAULT_NAMESPACE_ID;

    private static final String RECONNECT_ENABLED_PROPERTY =
            "nacos.maintainer.reconnect.enabled";

    private static final String RECONNECT_CONTROL_DIR_PROPERTY =
            "nacos.maintainer.reconnect.control.dir";

    private static final long RECONNECT_TIMEOUT_MILLIS = 120000L;

    @Test
    void shouldRequireAdministratorForCoreManagement() throws Exception {
        assumeAuthEnabled();

        ConfigMaintainerService administrator = createConfigMaintainerService();
        Namespace namespace = administrator.getNamespace(NAMESPACE_ID);
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, namespace.getNamespace());

        assertNoRight(() -> createConfigMaintainerService(AuthIdentity.ANONYMOUS)
                .getNamespace(NAMESPACE_ID));
        assertNoRight(() -> createConfigMaintainerService(invalidCredentialProperties())
                .getNamespace(NAMESPACE_ID));
        assertNoRight(() -> createConfigMaintainerService(AuthIdentity.CLIENT_READ_WRITE)
                .getNamespace(NAMESPACE_ID));
        assertNoRight(() -> createConfigMaintainerService(AuthIdentity.CLIENT_NO_PERMISSION)
                .getNamespace(NAMESPACE_ID));
    }

    @Test
    void shouldEnforceConfigAndNamingPermissionMatrixWithoutDeniedSideEffects()
            throws Exception {
        assumeAuthEnabled();

        ConfigMaintainerService administrator = createConfigMaintainerService();
        ConfigMaintainerService readWrite =
                createConfigMaintainerService(AuthIdentity.CLIENT_READ_WRITE);
        ConfigMaintainerService readOnly =
                createConfigMaintainerService(AuthIdentity.CLIENT_READ_ONLY);
        ConfigMaintainerService noPermission =
                createConfigMaintainerService(AuthIdentity.CLIENT_NO_PERMISSION);
        String deniedDataId = randomDataId("auth-denied-config");
        String allowedDataId = randomDataId("auth-allowed-config");
        String group = randomGroup("auth-config");
        String content = "maintainer.auth.permission=true";

        assertNoRight(() -> noPermission.publishConfig(deniedDataId, group, NAMESPACE_ID,
                content));
        assertNoRight(() -> readOnly.publishConfig(deniedDataId, group, NAMESPACE_ID,
                content));
        assertMissingConfig(administrator, deniedDataId, group);

        assertTrue(readWrite.publishConfig(allowedDataId, group, NAMESPACE_ID, content));
        addCleanup(() -> administrator.deleteConfig(allowedDataId, group, NAMESPACE_ID));
        ConfigDetailInfo detail = readOnly.getConfig(allowedDataId, group, NAMESPACE_ID);
        assertEquals(content, detail.getContent());

        NamingMaintainerService adminNaming = createNamingMaintainerService();
        NamingMaintainerService readWriteNaming =
                createNamingMaintainerService(AuthIdentity.CLIENT_READ_WRITE);
        NamingMaintainerService readOnlyNaming =
                createNamingMaintainerService(AuthIdentity.CLIENT_READ_ONLY);
        NamingMaintainerService noPermissionNaming =
                createNamingMaintainerService(AuthIdentity.CLIENT_NO_PERMISSION);
        String deniedService = randomMaintainerName("auth-denied-service");
        String allowedService = randomMaintainerName("auth-allowed-service");

        assertNoRight(() -> noPermissionNaming.createService(NAMESPACE_ID, group,
                deniedService, false, 0.0F));
        assertNoRight(() -> readOnlyNaming.createService(NAMESPACE_ID, group, deniedService,
                false, 0.0F));
        assertFalse(containsService(adminNaming, group, deniedService));

        assertNotNull(readWriteNaming.createService(NAMESPACE_ID, group, allowedService, false,
                0.0F));
        addCleanup(() -> adminNaming.removeService(NAMESPACE_ID, group, allowedService));
        assertTrue(containsService(readOnlyNaming, group, allowedService));
    }

    @Disabled("DAUTH-F04: invalid credentials currently downgrade to anonymous AI access; "
            + "see UNEXPECTED_PRODUCT_FINDINGS.md")
    @Test
    void shouldFailClosedForInvalidCredentialsOnAnonymousAiRead() throws Exception {
        assumeAuthEnabled();

        Properties invalidCredential = invalidCredentialProperties();
        assertTrue(invalidCredential.containsKey(PropertyKeyConst.USERNAME));
        AiMaintainerService maintainerService =
                AiMaintainerFactory.createAiMaintainerService(invalidCredential);

        assertNoRight(() -> maintainerService.skill().listSkills(NAMESPACE_ID,
                randomMaintainerName("invalid-credential"), "blur", 1, 10));
    }

    @Test
    void shouldRefreshExpiredTokenWithoutRepeatingDeniedNamespaceCreation() throws Exception {
        assumeAuthEnabled();

        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        assertEquals(NAMESPACE_ID, maintainerService.getNamespace(NAMESPACE_ID).getNamespace());
        ClientHttpProxy proxy = clientHttpProxy(maintainerService);
        ScheduledExecutorService executor = scheduledExecutor(proxy);
        awaitNextRefreshWindow(executor);

        expireCurrentToken(proxy);
        String namespaceId = randomMaintainerName("expired-token");
        addCleanup(() -> maintainerService.deleteNamespace(namespaceId));
        assertNoRight(() -> maintainerService.createNamespace(namespaceId, namespaceId,
                "must not be created by a denied request"));

        waitUntil("maintainer client should refresh the rejected token", 15000L,
                () -> NAMESPACE_ID.equals(
                        maintainerService.getNamespace(NAMESPACE_ID).getNamespace()));
        assertFalse(maintainerService.checkNamespaceIdExist(namespaceId));

        assertTrue(maintainerService.createNamespace(namespaceId, namespaceId,
                "created once after token refresh"));
        long matchingNamespaces = maintainerService.getNamespaceList().stream()
                .filter(each -> namespaceId.equals(each.getNamespace())).count();
        assertEquals(1L, matchingNamespaces);
    }

    @Test
    void shouldShutdownAuthenticationRefreshExecutor() throws Exception {
        assumeAuthEnabled();

        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        ClientHttpProxy proxy = clientHttpProxy(maintainerService);
        ScheduledExecutorService executor = scheduledExecutor(proxy);
        assertFalse(executor.isShutdown());

        maintainerService.shutdown();
        assertTrue(executor.isShutdown());
    }

    @Test
    @EnabledIfSystemProperty(named = RECONNECT_ENABLED_PROPERTY, matches = "true")
    void shouldRecoverOriginalMaintainerAndAvoidDuplicateNamespaceAfterRealServerRestart()
            throws Exception {
        assumeAuthEnabled();
        Path controlDirectory = reconnectControlDirectory();
        Path ready = resetMarker(controlDirectory, "client-ready");
        Path serverStopped = resetMarker(controlDirectory, "server-stopped");
        Path downObserved = resetMarker(controlDirectory, "client-observed-down");
        Path serverRestarted = resetMarker(controlDirectory, "server-restarted");

        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        String persistentNamespace = randomMaintainerName("restart-persistent");
        String createdAfterRestart = randomMaintainerName("restart-create-once");
        addCleanup(() -> maintainerService.deleteNamespace(createdAfterRestart));
        addCleanup(() -> maintainerService.deleteNamespace(persistentNamespace));
        assertTrue(maintainerService.createNamespace(persistentNamespace,
                persistentNamespace, "created before a real server restart"));
        assertEquals(persistentNamespace,
                maintainerService.getNamespace(persistentNamespace).getNamespace());
        writeMarker(ready, persistentNamespace);

        waitForMarker(serverStopped, "external harness should stop the standalone server");
        waitUntil("the original Maintainer SDK should observe the stopped server",
                RECONNECT_TIMEOUT_MILLIS,
                () -> maintainerRequestUnavailable(maintainerService));
        writeMarker(downObserved, persistentNamespace);
        waitForMarker(serverRestarted, "external harness should restart the standalone server");

        waitUntil("the original Maintainer SDK should recover after restart",
                RECONNECT_TIMEOUT_MILLIS,
                () -> maintainerService.liveness() && maintainerService.readiness()
                        && persistentNamespace.equals(maintainerService
                                .getNamespace(persistentNamespace).getNamespace()));
        assertTrue(maintainerService.createNamespace(createdAfterRestart,
                createdAfterRestart, "created once after recovery"));
        assertThrows(NacosException.class,
                () -> maintainerService.createNamespace(createdAfterRestart,
                        createdAfterRestart, "duplicate retry must not add another row"));
        long matchingNamespaces = maintainerService.getNamespaceList().stream()
                .filter(each -> createdAfterRestart.equals(each.getNamespace())).count();
        assertEquals(1L, matchingNamespaces);
    }

    private void assumeAuthEnabled() {
        assumeTrue(AUTH_ENABLED, "Auth-enabled maintainer tests require the auth-on baseline");
    }

    private boolean maintainerRequestUnavailable(ConfigMaintainerService service) {
        try {
            return !service.liveness();
        } catch (NacosException expected) {
            return true;
        }
    }

    private Path reconnectControlDirectory() throws Exception {
        String value = System.getProperty(RECONNECT_CONTROL_DIR_PROPERTY, "");
        if (value.isBlank()) {
            throw new IllegalStateException("Missing required restart IT property: "
                    + RECONNECT_CONTROL_DIR_PROPERTY);
        }
        Path result = Paths.get(value);
        Files.createDirectories(result);
        return result;
    }

    private Path resetMarker(Path controlDirectory, String name) throws Exception {
        Path result = controlDirectory.resolve(name);
        Files.deleteIfExists(result);
        return result;
    }

    private void waitForMarker(Path marker, String reason) throws Exception {
        waitUntil(reason, RECONNECT_TIMEOUT_MILLIS, () -> Files.isRegularFile(marker));
    }

    private void writeMarker(Path marker, String value) throws Exception {
        Files.write(marker, Collections.singletonList(value), StandardCharsets.UTF_8);
    }

    private void assertMissingConfig(ConfigMaintainerService service, String dataId,
            String group) {
        NacosException exception = assertThrows(NacosException.class,
                () -> service.getConfig(dataId, group, NAMESPACE_ID));
        assertTrue(exception.getErrCode() == NacosException.NOT_FOUND
                || exception.getErrCode() == NacosException.RESOURCE_NOT_FOUND,
                exception.getMessage());
    }

    private boolean containsService(NamingMaintainerService service, String group,
            String serviceName) throws Exception {
        return service.listServices(NAMESPACE_ID, group, serviceName, false, 1, 10).getPageItems()
                .stream().anyMatch(each -> serviceName.equals(each.getName()));
    }

    private void assertNoRight(CheckedCall call) {
        NacosException exception = assertThrows(NacosException.class, call::execute);
        assertEquals(NacosException.NO_RIGHT, exception.getErrCode(), exception.getMessage());
    }

    private ClientHttpProxy clientHttpProxy(ConfigMaintainerService service) throws Exception {
        Field field = AbstractCoreMaintainerService.class.getDeclaredField("clientHttpProxy");
        field.setAccessible(true);
        return (ClientHttpProxy) field.get(service);
    }

    private ScheduledExecutorService scheduledExecutor(ClientHttpProxy proxy) throws Exception {
        Field field = ClientHttpProxy.class.getDeclaredField("executor");
        field.setAccessible(true);
        return (ScheduledExecutorService) field.get(proxy);
    }

    private void awaitNextRefreshWindow(ScheduledExecutorService executor) throws Exception {
        ScheduledThreadPoolExecutor scheduled = (ScheduledThreadPoolExecutor) executor;
        waitUntil("auth refresh task should enter its next scheduled window", 10000L, () -> {
            Delayed next = (Delayed) scheduled.getQueue().peek();
            return null != next && next.getDelay(TimeUnit.MILLISECONDS) > 3000L;
        });
    }

    private void expireCurrentToken(ClientHttpProxy proxy) throws Exception {
        Field managerField = ClientHttpProxy.class.getDeclaredField("clientAuthPluginManager");
        managerField.setAccessible(true);
        ClientAuthPluginManager manager = (ClientAuthPluginManager) managerField.get(proxy);
        NacosClientAuthServiceImpl authService = manager.getAuthServiceSpiImplSet().stream()
                .filter(NacosClientAuthServiceImpl.class::isInstance)
                .map(NacosClientAuthServiceImpl.class::cast).findFirst().orElseThrow();

        LoginIdentityContext identityContext = authService.getLoginIdentityContext(null);
        identityContext.setParameter(NacosAuthLoginConstant.ACCESSTOKEN,
                "expired-maintainer-sdk-it-token");
        Field refreshTime = NacosClientAuthServiceImpl.class.getDeclaredField("lastRefreshTime");
        refreshTime.setAccessible(true);
        refreshTime.setLong(authService, 0L);
    }

    @FunctionalInterface
    private interface CheckedCall {

        void execute() throws Exception;
    }
}
