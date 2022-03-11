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

package com.alibaba.nacos.plugin.encryption.impl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * AesEncryptionPluginServiceTest.
 *
 * @author lixiaoshuang
 */
public class AesEncryptionPluginServiceTest {
    
    private AesEncryptionPluginService aesEncryptionPluginService;
    
    private static final String CONTENT = "nacos";
    
    @Before
    public void setUp() throws Exception {
        aesEncryptionPluginService = new AesEncryptionPluginService();
    }
    
    @Test
    public void testEncrypt() {
        String secretKey = aesEncryptionPluginService.generateSecretKey();
        String encrypt = aesEncryptionPluginService.encrypt(secretKey, CONTENT);
        Assert.assertNotNull(encrypt);
    }
    
    @Test
    public void testDecrypt() {
        String secretKey = aesEncryptionPluginService.generateSecretKey();
        String encrypt = aesEncryptionPluginService.encrypt(secretKey, CONTENT);
        String decrypt = aesEncryptionPluginService.decrypt(secretKey, encrypt);
        Assert.assertNotNull(decrypt);
    }
    
    @Test
    public void testGenerateSecretKey() {
        String secretKey = aesEncryptionPluginService.generateSecretKey();
        Assert.assertNotNull(secretKey);
    }
    
    @Test
    public void testNamed() {
        String named = aesEncryptionPluginService.algorithmName();
        Assert.assertEquals(named, "aes");
    }
}