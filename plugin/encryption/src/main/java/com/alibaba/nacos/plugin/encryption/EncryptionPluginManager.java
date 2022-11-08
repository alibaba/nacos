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

package com.alibaba.nacos.plugin.encryption;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encryption Plugin Management.
 *
 * @author lixiaoshuang
 */
public class EncryptionPluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptionPluginManager.class);
    
    private static final Map<String, EncryptionPluginService> ENCRYPTION_SPI_MAP = new ConcurrentHashMap<>();
    
    private static final EncryptionPluginManager INSTANCE = new EncryptionPluginManager();
    
    private EncryptionPluginManager() {
        loadInitial();
    }
    
    /**
     * Load initial.
     */
    private void loadInitial() {
        Collection<EncryptionPluginService> encryptionPluginServices = NacosServiceLoader.load(
                EncryptionPluginService.class);
        for (EncryptionPluginService encryptionPluginService : encryptionPluginServices) {
            if (StringUtils.isBlank(encryptionPluginService.algorithmName())) {
                LOGGER.warn("[EncryptionPluginManager] Load EncryptionPluginService({}) algorithmName(null/empty) fail."
                        + " Please Add algorithmName to resolve.", encryptionPluginService.getClass());
                continue;
            }
            ENCRYPTION_SPI_MAP.put(encryptionPluginService.algorithmName(), encryptionPluginService);
            LOGGER.info("[EncryptionPluginManager] Load EncryptionPluginService({}) algorithmName({}) successfully.",
                    encryptionPluginService.getClass(), encryptionPluginService.algorithmName());
        }
    }
    
    /**
     * Get EncryptionPluginManager instance.
     *
     * @return EncryptionPluginManager
     */
    public static EncryptionPluginManager instance() {
        return INSTANCE;
    }
    
    /**
     * get EncryptionPluginService instance.
     *
     * @param algorithmName algorithmName, mark a EncryptionPluginService instance.
     * @return EncryptionPluginService instance.
     */
    public Optional<EncryptionPluginService> findEncryptionService(String algorithmName) {
        return Optional.ofNullable(ENCRYPTION_SPI_MAP.get(algorithmName));
    }
    
    /**
     * Injection realization.
     *
     * @param encryptionPluginService Encryption implementation
     */
    public static synchronized void join(EncryptionPluginService encryptionPluginService) {
        if (Objects.isNull(encryptionPluginService)) {
            return;
        }
        ENCRYPTION_SPI_MAP.put(encryptionPluginService.algorithmName(), encryptionPluginService);
        LOGGER.info("[EncryptionPluginManager] join successfully.");
    }
    
}
