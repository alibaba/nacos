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

package com.alibaba.nacos.console.proxy.core;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.console.handler.core.NamespaceHandler;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamespaceProxyTest {

    private static final String NAMESPACE_ID = "testNamespaceId";

    private static final String NAMESPACE_NAME = "testNamespaceName";

    private static final String NAMESPACE_DESC = "testNamespaceDesc";

    private static final String AUTH_TYPE = "testNamespaceAuth";

    @Mock
    private NamespaceHandler namespaceHandler;

    @Mock
    private NacosAuthConfig nacosAuthConfig;

    @Mock
    private AuthPluginService authPluginService;

    private Map<String, NacosAuthConfig> cachedConfigMap;

    private NamespaceProxy namespaceProxy;

    @BeforeEach
    public void setUp() {
        namespaceProxy = new NamespaceProxy(namespaceHandler);
    }

    /**
     * Clean up auth test context.
     */
    @AfterEach
    public void tearDown() {
        if (cachedConfigMap != null) {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(),
                "nacosAuthConfigMap", cachedConfigMap);
        }
        Map<String, AuthPluginService> authServiceMap =
            (Map<String, AuthPluginService>) ReflectionTestUtils.getField(
                AuthPluginManager.getInstance(), "authServiceMap");
        authServiceMap.remove(AUTH_TYPE);
        PluginStateCheckerHolder.setInstance(null);
        RequestContextHolder.removeContext();
    }

    @Test
    public void getNamespaceDetail() throws NacosException {
        String namespaceId = "testNamespaceId";
        Namespace expectedNamespace = new Namespace();
        expectedNamespace.setNamespace(namespaceId);
        expectedNamespace.setNamespaceShowName("Test Namespace");

        when(namespaceHandler.getNamespaceDetail(namespaceId)).thenReturn(expectedNamespace);

        Namespace actualNamespace = namespaceProxy.getNamespaceDetail(namespaceId);

        assertEquals(expectedNamespace.getNamespace(), actualNamespace.getNamespace());
        assertEquals(expectedNamespace.getNamespaceShowName(),
            actualNamespace.getNamespaceShowName());
    }

    @Test
    public void getNamespaceList() throws NacosException {
        List<Namespace> expectedNamespaces =
            Arrays.asList(new Namespace("namespace1", "Namespace 1"),
                new Namespace("namespace2", "Namespace 2"));
        when(namespaceHandler.getNamespaceList()).thenReturn(expectedNamespaces);

        List<Namespace> actualNamespaces = namespaceProxy.getNamespaceList();

        assertEquals(expectedNamespaces, actualNamespaces);
    }

    @Test
    public void getNamespaceListFilterUnauthorizedNamespace() throws NacosException {
        enableConsoleAuth();
        List<Namespace> expectedNamespaces =
            Arrays.asList(new Namespace("namespace1", "Namespace 1"),
                new Namespace("namespace2", "Namespace 2"));
        IdentityContext identityContext = new IdentityContext();
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(identityContext);
        when(namespaceHandler.getNamespaceList()).thenReturn(expectedNamespaces);
        when(authPluginService.getAuthorizedNamespaceIds(eq(identityContext),
            eq(Arrays.asList("namespace1", "namespace2")),
            eq(ActionTypes.READ))).thenReturn(Optional.of(Set.of("namespace1")));

        List<Namespace> actualNamespaces = namespaceProxy.getNamespaceList();

        assertEquals(1, actualNamespaces.size());
        assertEquals("namespace1", actualNamespaces.get(0).getNamespace());
    }

    @Test
    public void createNamespace() throws NacosException {
        when(namespaceHandler.createNamespace(NAMESPACE_ID, NAMESPACE_NAME, NAMESPACE_DESC))
            .thenReturn(true);

        Boolean result =
            namespaceProxy.createNamespace(NAMESPACE_ID, NAMESPACE_NAME, NAMESPACE_DESC);

        assertTrue(result);
        verify(namespaceHandler, times(1)).createNamespace(NAMESPACE_ID, NAMESPACE_NAME,
            NAMESPACE_DESC);
    }

    @Test
    public void updateNamespace() throws NacosException {
        NamespaceForm namespaceForm =
            new NamespaceForm("namespaceId", "namespaceName", "namespaceDesc");
        when(namespaceHandler.updateNamespace(namespaceForm)).thenReturn(true);

        Boolean result = namespaceProxy.updateNamespace(namespaceForm);

        assertTrue(result);
        verify(namespaceHandler, times(1)).updateNamespace(namespaceForm);
    }

    @Test
    public void deleteNamespace() throws NacosException {
        String namespaceId = "testNamespaceId";
        when(namespaceHandler.deleteNamespace(namespaceId)).thenReturn(true);

        Boolean result = namespaceProxy.deleteNamespace(namespaceId);

        assertTrue(result);
        verify(namespaceHandler, times(1)).deleteNamespace(namespaceId);
    }

    @Test
    public void checkNamespaceIdExist() throws NacosException {
        when(namespaceHandler.checkNamespaceIdExist(NAMESPACE_ID)).thenReturn(true);

        Boolean result = namespaceProxy.checkNamespaceIdExist(NAMESPACE_ID);

        assertTrue(result);
        verify(namespaceHandler, times(1)).checkNamespaceIdExist(NAMESPACE_ID);
    }

    private void enableConsoleAuth() {
        cachedConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
            NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        when(nacosAuthConfig.isAuthEnabled()).thenReturn(true);
        when(nacosAuthConfig.getNacosAuthSystemType()).thenReturn(AUTH_TYPE);
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            Map.of(ApiType.CONSOLE_API.name(), nacosAuthConfig));
        Map<String, AuthPluginService> authServiceMap =
            (Map<String, AuthPluginService>) ReflectionTestUtils.getField(
                AuthPluginManager.getInstance(), "authServiceMap");
        authServiceMap.put(AUTH_TYPE, authPluginService);
        PluginStateCheckerHolder.setInstance((pluginType, pluginName) -> true);
    }

}
