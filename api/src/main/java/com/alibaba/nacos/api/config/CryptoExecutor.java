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

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CryptoExecuter.
 *
 * @author lixiaoshuang
 */
public class CryptoExecutor {
    
    /**
     * chipher-AES-dataId.
     */
    private static final String PREFIX = "chipher-";
    
    /**
     * Execute encryption.
     *
     * @param secretKey secretKey
     * @param content   content
     * @return encrypt value
     */
    public static String executeEncrypt(BiFunction<String, String, String> biFunc, String secretKey, String content) {
        return biFunc.apply(secretKey, content);
    }
    
    /**
     * Execute decryption.
     *
     * @param dataId    dataId
     * @param secretKey secretKey
     * @param content   content
     * @return decrypt value
     */
    public static String executeDecrypt(String dataId, String secretKey, String content) {
        CryptoSpi cryptoSpi = cryptoInstance(dataId);
        if (Objects.isNull(cryptoSpi)) {
            return content;
        }
        return cryptoSpi.decrypt(secretKey, content);
    }
    
    /**
     * Parse prefix match encryption algorithm.
     *
     * @param dataId dataId
     * @return Encryption algorithm instance
     */
    public static CryptoSpi cryptoInstance(String dataId) {
        boolean result = checkCipher(dataId);
        if (result) {
            String algorithmName = Stream.of(dataId.split("-")).collect(Collectors.toList()).get(1);
            return CryptoManager.instance(algorithmName);
        }
        return null;
    }
    
    /**
     * Check if encryption and decryption is needed.
     *
     * @param dataId dataId
     * @return boolean
     */
    private static boolean checkCipher(String dataId) {
        return dataId.startsWith(PREFIX);
    }
}
