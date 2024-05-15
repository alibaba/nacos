/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.auth.ram.identify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialWatcherTest {
    
    @Mock
    private CredentialService credentialService;
    
    private CredentialWatcher credentialWatcher;
    
    private Method loadCredentialMethod;
    
    private Method loadCredentialFromPropertiesMethod;
    
    @BeforeEach
    void setUp() throws Exception {
        credentialWatcher = new CredentialWatcher("testApp", credentialService);
        loadCredentialMethod = CredentialWatcher.class.getDeclaredMethod("loadCredential", boolean.class);
        loadCredentialMethod.setAccessible(true);
        loadCredentialFromPropertiesMethod = CredentialWatcher.class.getDeclaredMethod("loadCredentialFromProperties",
                InputStream.class, boolean.class, Credentials.class);
        loadCredentialFromPropertiesMethod.setAccessible(true);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        credentialWatcher.stop();
        System.clearProperty("spas.identity");
        System.clearProperty(IdentifyConstants.ENV_ACCESS_KEY);
        System.clearProperty(IdentifyConstants.ENV_SECRET_KEY);
        CredentialService.freeInstance();
    }
    
    @Test
    void testStop() throws NoSuchFieldException, IllegalAccessException {
        credentialWatcher.stop();
        Field executorField = CredentialWatcher.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ScheduledExecutorService executor = (ScheduledExecutorService) executorField.get(credentialWatcher);
        assertTrue(executor.isShutdown());
    }
    
    @Test
    void testLoadCredentialByEnv() throws InvocationTargetException, IllegalAccessException {
        System.setProperty(IdentifyConstants.ENV_ACCESS_KEY, "testAk");
        System.setProperty(IdentifyConstants.ENV_SECRET_KEY, "testSk");
        final AtomicReference<String> readAk = new AtomicReference<>("");
        final AtomicReference<String> readSK = new AtomicReference<>("");
        final AtomicReference<String> readTenantId = new AtomicReference<>("");
        doAnswer(invocationOnMock -> {
            Credentials credentials = invocationOnMock.getArgument(0, Credentials.class);
            readAk.set(credentials.getAccessKey());
            readSK.set(credentials.getSecretKey());
            readTenantId.set(credentials.getTenantId());
            return null;
        }).when(credentialService).setCredential(any());
        loadCredentialMethod.invoke(credentialWatcher, true);
        assertEquals("testAk", readAk.get());
        assertEquals("testSk", readSK.get());
        assertNull(readTenantId.get());
    }
    
    @Test
    void testLoadCredentialByIdentityFile() throws InvocationTargetException, IllegalAccessException {
        URL url = CredentialWatcherTest.class.getResource("/spas.identity");
        System.setProperty("spas.identity", url.getPath());
        final AtomicReference<String> readAk = new AtomicReference<>("");
        final AtomicReference<String> readSK = new AtomicReference<>("");
        final AtomicReference<String> readTenantId = new AtomicReference<>("");
        doAnswer(invocationOnMock -> {
            Credentials credentials = invocationOnMock.getArgument(0, Credentials.class);
            readAk.set(credentials.getAccessKey());
            readSK.set(credentials.getSecretKey());
            readTenantId.set(credentials.getTenantId());
            return null;
        }).when(credentialService).setCredential(any());
        loadCredentialMethod.invoke(credentialWatcher, true);
        assertEquals("testAk", readAk.get());
        assertEquals("testSk", readSK.get());
        assertEquals("testTenantId", readTenantId.get());
    }
    
    @Test
    void testLoadCredentialByInvalidIdentityFile() throws InvocationTargetException, IllegalAccessException {
        URL url = CredentialWatcherTest.class.getResource("/spas_invalid.identity");
        System.setProperty("spas.identity", url.getPath());
        final AtomicReference<String> readAk = new AtomicReference<>("");
        final AtomicReference<String> readSK = new AtomicReference<>("");
        final AtomicReference<String> readTenantId = new AtomicReference<>("");
        doAnswer(invocationOnMock -> {
            Credentials credentials = invocationOnMock.getArgument(0, Credentials.class);
            readAk.set(credentials.getAccessKey());
            readSK.set(credentials.getSecretKey());
            readTenantId.set(credentials.getTenantId());
            return null;
        }).when(credentialService).setCredential(any());
        loadCredentialMethod.invoke(credentialWatcher, true);
        assertEquals("", readAk.get());
        assertEquals("testSk", readSK.get());
        assertEquals("testTenantId", readTenantId.get());
    }
    
    /**
     * The docker file is need /etc permission, which depend environment. So use mock InputStream to test.
     */
    @Test
    void testLoadCredentialByDockerFile()
            throws FileNotFoundException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        URL url = CredentialWatcherTest.class.getResource("/spas_docker.identity");
        InputStream propertiesIS = new FileInputStream(url.getPath());
        Credentials actual = new Credentials();
        Field propertyPathField = CredentialWatcher.class.getDeclaredField("propertyPath");
        propertyPathField.setAccessible(true);
        propertyPathField.set(credentialWatcher, IdentifyConstants.DOCKER_CREDENTIAL_PATH);
        loadCredentialFromPropertiesMethod.invoke(credentialWatcher, propertiesIS, true, actual);
        assertEquals("testAk", actual.getAccessKey());
        assertEquals("testSk", actual.getSecretKey());
        assertEquals("testTenantId", actual.getTenantId());
    }
    
    @Test
    void testLoadCredentialByFileWithIoException()
            throws IOException, InvocationTargetException, IllegalAccessException {
        InputStream propertiesIS = mock(InputStream.class);
        when(propertiesIS.read(any())).thenThrow(new IOException("test"));
        doThrow(new IOException("test")).when(propertiesIS).close();
        Credentials actual = new Credentials();
        loadCredentialFromPropertiesMethod.invoke(credentialWatcher, propertiesIS, true, actual);
        assertNull(actual.getAccessKey());
        assertNull(actual.getSecretKey());
        assertNull(actual.getTenantId());
    }
    
    @Test
    void testReLoadCredential() throws InvocationTargetException, IllegalAccessException, InterruptedException {
        URL url = CredentialWatcherTest.class.getResource("/spas_modified.identity");
        modifiedFile(url, true);
        System.setProperty("spas.identity", url.getPath());
        final AtomicReference<String> readAk = new AtomicReference<>("");
        final AtomicReference<String> readSK = new AtomicReference<>("");
        final AtomicReference<String> readTenantId = new AtomicReference<>("");
        doAnswer(invocationOnMock -> {
            Credentials credentials = invocationOnMock.getArgument(0, Credentials.class);
            readAk.set(credentials.getAccessKey());
            readSK.set(credentials.getSecretKey());
            readTenantId.set(credentials.getTenantId());
            return null;
        }).when(credentialService).setCredential(any());
        loadCredentialMethod.invoke(credentialWatcher, true);
        assertEquals("testAk", readAk.get());
        assertEquals("testSk", readSK.get());
        assertNull(readTenantId.get());
        // waiting reload thread work
        modifiedFile(url, false);
        TimeUnit.MILLISECONDS.sleep(10500);
        assertEquals("testAk", readAk.get());
        assertEquals("testSk", readSK.get());
        assertEquals("testTenantId", readTenantId.get());
    }
    
    private boolean modifiedFile(URL url, boolean init) {
        File file = new File(url.getPath());
        boolean result;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            if (init) {
                bw.write("accessKey=testAk\nsecretKey=testSk");
            } else {
                bw.write("accessKey=testAk\nsecretKey=testSk\ntenantId=testTenantId");
            }
            bw.flush();
            result = true;
        } catch (IOException ignored) {
            result = false;
        }
        return result;
    }
}
