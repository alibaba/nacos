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

package com.alibaba.nacos.client.config;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * AesCryptoTest.
 *
 * @author lixiaoshuang
 */
public class AesCryptoTest {
    
    private AesCrypto aesCrypto;
    
    @Before
    public void setUp() throws Exception {
        aesCrypto = new AesCrypto();
    }
    
    @Test
    public void testEncrypt() {
        String secretKey = aesCrypto.generateSecretKey();
        String content = "nacos";
        String encrypt = aesCrypto.encrypt(secretKey, content);
        Assert.assertNotNull(encrypt);
    }
    
    @Test
    public void testDecrypt() {
        String secretKey = aesCrypto.generateSecretKey();
        String content = "nacos";
        String encrypt = aesCrypto.encrypt(secretKey, content);
        String decrypt = aesCrypto.decrypt(secretKey, encrypt);
        Assert.assertNotNull(decrypt);
    }
    
    @Test
    public void testGenerateSecretKey() {
        String secretKey = aesCrypto.generateSecretKey();
        Assert.assertNotNull(secretKey);
    }
    
    @Test
    public void testNamed() {
        String named = aesCrypto.named();
        Assert.assertEquals(named, "aes");
    }
}