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

package com.alibaba.nacos.plugin.encryption.spi;

/**
 * Encryption and decryption spi.
 *
 * @author lixiaoshuang
 */
public interface EncryptionPluginService {
    
    /**
     * Encrypted interface.
     *
     * @param secretKey secret key
     * @param content   content unencrypted
     * @return encrypt value
     */
    String encrypt(String secretKey, String content);
    
    /**
     * Decryption interface.
     *
     * @param secretKey secret key
     * @param content   encrypted
     * @return decrypt value
     */
    String decrypt(String secretKey, String content);
    
    /**
     * Generate Secret key.
     *
     * @return Secret key
     */
    String generateSecretKey();
    
    /**
     * Algorithm naming.
     *
     * @return name
     */
    String algorithmName();
    
    /**
     * encrypt secretKey.
     * @param secretKey secretKey
     * @return encrypted secretKey
     */
    String encryptSecretKey(String secretKey);
    
    /**
     * decrypt secretKey.
     * @param secretKey secretKey
     * @return decrypted secretKey
     */
    String decryptSecretKey(String secretKey);
}
