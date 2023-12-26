/*
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
 */

package com.alibaba.nacos.plugin.encryption.handler;

import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.encryption.EncryptionPluginManager;
import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * AES encryption algorithm testing dataId with prefix cipher.
 *
 * @Author shiwenyu
 * @Date 2023/12/22 6:07 PM
 * @Version 1.0
 */
public class EncryptionAesHandlerTest {
    
    private EncryptionPluginService mockEncryptionPluginService;
    
    @Before
    public void setUp() {
        mockEncryptionPluginService = new EncryptionPluginService() {
            
            private static final String ALGORITHM = "AES";
            
            private static final String AES_PKCS5P = "AES/ECB/PKCS5Padding";
            
            // 随机生成密钥-用来加密数据内容
            private final String contentKey = generateKey();
            
            // 随机生成密钥-用来加密密钥
            private final String theKeyOfContentKey = generateKey();
            
            private String generateKey() {
                SecureRandom secureRandom = new SecureRandom();
                KeyGenerator keyGenerator;
                try {
                    keyGenerator = KeyGenerator.getInstance(ALGORITHM);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                keyGenerator.init(128, secureRandom);
                SecretKey secretKey = keyGenerator.generateKey();
                byte[] keyBytes = secretKey.getEncoded();
                return Base64.encodeBase64String(keyBytes);
            }
            
            @Override
            public String encrypt(String secretKey, String content) {
                return Base64.encodeBase64String(aes(Cipher.ENCRYPT_MODE, content, secretKey));
            }
            
            @Override
            public String decrypt(String secretKey, String content) {
                if (StringUtils.isBlank(secretKey)) {
                    return null;
                }
                return aesDecrypt(content, secretKey);
            }
            
            @Override
            public String generateSecretKey() {
                return contentKey;
            }
            
            @Override
            public String algorithmName() {
                return ALGORITHM.toLowerCase();
            }
            
            @Override
            public String encryptSecretKey(String secretKey) {
                return Base64.encodeBase64String(aes(Cipher.ENCRYPT_MODE, generateSecretKey(), theKeyOfContentKey));
            }
            
            @Override
            public String decryptSecretKey(String secretKey) {
                if (StringUtils.isBlank(secretKey)) {
                    return null;
                }
                return aesDecrypt(secretKey, theKeyOfContentKey);
            }
            
            private byte[] aes(int mode, String content, String key) {
                try {
                    return aesBytes(mode, content.getBytes(StandardCharsets.UTF_8), key);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            private byte[] aesBytes(int mode, byte[] content, String key) {
                SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
                Cipher cipher = null;
                try {
                    cipher = Cipher.getInstance(AES_PKCS5P);
                    cipher.init(mode, keySpec);
                    return cipher.doFinal(content);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            private String aesDecrypt(String content, String key) {
                byte[] bytes = aesBytes(Cipher.DECRYPT_MODE, Base64.decodeBase64(content), key);
                return new String(bytes, StandardCharsets.UTF_8);
            }
        };
        EncryptionPluginManager.join(mockEncryptionPluginService);
    }
    
    @Test
    public void testEncrypt() {
        String content = "content";
        String contentKey = mockEncryptionPluginService.generateSecretKey();
        Pair<String, String> pair = EncryptionHandler.encryptHandler("cipher-aes-dataId", content);
        Assert.assertEquals("should return the encryption secret key if algorithm defined.", mockEncryptionPluginService.encryptSecretKey(contentKey),
                pair.getFirst());
        Assert.assertEquals("should return the encryption content if algorithm defined.", mockEncryptionPluginService.encrypt(contentKey, content),
                pair.getSecond());
    }
    
    @Test
    public void testDecrypt() {
        String content = "content";
        String contentKey = mockEncryptionPluginService.generateSecretKey();
        String encryptionSecretKey = mockEncryptionPluginService.encryptSecretKey(contentKey);
        String encryptionContent = mockEncryptionPluginService.encrypt(contentKey, content);
        
        Pair<String, String> pair = EncryptionHandler.decryptHandler("cipher-aes-dataId", encryptionSecretKey, encryptionContent);
        
        Assert.assertEquals("should return the original secret key if algorithm defined.", mockEncryptionPluginService.generateSecretKey(),
                pair.getFirst());
        Assert.assertEquals("should return the original content if algorithm defined.", content, pair.getSecond());
        
    }
    
    @Test
    public void testEncryptAndDecrypt() {
        String dataId = "cipher-aes-dataId";
        String content = "content";
        String contentKey = mockEncryptionPluginService.generateSecretKey();
        
        Pair<String, String> encryptPair = EncryptionHandler.encryptHandler(dataId, content);
        String encryptionSecretKey = encryptPair.getFirst();
        String encryptionContent = encryptPair.getSecond();
        Assert.assertNotNull(encryptPair);
        Assert.assertEquals("should return the encryption secret key if algorithm defined.", mockEncryptionPluginService.encryptSecretKey(contentKey),
                encryptionSecretKey);
        Assert.assertEquals("should return the encryption content if algorithm defined.", mockEncryptionPluginService.encrypt(contentKey, content),
                encryptionContent);
        
        Pair<String, String> decryptPair = EncryptionHandler.decryptHandler(dataId, encryptionSecretKey, encryptionContent);
        Assert.assertNotNull(decryptPair);
        Assert.assertEquals("should return the original secret key if algorithm defined.", mockEncryptionPluginService.generateSecretKey(),
                decryptPair.getFirst());
        Assert.assertEquals("should return the original content if algorithm defined.", content, decryptPair.getSecond());
    }
    
    @Test
    public void testPrefixNotCipherEncrypt() {
        String content = "content";
        Pair<String, String> pair = EncryptionHandler.encryptHandler("test-dataId", content);
        Assert.assertNotNull(pair);
        Assert.assertEquals(pair.getFirst(), "");
        Assert.assertEquals(pair.getSecond(), content);
    }
    
    @Test
    public void testPrefixNotCipherDecrypt() {
        String content = "content";
        Pair<String, String> pair = EncryptionHandler.decryptHandler("test-dataId", "", content);
        Assert.assertNotNull(pair);
        Assert.assertEquals(pair.getFirst(), "");
        Assert.assertEquals(pair.getSecond(), content);
    }
    
    @Test
    public void testAlgorithmEmpty() {
        String dataId = "cipher-";
        String content = "content";
        Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
        Assert.assertNotNull("should not throw exception when parsing enc algo for dataId '" + dataId + "'", pair);
        Assert.assertEquals(pair.getFirst(), "");
        Assert.assertEquals(pair.getSecond(), content);
    }
    
    @Test
    public void testUnknownAlgorithmNameEncrypt() {
        String dataId = "cipher-mySM4-application";
        String content = "content";
        Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
        Assert.assertNotNull(pair);
        Assert.assertEquals(pair.getFirst(), "");
        Assert.assertEquals("should return original content if algorithm is not defined.", content, pair.getSecond());
    }
    
    @Test
    public void testUnknownAlgorithmNameDecrypt() {
        String dataId = "cipher-mySM4-application";
        String content = "content";
        Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, "", content);
        Assert.assertNotNull(pair);
        Assert.assertEquals(pair.getFirst(), "");
        Assert.assertEquals("should return original content if algorithm is not defined.", content, pair.getSecond());
    }
}
