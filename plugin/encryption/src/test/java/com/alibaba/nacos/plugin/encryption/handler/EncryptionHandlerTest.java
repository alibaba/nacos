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

package com.alibaba.nacos.plugin.encryption.handler;

import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.plugin.encryption.EncryptionPluginManager;
import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * EncryptionHandlerTest.
 *
 * @author lixiaoshuang
 */
public class EncryptionHandlerTest {
    
    @Before
    public void setUp() {
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
        });
    }
    
    @Test
    public void testEncryptHandler() {
        Pair<String, String> pair = EncryptionHandler.encryptHandler("test-dataId", "content");
        Assert.assertNotNull(pair);
    }
    
    @Test
    public void testDecryptHandler() {
        Pair<String, String> pair = EncryptionHandler.decryptHandler("test-dataId", "12345678", "content");
        Assert.assertNotNull(pair);
    }
}