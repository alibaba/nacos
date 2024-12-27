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

package com.alibaba.nacos.common.tls;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class TlsFileWatcherTest {
    
    static Field watchFilesMapField;
    
    static Field fileMd5MapField;
    
    static Field serviceField;
    
    static Field startedField;
    
    File tempFile;
    
    @Mock
    ScheduledExecutorService executorService;
    
    @BeforeAll
    static void setUpBeforeClass() throws NoSuchFieldException, IllegalAccessException {
        watchFilesMapField = TlsFileWatcher.getInstance().getClass().getDeclaredField("watchFilesMap");
        watchFilesMapField.setAccessible(true);
        Field modifiersField1 = Field.class.getDeclaredField("modifiers");
        modifiersField1.setAccessible(true);
        modifiersField1.setInt(watchFilesMapField, watchFilesMapField.getModifiers() & ~Modifier.FINAL);
        
        fileMd5MapField = TlsFileWatcher.getInstance().getClass().getDeclaredField("fileMd5Map");
        fileMd5MapField.setAccessible(true);
        
        serviceField = TlsFileWatcher.getInstance().getClass().getDeclaredField("service");
        serviceField.setAccessible(true);
        Field modifiersField2 = Field.class.getDeclaredField("modifiers");
        modifiersField2.setAccessible(true);
        modifiersField2.setInt(watchFilesMapField, watchFilesMapField.getModifiers() & ~Modifier.FINAL);
        
        startedField = TlsFileWatcher.getInstance().getClass().getDeclaredField("started");
        startedField.setAccessible(true);
        Field modifiersField3 = Field.class.getDeclaredField("modifiers");
        modifiersField3.setAccessible(true);
        modifiersField3.setInt(watchFilesMapField, watchFilesMapField.getModifiers() & ~Modifier.FINAL);
    }
    
    @BeforeEach
    void setUp() throws IOException, IllegalAccessException {
        tempFile = new File("test.txt");
        tempFile.createNewFile();
        serviceField.set(TlsFileWatcher.getInstance(), executorService);
        startedField.set(TlsFileWatcher.getInstance(), new AtomicBoolean(false));
        Answer<?> answer = invocationOnMock -> {
            Runnable runnable = (Runnable) invocationOnMock.getArguments()[0];
            runnable.run();
            return null;
        };
        doAnswer(answer).when(executorService).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    }
    
    @AfterEach
    void tearDown() throws IllegalAccessException {
        ((Map<?, ?>) watchFilesMapField.get(TlsFileWatcher.getInstance())).clear();
        ((Map<?, ?>) fileMd5MapField.get(TlsFileWatcher.getInstance())).clear();
        tempFile.deleteOnExit();
    }
    
    @Test
    void testAddFileChangeListener1() throws IOException, IllegalAccessException {
        TlsFileWatcher.getInstance().addFileChangeListener(filePath -> {
        }, "not/exist/path");
        
        assertTrue(((Map<?, ?>) watchFilesMapField.get(TlsFileWatcher.getInstance())).isEmpty());
        assertTrue(((Map<?, ?>) fileMd5MapField.get(TlsFileWatcher.getInstance())).isEmpty());
    }
    
    @Test
    void testAddFileChangeListener2() throws IOException, IllegalAccessException {
        TlsFileWatcher.getInstance().addFileChangeListener(filePath -> {
        }, (String) null);
        
        assertTrue(((Map<?, ?>) watchFilesMapField.get(TlsFileWatcher.getInstance())).isEmpty());
        assertTrue(((Map<?, ?>) fileMd5MapField.get(TlsFileWatcher.getInstance())).isEmpty());
    }
    
    @Test
    void testAddFileChangeListener3() throws IOException, IllegalAccessException {
        TlsFileWatcher.getInstance().addFileChangeListener(filePath -> {
        }, tempFile.getPath());
        
        assertEquals(1, ((Map<?, ?>) watchFilesMapField.get(TlsFileWatcher.getInstance())).size());
        assertEquals(1, ((Map<?, ?>) fileMd5MapField.get(TlsFileWatcher.getInstance())).size());
    }
    
    @Test
    void testStartGivenTlsFileNotChangeThenNoNotify() throws IllegalAccessException, InterruptedException, IOException {
        // given
        AtomicBoolean notified = new AtomicBoolean(false);
        TlsFileWatcher.getInstance().addFileChangeListener(filePath -> notified.set(true), tempFile.getPath());
        
        // when
        TlsFileWatcher.getInstance().start();
        
        // then
        assertFalse(notified.get());
    }
    
    @Test
    void testStartGivenTlsFileChangeThenNotifyTheChangeFilePath() throws IllegalAccessException, IOException {
        // given
        AtomicBoolean notified = new AtomicBoolean(false);
        AtomicReference<String> changedFilePath = new AtomicReference<>();
        TlsFileWatcher.getInstance().addFileChangeListener(filePath -> {
            notified.set(true);
            changedFilePath.set(filePath);
        }, tempFile.getPath());
        ((Map<String, String>) fileMd5MapField.get(TlsFileWatcher.getInstance())).put("test.txt", "");
        
        // when
        TlsFileWatcher.getInstance().start();
        
        // then
        assertTrue(notified.get());
        assertEquals("test.txt", changedFilePath.get());
    }
    
    @Test
    void testStartGivenTaskIsAlreadyRunThenNotRunAgain() {
        TlsFileWatcher.getInstance().start();
        TlsFileWatcher.getInstance().start();
        
        verify(executorService, times(1)).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    }
}