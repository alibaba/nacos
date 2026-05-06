/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractServerStateHandlerTest {
    
    private MockAbstractServerStateHandler serverStateHandler;
    
    private String cachedConfPath;
    
    private String basicPath;
    
    @BeforeEach
    void setUp() {
        basicPath = AbstractServerStateHandlerTest.class.getClassLoader().getResource("nacos-console.properties").getPath();
        basicPath = new File(basicPath).getParentFile().getAbsolutePath();
        basicPath = new File(basicPath, "mock").getAbsolutePath();
        cachedConfPath = EnvUtil.getConfPath();
        serverStateHandler = new MockAbstractServerStateHandler();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setConfPath(cachedConfPath);
    }
    
    @Test
    void getAnnouncementWithTopPath() {
        assertThrows(IllegalArgumentException.class, () -> serverStateHandler.getAnnouncement(".."));
    }
    
    @Test
    void getAnnouncementWithFolderSeparator() {
        assertThrows(IllegalArgumentException.class, () -> serverStateHandler.getAnnouncement("/"));
    }
    
    @Test
    void getAnnouncementWithWindowsFolderSeparator() {
        assertThrows(IllegalArgumentException.class, () -> serverStateHandler.getAnnouncement("\\"));
    }
    
    @Test
    void getAnnouncementFound() {
        String existConfPath = new File(basicPath, "existconf").getAbsolutePath();
        EnvUtil.setConfPath(existConfPath);
        String actual = serverStateHandler.getAnnouncement("zh_CN");
        assertEquals("test announcement content", actual);
    }
    
    @Test
    void getAnnouncementNotFound() {
        String existConfPath = new File(basicPath, "nonexistconf").getAbsolutePath();
        EnvUtil.setConfPath(existConfPath);
        String actual = serverStateHandler.getAnnouncement("zh_CN");
        assertNull(actual);
    }
    
    @Test
    void getConsoleUiGuideFound() {
        String existConfPath = new File(basicPath, "existconf").getAbsolutePath();
        EnvUtil.setConfPath(existConfPath);
        String actual = serverStateHandler.getConsoleUiGuide();
        assertEquals("test guide content", actual);
    }
    
    @Test
    void getConsoleUiGuideNotFound() {
        String existConfPath = new File(basicPath, "nonexistconf").getAbsolutePath();
        EnvUtil.setConfPath(existConfPath);
        String actual = serverStateHandler.getConsoleUiGuide();
        assertNull(actual);
    }
    
    private final class MockAbstractServerStateHandler extends AbstractServerStateHandler {
        
        @Override
        public Map<String, String> getServerState() throws NacosException {
            return null;
        }
    }
}