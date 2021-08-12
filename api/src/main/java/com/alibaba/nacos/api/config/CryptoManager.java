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

package com.alibaba.nacos.api.config;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CryptoManager.
 *
 * @author lixiaoshuang
 */
public class CryptoManager {
    
    private static final Map<String, CryptoSpi> CRYPTO_SPI_MAP = new ConcurrentHashMap<>();
    
    static {
        loadInitial();
    }
    
    /**
     * Load initial.
     */
    private static void loadInitial() {
        ServiceLoader<CryptoSpi> serviceLoaders = ServiceLoader.load(CryptoSpi.class);
        for (CryptoSpi cryptoSpi : serviceLoaders) {
            CRYPTO_SPI_MAP.put(cryptoSpi.named(), cryptoSpi);
        }
    }
    
    /**
     * Get an instance of an algorithm.
     *
     * @param name algorithm name
     * @return
     */
    public static CryptoSpi instance(String name) {
        return CRYPTO_SPI_MAP.get(name);
    }
    
    /**
     * Injection realization.
     *
     * @param cryptoSpi Encryption implementation
     */
    public static synchronized void join(CryptoSpi cryptoSpi) {
        CRYPTO_SPI_MAP.put(cryptoSpi.named(), cryptoSpi);
    }
}
