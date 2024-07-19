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

package com.alibaba.nacos.client.config.filter.impl;

import com.alibaba.nacos.api.config.filter.IConfigFilterChain;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.encryption.EncryptionPluginManager;
import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AES encryption algorithm testing dataId with prefix cipher.
 *
 * @Author shiwenyu
 * @Date 2023/12/23 9:45 PM
 * @Version 1.0
 */
@ExtendWith(MockitoExtension.class)
class ConfigEncryptionFilterTest1 {
    
    private ConfigEncryptionFilter configEncryptionFilter;
    
    private EncryptionPluginService mockEncryptionPluginService;
    
    @Mock
    private IConfigFilterChain iConfigFilterChain;
    
    @BeforeEach
    void setUp() throws Exception {
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
        
        configEncryptionFilter = new ConfigEncryptionFilter();
    }
    
    @Test
    void testDoFilterEncryptedData() throws NacosException {
        String dataId = "cipher-aes-test";
        String content = "nacos";
        final String encryptionContent = mockEncryptionPluginService.encrypt(
                mockEncryptionPluginService.generateSecretKey(), content);
        final String theKeyOfContentKey = mockEncryptionPluginService.encryptSecretKey(
                mockEncryptionPluginService.generateSecretKey());
        
        ConfigRequest configRequest = new ConfigRequest();
        configRequest.setDataId(dataId);
        configRequest.setContent(content);
        configEncryptionFilter.doFilter(configRequest, null, iConfigFilterChain);
        assertEquals(configRequest.getContent(), encryptionContent);
        assertEquals(configRequest.getEncryptedDataKey(), theKeyOfContentKey);
        
        ConfigResponse configResponse = new ConfigResponse();
        configResponse.setDataId(dataId);
        configResponse.setContent(encryptionContent);
        configResponse.setEncryptedDataKey(theKeyOfContentKey);
        configEncryptionFilter.doFilter(null, configResponse, iConfigFilterChain);
        assertEquals(configResponse.getContent(), content);
        assertEquals(configResponse.getEncryptedDataKey(), mockEncryptionPluginService.generateSecretKey());
    }
    
    @Test
    void testDoFilter() throws NacosException {
        String dataId = "test";
        String content = "nacos";
        
        ConfigRequest configRequest = new ConfigRequest();
        configRequest.setDataId(dataId);
        configRequest.setContent(content);
        configEncryptionFilter.doFilter(configRequest, null, iConfigFilterChain);
        assertEquals(configRequest.getContent(), content);
        assertEquals("", configRequest.getEncryptedDataKey());
        
        ConfigResponse configResponse = new ConfigResponse();
        configResponse.setDataId(dataId);
        configResponse.setContent(content);
        configResponse.setEncryptedDataKey("");
        configEncryptionFilter.doFilter(null, configResponse, iConfigFilterChain);
        assertEquals(configResponse.getContent(), content);
        assertEquals("", configResponse.getEncryptedDataKey());
    }
    
    @Test
    void testGetOrder() {
        int order = configEncryptionFilter.getOrder();
        assertEquals(0, order);
    }
}
