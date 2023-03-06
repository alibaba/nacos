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

package com.alibaba.nacos.plugin.encryption;

import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

/**
 * EncryptionPluginManagerTest.
 *
 * @author lixiaoshuang
 */
public class EncryptionPluginManagerTest {
    
    @Test
    public void testInstance() {
        EncryptionPluginManager instance = EncryptionPluginManager.instance();
        Assert.assertNotNull(instance);
    }
    
    @Test
    public void testJoin() {
        EncryptionPluginManager.join(new EncryptionPluginService() {
            @Override
            public String encrypt(String secretKey, String content) {
                return content;
            }
            
            @Override
            public String decrypt(String secretKey, String content) {
                return content;
            }
            
            @Override
            public String generateSecretKey() {
                return "12345678";
            }
            
            @Override
            public String algorithmName() {
                return "aes";
            }
    
            @Override
            public String encryptSecretKey(String secretKey) {
                return secretKey;
            }
    
            @Override
            public String decryptSecretKey(String secretKey) {
                return secretKey;
            }
        });
        Assert.assertNotNull(EncryptionPluginManager.instance().findEncryptionService("aes"));
    }
    
    @Test
    public void testFindEncryptionService() {
        EncryptionPluginManager instance = EncryptionPluginManager.instance();
        Optional<EncryptionPluginService> optional = instance.findEncryptionService("aes");
        Assert.assertTrue(optional.isPresent());
    }
    
}