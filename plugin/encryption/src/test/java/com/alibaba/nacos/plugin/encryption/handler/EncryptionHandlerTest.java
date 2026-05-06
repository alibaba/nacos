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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * EncryptionHandlerTest.
 *
 * @author lixiaoshuang
 */
class EncryptionHandlerTest {
    
    private EncryptionPluginService mockEncryptionPluginService;
    
    @BeforeEach
    void setUp() {
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
    void testEncryptHandler() {
        Pair<String, String> pair = EncryptionHandler.encryptHandler("test-dataId", "content");
        assertNotNull(pair);
    }
    
    @Test
    void testDecryptHandler() {
        Pair<String, String> pair = EncryptionHandler.decryptHandler("test-dataId", "12345678", "content");
        assertNotNull(pair);
    }
    
    @Test
    void testCornerCaseDataIdAlgoParse() {
        String dataId = "cipher-";
        Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, "content");
        assertNotNull(pair, "should not throw exception when parsing enc algo for dataId '" + dataId + "'");
    }
    
    @Test
    void testUnknownAlgorithmNameEnc() {
        String dataId = "cipher-mySM4-application";
        String content = "content";
        Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
        assertNotNull(pair);
        assertEquals(content, pair.getSecond(), "should return original content if algorithm is not defined.");
    }
    
    @Test
    void testUnknownAlgorithmNameDecrypt() {
        String dataId = "cipher-mySM4-application";
        String content = "content";
        Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, "", content);
        assertNotNull(pair);
        assertEquals(content, pair.getSecond(), "should return original content if algorithm is not defined.");
    }
    
    @Test
    void testEncrypt() {
        String dataId = "cipher-mockAlgo-application";
        String content = "content";
        String sec = mockEncryptionPluginService.generateSecretKey();
        Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
        assertNotNull(pair);
        assertEquals(mockEncryptionPluginService.encrypt(sec, content), pair.getSecond(), "should return encrypted content.");
        assertEquals(mockEncryptionPluginService.encryptSecretKey(sec), pair.getFirst(), "should return encrypted secret key.");
    }
    
    @Test
    void testDecrypt() {
        String dataId = "cipher-mockAlgo-application";
        String oContent = "content";
        String oSec = mockEncryptionPluginService.generateSecretKey();
        String content = mockEncryptionPluginService.encrypt(oSec, oContent);
        String sec = mockEncryptionPluginService.encryptSecretKey(oSec);
        Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, sec, content);
        assertNotNull(pair);
        assertEquals(oContent, pair.getSecond(), "should return original content.");
        assertEquals(oSec, pair.getFirst(), "should return original secret key.");
    }
}
