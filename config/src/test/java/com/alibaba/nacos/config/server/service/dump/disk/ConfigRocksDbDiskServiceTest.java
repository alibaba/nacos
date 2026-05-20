/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.dump.disk;

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigRocksDbDiskServiceTest {
    
    private static final String BASE_DIR = File.separator + "rocksdata" + File.separator
        + "config-data";
    
    private static final String GRAY_DIR = File.separator + "rocksdata" + File.separator
        + "gray-data";
    
    @TempDir
    private File tempDir;
    
    private MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @BeforeEach
    void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
    }
    
    @AfterEach
    void tearDown() {
        envUtilMockedStatic.close();
    }
    
    @Test
    void testConstructorCreatesRocksDataDirs() {
        new ConfigRocksDbDiskService();
        
        assertTrue(new File(tempDir, "rocksdata").exists());
        assertTrue(new File(tempDir.getAbsolutePath() + BASE_DIR).exists());
        assertTrue(new File(tempDir.getAbsolutePath() + GRAY_DIR).exists());
    }
    
    @Test
    void testGetKeyByteWhenKeysEmpty() throws Exception {
        ConfigRocksDbDiskService service = new ConfigRocksDbDiskService();
        
        assertArrayEquals(new byte[0], invokeGetKeyByte(service));
        assertArrayEquals(new byte[0], invokeGetKeyByteWithNullArray(service));
    }
    
    @Test
    void testGetKeyByteEncodesReservedCharactersAndBlankKeys() throws Exception {
        ConfigRocksDbDiskService service = new ConfigRocksDbDiskService();
        
        byte[] keyBytes = invokeGetKeyByte(service, "a+b%c", " ", null);
        
        assertEquals("a%2Bb%25c+++", new String(keyBytes, StandardCharsets.UTF_8));
    }
    
    @Test
    void testSaveAndRemoveUseRocksDb() throws Exception {
        RocksDB rocksDB = mock(RocksDB.class);
        TestConfigRocksDbDiskService service = new TestConfigRocksDbDiskService(rocksDB);
        
        service.saveToDisk("dataId", "group", "tenant", "content");
        service.saveGrayToDisk("dataId", "group", "tenant", "gray", "grayContent");
        service.removeConfigInfo("dataId", "group", "tenant");
        service.removeConfigInfo4Gray("dataId", "group", "tenant", "gray");
        
        verify(rocksDB, times(2)).put(any(byte[].class), any(byte[].class));
        verify(rocksDB, times(2)).delete(any(byte[].class));
    }
    
    @Test
    void testReadContentAndMd5FromRocksDb() throws Exception {
        RocksDB rocksDB = mock(RocksDB.class);
        when(rocksDB.get(any(byte[].class))).thenReturn(bytes("content"), bytes("gray"),
            bytes("md5-content"), null);
        TestConfigRocksDbDiskService service = new TestConfigRocksDbDiskService(rocksDB);
        
        assertEquals("content", service.getContent("dataId", "group", "tenant"));
        assertEquals("gray", service.getGrayContent("dataId", "group", "tenant", "gray"));
        assertEquals(MD5Utils.md5Hex("md5-content", "UTF-8"),
            service.getLocalConfigMd5("dataId", "group", "tenant", "UTF-8"));
        assertNull(service.getContent("missing", "group", "tenant"));
    }
    
    @Test
    void testSaveThrowsIoExceptionWhenRocksDbPutFails() throws Exception {
        RocksDB rocksDB = mock(RocksDB.class);
        doThrow(new RocksDBException("put failed")).when(rocksDB)
            .put(any(byte[].class), any(byte[].class));
        TestConfigRocksDbDiskService service = new TestConfigRocksDbDiskService(rocksDB);
        
        assertThrows(IOException.class,
            () -> service.saveToDisk("dataId", "group", "tenant", "content"));
        assertThrows(IOException.class,
            () -> service.saveGrayToDisk("dataId", "group", "tenant", "gray", "content"));
    }
    
    @Test
    void testReadThrowsIoExceptionWhenRocksDbGetFails() throws Exception {
        RocksDB rocksDB = mock(RocksDB.class);
        when(rocksDB.get(any(byte[].class))).thenThrow(new RocksDBException("get failed"));
        TestConfigRocksDbDiskService service = new TestConfigRocksDbDiskService(rocksDB);
        
        assertThrows(IOException.class, () -> service.getContent("dataId", "group", "tenant"));
        assertThrows(IOException.class,
            () -> service.getGrayContent("dataId", "group", "tenant", "gray"));
    }
    
    @Test
    void testRemoveIgnoresRocksDbDeleteFailure() throws Exception {
        RocksDB rocksDB = mock(RocksDB.class);
        doThrow(new RocksDBException("delete failed")).when(rocksDB).delete(any(byte[].class));
        TestConfigRocksDbDiskService service = new TestConfigRocksDbDiskService(rocksDB);
        
        service.removeConfigInfo("dataId", "group", "tenant");
        service.removeConfigInfo4Gray("dataId", "group", "tenant", "gray");
        
        verify(rocksDB, times(2)).delete(any(byte[].class));
    }
    
    @Test
    void testCreateOptionsForGrayDir() {
        ConfigRocksDbDiskService service = new ConfigRocksDbDiskService();
        
        ColumnFamilyOptions columnFamilyOptions = service.createColumnFamilyOptions(GRAY_DIR);
        Options options = service.createOptions(GRAY_DIR);
        
        assertNotNull(columnFamilyOptions);
        assertNotNull(options);
        columnFamilyOptions.close();
        options.close();
    }
    
    @Test
    void testDeleteDirReturnsWhenRocksDataMissing() throws Exception {
        ConfigRocksDbDiskService service = new ConfigRocksDbDiskService();
        deleteRecursively(new File(tempDir, "rocksdata"));
        
        Method method = ConfigRocksDbDiskService.class.getDeclaredMethod("deleteDirIfExist",
            String.class);
        method.setAccessible(true);
        method.invoke(service, BASE_DIR);
        
        assertTrue(tempDir.exists());
    }
    
    @Test
    void testInitAndGetDbReturnsSecondCheckedDb() throws Exception {
        RocksDB rocksDB = mock(RocksDB.class);
        ConfigRocksDbDiskService service = new ConfigRocksDbDiskService();
        FlippingContainsMap rocksDbMap = new FlippingContainsMap();
        rocksDbMap.put(BASE_DIR, rocksDB);
        service.rocksDbMap = rocksDbMap;
        
        assertEquals(rocksDB, service.initAndGetDB(BASE_DIR));
    }
    
    @Test
    void testCreateDirIfEmptyCreatesMissingDir() throws Exception {
        ConfigRocksDbDiskService service = new ConfigRocksDbDiskService();
        File missingDir = new File(tempDir, "missing-rocks-dir");
        Method method = ConfigRocksDbDiskService.class.getDeclaredMethod("createDirIfEmpty",
            String.class);
        method.setAccessible(true);
        
        method.invoke(service, missingDir.getAbsolutePath());
        
        assertTrue(missingDir.exists());
    }
    
    @Test
    void testClearAllDestroysDbFailureIsIgnored() throws Exception {
        RocksDB rocksDB = mock(RocksDB.class);
        ConfigRocksDbDiskService service = new ConfigRocksDbDiskService();
        service.rocksDbMap.put(BASE_DIR, rocksDB);
        
        try (MockedStatic<RocksDB> rocksDbMockedStatic = Mockito.mockStatic(RocksDB.class)) {
            rocksDbMockedStatic.when(() -> RocksDB.destroyDB(anyString(), any(Options.class)))
                .thenThrow(new RocksDBException("destroy failed"));
            
            service.clearAll();
        }
        
        verify(rocksDB, times(1)).close();
    }
    
    @Test
    void testClearAllGrayClosesAndDestroysDb() throws Exception {
        RocksDB rocksDB = mock(RocksDB.class);
        ConfigRocksDbDiskService service = new ConfigRocksDbDiskService();
        service.rocksDbMap.put(GRAY_DIR, rocksDB);
        
        try (MockedStatic<RocksDB> rocksDbMockedStatic = Mockito.mockStatic(RocksDB.class)) {
            service.clearAllGray();
            
            rocksDbMockedStatic.verify(
                () -> RocksDB.destroyDB(anyString(), any(Options.class)), times(1));
        }
        verify(rocksDB, times(1)).close();
    }
    
    @Test
    void testClearAllGrayDestroyDbFailureIsIgnored() throws Exception {
        RocksDB rocksDB = mock(RocksDB.class);
        ConfigRocksDbDiskService service = new ConfigRocksDbDiskService();
        service.rocksDbMap.put(GRAY_DIR, rocksDB);
        
        try (MockedStatic<RocksDB> rocksDbMockedStatic = Mockito.mockStatic(RocksDB.class)) {
            rocksDbMockedStatic.when(() -> RocksDB.destroyDB(anyString(), any(Options.class)))
                .thenThrow(new RocksDBException("destroy failed"));
            
            service.clearAllGray();
        }
        
        verify(rocksDB, times(1)).close();
    }
    
    private byte[] invokeGetKeyByte(ConfigRocksDbDiskService service, String... keys)
        throws Exception {
        Method method = ConfigRocksDbDiskService.class.getDeclaredMethod("getKeyByte",
            String[].class);
        method.setAccessible(true);
        return (byte[]) method.invoke(service, (Object) keys);
    }
    
    private byte[] invokeGetKeyByteWithNullArray(ConfigRocksDbDiskService service)
        throws Exception {
        Method method = ConfigRocksDbDiskService.class.getDeclaredMethod("getKeyByte",
            String[].class);
        method.setAccessible(true);
        return (byte[]) method.invoke(service, new Object[] {null});
    }
    
    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
    
    private void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
    
    private static class TestConfigRocksDbDiskService extends ConfigRocksDbDiskService {
        
        private final RocksDB rocksDB;
        
        TestConfigRocksDbDiskService(RocksDB rocksDB) {
            this.rocksDB = rocksDB;
        }
        
        @Override
        RocksDB initAndGetDB(String dir) {
            return rocksDB;
        }
    }
    
    private static class FlippingContainsMap extends HashMap<String, RocksDB> {
        
        private int containsCount;
        
        @Override
        public boolean containsKey(Object key) {
            containsCount++;
            return containsCount > 1 && super.containsKey(key);
        }
    }
}
