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
    
    private EncryptionPluginService mockEncryptionPluginService;
    
    @Before
    public void setUp() {
        mockEncryptionPluginService = new EncryptionPluginService() {
            @Override
            public String encrypt(String secretKey, String content) {
                return secretKey + content;
            }
            
            @Override
            public String decrypt(String secretKey, String content) {
                return content.replaceFirst(secretKey, "");
            }
            
            @Override
            public String generateSecretKey() {
                return "12345678";
            }
            
            @Override
            public String algorithmName() {
                return "mockAlgo";
            }
            
            @Override
            public String encryptSecretKey(String secretKey) {
                return secretKey + secretKey;
            }
            
            @Override
            public String decryptSecretKey(String secretKey) {
                return generateSecretKey();
            }
        };
        EncryptionPluginManager.join(mockEncryptionPluginService);
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
    
    @Test
    public void testCornerCaseDataIdAlgoParse() {
        String dataId = "cipher-";
        Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, "content");
        Assert.assertNotNull("should not throw exception when parsing enc algo for dataId '" + dataId + "'", pair);
    }
    
    @Test
    public void testUnknownAlgorithmNameEnc() {
        String dataId = "cipher-mySM4-application";
        String content = "content";
        Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
        Assert.assertNotNull(pair);
        Assert.assertEquals("should return original content if algorithm is not defined.", content, pair.getSecond());
    }
    
    @Test
    public void testUnknownAlgorithmNameDecrypt() {
        String dataId = "cipher-mySM4-application";
        String content = "content";
        Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, "", content);
        Assert.assertNotNull(pair);
        Assert.assertEquals("should return original content if algorithm is not defined.", content, pair.getSecond());
    }
    
    @Test
    public void testEncrypt() {
        String dataId = "cipher-mockAlgo-application";
        String content = "content";
        String sec = mockEncryptionPluginService.generateSecretKey();
        Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
        Assert.assertNotNull(pair);
        Assert.assertEquals("should return encrypted content.",
                mockEncryptionPluginService.encrypt(sec, content), pair.getSecond());
        Assert.assertEquals("should return encrypted secret key.",
                mockEncryptionPluginService.encryptSecretKey(sec), pair.getFirst());
    }
    
    @Test
    public void testDecrypt() {
        String dataId = "cipher-mockAlgo-application";
        String oContent = "content";
        String oSec = mockEncryptionPluginService.generateSecretKey();
        String content = mockEncryptionPluginService.encrypt(oSec, oContent);
        String sec = mockEncryptionPluginService.encryptSecretKey(oSec);
        Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, sec, content);
        Assert.assertNotNull(pair);
        Assert.assertEquals("should return original content.", oContent, pair.getSecond());
        Assert.assertEquals("should return original secret key.", oSec, pair.getFirst());
    }
}