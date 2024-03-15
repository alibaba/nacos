/*
 *
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
 *
 */

package com.alibaba.nacos.client.auth.ram.identify;

import junit.framework.TestCase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CredentialServiceTest extends TestCase {
    
    private static final String APP_NAME = "app";
    
    @Before
    public void setUp() throws Exception {
    }
    
    @After
    public void tearDown() throws Exception {
        System.clearProperty(IdentifyConstants.PROJECT_NAME_PROPERTY);
        CredentialService.freeInstance();
        CredentialService.freeInstance(APP_NAME);
    }
    
    @Test
    public void testGetInstance() {
        CredentialService credentialService1 = CredentialService.getInstance();
        CredentialService credentialService2 = CredentialService.getInstance();
        Assert.assertEquals(credentialService1, credentialService2);
    }
    
    @Test
    public void testGetInstance2() {
        CredentialService credentialService1 = CredentialService.getInstance(APP_NAME);
        CredentialService credentialService2 = CredentialService.getInstance(APP_NAME);
        Assert.assertEquals(credentialService1, credentialService2);
    }
    
    @Test
    public void testGetInstance3() throws NoSuchFieldException, IllegalAccessException {
        System.setProperty(IdentifyConstants.PROJECT_NAME_PROPERTY, APP_NAME);
        CredentialService credentialService1 = CredentialService.getInstance();
        Field appNameField = credentialService1.getClass().getDeclaredField("appName");
        appNameField.setAccessible(true);
        String appName = (String) appNameField.get(credentialService1);
        assertEquals(APP_NAME, appName);
    }
    
    @Test
    public void testFreeInstance() {
        CredentialService credentialService1 = CredentialService.getInstance();
        CredentialService credentialService2 = CredentialService.freeInstance();
        Assert.assertEquals(credentialService1, credentialService2);
    }
    
    @Test
    public void testFreeInstance2() {
        CredentialService credentialService1 = CredentialService.getInstance();
        CredentialService credentialService2 = CredentialService.freeInstance();
        Assert.assertEquals(credentialService1, credentialService2);
    }
    
    @Test
    public void testFree() throws NoSuchFieldException, IllegalAccessException {
        CredentialService credentialService1 = CredentialService.getInstance();
        CredentialWatcher mockWatcher = mock(CredentialWatcher.class);
        Field watcherField = CredentialService.class.getDeclaredField("watcher");
        watcherField.setAccessible(true);
        watcherField.set(credentialService1, mockWatcher);
        //when
        credentialService1.free();
        //then
        verify(mockWatcher, times(1)).stop();
    }
    
    @Test
    public void testGetCredential() {
        CredentialService credentialService1 = CredentialService.getInstance();
        Credentials credential = credentialService1.getCredential();
        Assert.assertNotNull(credential);
    }
    
    @Test
    public void testSetCredential() {
        CredentialService credentialService1 = CredentialService.getInstance();
        Credentials credential = new Credentials();
        //when
        credentialService1.setCredential(credential);
        //then
        Assert.assertEquals(credential, credentialService1.getCredential());
    }
    
    @Test
    public void testSetStaticCredential() throws NoSuchFieldException, IllegalAccessException {
        CredentialService credentialService1 = CredentialService.getInstance();
        CredentialWatcher mockWatcher = mock(CredentialWatcher.class);
        Field watcherField = CredentialService.class.getDeclaredField("watcher");
        watcherField.setAccessible(true);
        watcherField.set(credentialService1, mockWatcher);
        Credentials credential = new Credentials();
        //when
        credentialService1.setStaticCredential(credential);
        //then
        Assert.assertEquals(credential, credentialService1.getCredential());
        verify(mockWatcher, times(1)).stop();
    }
    
    @Test
    public void testRegisterCredentialListener() {
        CredentialListener expect = mock(CredentialListener.class);
        CredentialService credentialService1 = CredentialService.getInstance();
        credentialService1.registerCredentialListener(expect);
        Credentials newCredentials = new Credentials();
        newCredentials.setAccessKey("ak");
        credentialService1.setCredential(newCredentials);
        verify(expect, times(1)).onUpdateCredential();
    }
}
