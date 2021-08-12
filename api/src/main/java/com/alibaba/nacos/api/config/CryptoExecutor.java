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

package com.alibaba.nacos.api.config;

/**
 * CryptoExecuter.
 *
 * @author  lixiaoshuang
 */
public class CryptoExecutor {
    
    private static final String PREFIX = "chipher-";
    
    /**
     * Check if encryption and decryption is needed.
     *
     * @param dataId dataId
     * @return boolean
     */
    public static boolean checkCipher(String dataId) {
        if (dataId.startsWith(PREFIX)) {
            return true;
        }
        return false;
    }
    
    /**
     * Execute encryption.
     *
     * @param dataId  dataId
     * @param content content
     * @return
     */
    public static String executeEncrypt(String dataId, String content) {
        CryptoSpi cryptoSpi = cryptoInstance(dataId);
        return cryptoSpi.encrypt(cryptoSpi.generateSecretKey(), content);
    }
    
    /**
     * Execute decryption.
     *
     * @param dataId  dataId
     * @param content content
     * @return
     */
    public static String executeDecrypt(String dataId, String content) {
        CryptoSpi cryptoSpi = cryptoInstance(dataId);
        return cryptoSpi.decrypt("", content);
    }
    
    /**
     * Parse prefix match encryption algorithm.
     *
     * @param dataId dataId
     * @return Encryption algorithm instance
     */
    private static CryptoSpi cryptoInstance(String dataId) {
        return CryptoManager.instance("AES");
    }
}
