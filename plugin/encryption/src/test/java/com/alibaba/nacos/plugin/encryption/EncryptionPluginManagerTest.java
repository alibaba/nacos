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

package com.alibaba.nacos.plugin.encryption;

import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EncryptionPluginManagerTest.
 *
 * @author lixiaoshuang
 */
class EncryptionPluginManagerTest {
    
    @AfterEach
    void tearDown() {
        PluginStateCheckerHolder.setInstance(null);
    }
    
    @Test
    void testInstance() {
        EncryptionPluginManager instance = EncryptionPluginManager.instance();
        assertNotNull(instance);
    }
    
    @Test
    void testJoin() {
        EncryptionPluginManager.join(new TestEncryptionPluginService("aes"));
        assertNotNull(EncryptionPluginManager.instance().findEncryptionService("aes"));
    }
    
    @Test
    void testFindEncryptionService() {
        EncryptionPluginManager instance = EncryptionPluginManager.instance();
        Optional<EncryptionPluginService> optional = instance.findEncryptionService("aes");
        assertTrue(optional.isPresent());
    }
    
    @Test
    void testDisabledNullJoinAndGetAllPlugins() {
        EncryptionPluginManager.join(null);
        PluginStateCheckerHolder.setInstance((pluginType, pluginName) -> false);
        
        Optional<EncryptionPluginService> optional =
            EncryptionPluginManager.instance().findEncryptionService("aes");
        
        assertFalse(optional.isPresent());
        assertThrows(UnsupportedOperationException.class,
            () -> EncryptionPluginManager.instance().getAllPlugins().clear());
    }
    
    @Test
    void testEncryptionPluginServiceMethods() {
        EncryptionPluginService service = new TestEncryptionPluginService("aes");
        
        assertEquals("content", service.encrypt("secretKey", "content"));
        assertEquals("content", service.decrypt("secretKey", "content"));
        assertEquals("12345678", service.generateSecretKey());
        assertEquals("aes", service.algorithmName());
        assertEquals("secretKey", service.encryptSecretKey("secretKey"));
        assertEquals("secretKey", service.decryptSecretKey("secretKey"));
    }
    
    @Test
    void testLoadInitialFromSpiSkipsBlankAlgorithmName() throws Exception {
        Map<String, EncryptionPluginService> plugins = getPlugins();
        Map<String, EncryptionPluginService> snapshot = new HashMap<>(plugins);
        plugins.clear();
        Method method = EncryptionPluginManager.class.getDeclaredMethod("loadInitial");
        method.setAccessible(true);
        
        try {
            method.invoke(EncryptionPluginManager.instance());
            
            assertTrue(EncryptionPluginManager.instance().findEncryptionService("spi-aes")
                .isPresent());
            assertFalse(EncryptionPluginManager.instance().findEncryptionService("").isPresent());
        } finally {
            plugins.clear();
            plugins.putAll(snapshot);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, EncryptionPluginService> getPlugins() throws Exception {
        Field field = EncryptionPluginManager.class.getDeclaredField("ENCRYPTION_SPI_MAP");
        field.setAccessible(true);
        return (Map<String, EncryptionPluginService>) field.get(null);
    }
    
    private static class TestEncryptionPluginService implements EncryptionPluginService {
        
        private final String algorithmName;
        
        private TestEncryptionPluginService(String algorithmName) {
            this.algorithmName = algorithmName;
        }
        
        @Override
        public String encrypt(String secretKey, String content) {
            return content;
        }
        
        @Override
        public String decrypt(String secretKey, String content) {
            return content;
        }
        
        @Override
        public String generateSecretKey() {
            return "12345678";
        }
        
        @Override
        public String algorithmName() {
            return algorithmName;
        }
        
        @Override
        public String encryptSecretKey(String secretKey) {
            return secretKey;
        }
        
        @Override
        public String decryptSecretKey(String secretKey) {
            return secretKey;
        }
    }
}
