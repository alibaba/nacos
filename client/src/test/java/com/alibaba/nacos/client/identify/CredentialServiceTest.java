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

package com.alibaba.nacos.client.identify;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CredentialServiceTest extends TestCase {
    
    @Test
    public void testGetInstance() {
        CredentialService credentialService1 = CredentialService.getInstance();
        CredentialService credentialService2 = CredentialService.getInstance();
        Assert.assertEquals(credentialService1, credentialService2);
    }
    
    @Test
    public void testGetInstance2() {
        CredentialService credentialService1 = CredentialService.getInstance("app");
        CredentialService credentialService2 = CredentialService.getInstance("app");
        Assert.assertEquals(credentialService1, credentialService2);
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
    public void testRegisterCredentialListener() throws NoSuchFieldException, IllegalAccessException {
        CredentialService credentialService1 = CredentialService.getInstance();
        Field listenerField = CredentialService.class.getDeclaredField("listener");
        listenerField.setAccessible(true);
        CredentialListener expect = mock(CredentialListener.class);
        //when
        credentialService1.registerCredentialListener(expect);
        //then
        CredentialListener actual = (CredentialListener) listenerField.get(credentialService1);
        Assert.assertEquals(expect, actual);
        
    }
    
    @Test
    public void testGetAkAndSk() {
        CredentialService credentialService1 = CredentialService.getInstance();
        Credentials c = new Credentials();
        c.setAccessKey("ak");
        c.setSecretKey("sk");
        credentialService1.setCredential(c);
        
        Assert.assertEquals("ak", credentialService1.getAccessKey());
        Assert.assertEquals("sk", credentialService1.getSecretKey());
    }
    
    @Test
    public void testSetSecretKey() {
        CredentialService credentialService1 = CredentialService.getInstance();
        Credentials c = new Credentials();
        c.setAccessKey("ak");
        c.setSecretKey("sk");
        credentialService1.setCredential(c);
        credentialService1.setAccessKey("ak1");
        credentialService1.setSecretKey("sk1");
        
        Assert.assertEquals("ak1", credentialService1.getAccessKey());
        Assert.assertEquals("sk1", credentialService1.getSecretKey());
    }
    
}