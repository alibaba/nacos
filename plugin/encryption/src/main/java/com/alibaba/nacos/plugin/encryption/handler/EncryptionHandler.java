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
import com.alibaba.nacos.plugin.encryption.EncryptionPluginManager;
import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * EncryptionHandler.
 *
 * @author lixiaoshuang
 */
public class EncryptionHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptionHandler.class);
    
    /**
     * For example：cipher-AES-dataId.
     */
    private static final String PREFIX = "cipher-";
    
    /**
     * Execute encryption.
     *
     * @param dataId  dataId
     * @param content Content that needs to be encrypted.
     * @return Return key and ciphertext.
     */
    public static Pair<String, String> encryptHandler(String dataId, String content) {
        if (!checkCipher(dataId)) {
            return Pair.with("", content);
        }
        Optional<String> algorithmName = parseAlgorithmName(dataId);
        Optional<EncryptionPluginService> optional = algorithmName.flatMap(
                EncryptionPluginManager.instance()::findEncryptionService);
        if (!optional.isPresent()) {
            LOGGER.warn("[EncryptionHandler] [encryptHandler] No encryption program with the corresponding name found");
            return Pair.with("", content);
        }
        EncryptionPluginService encryptionPluginService = optional.get();
        String secretKey = encryptionPluginService.generateSecretKey();
        String encryptContent = encryptionPluginService.encrypt(secretKey, content);
        return Pair.with(encryptionPluginService.encryptSecretKey(secretKey), encryptContent);
    }
    
    /**
     * Execute decryption.
     *
     * @param dataId    dataId
     * @param secretKey Decryption key.
     * @param content   Content that needs to be decrypted.
     * @return Return key and plaintext.
     */
    public static Pair<String, String> decryptHandler(String dataId, String secretKey, String content) {
        if (!checkCipher(dataId)) {
            return Pair.with(secretKey, content);
        }
        Optional<String> algorithmName = parseAlgorithmName(dataId);
        Optional<EncryptionPluginService> optional = algorithmName.flatMap(
                EncryptionPluginManager.instance()::findEncryptionService);
        if (!optional.isPresent()) {
            LOGGER.warn("[EncryptionHandler] [decryptHandler] No encryption program with the corresponding name found");
            return Pair.with(secretKey, content);
        }
        EncryptionPluginService encryptionPluginService = optional.get();
        String decryptSecretKey = encryptionPluginService.decryptSecretKey(secretKey);
        String decryptContent = encryptionPluginService.decrypt(decryptSecretKey, content);
        return Pair.with(decryptSecretKey, decryptContent);
    }
    
    /**
     * Parse encryption algorithm name.
     *
     * @param dataId dataId
     * @return algorithm name
     */
    private static Optional<String> parseAlgorithmName(String dataId) {
        return Stream.of(dataId.split("-")).skip(1).findFirst();
    }
    
    /**
     * Check if encryption and decryption is needed.
     *
     * @param dataId dataId
     * @return boolean whether data id needs encrypt
     */
    private static boolean checkCipher(String dataId) {
        return dataId.startsWith(PREFIX) && !PREFIX.equals(dataId);
    }
}
