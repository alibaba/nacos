/*
 * Copyright (c) 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.upgrade;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Persist upgrade states to disk.
 *
 * @author gengtuo.ygt
 * on 2021/5/18
 */
@Component
public class UpgradeStates extends Subscriber<UpgradeStates.UpgradeStateChangedEvent> {
    
    private static final String FILE_NAME = "upgrade.state";
    
    private static final String UPGRADED_KEY = "upgraded";
    
    public static final Path UPGRADE_STATE_FILE =
            Paths.get(EnvUtil.getNacosHome() + File.separator + "data" + File.separator + FILE_NAME);
    
    private final Properties properties = new Properties();
    
    @PostConstruct
    private void init() throws IOException {
        if (Files.isDirectory(UPGRADE_STATE_FILE)) {
            throw new IOException(UPGRADE_STATE_FILE + " is a directory");
        }
        try {
            Files.createDirectories(UPGRADE_STATE_FILE.getParent().toAbsolutePath());
        } catch (FileAlreadyExistsException ignored) {
        }
        readFromDisk();
        NotifyCenter.registerSubscriber(this);
    }
    
    @PreDestroy
    private void destroy() throws IOException {
        writeToDisk();
    }
    
    private void readFromDisk() {
        try {
            if (Files.notExists(UPGRADE_STATE_FILE)) {
                Loggers.SRV_LOG.info("{} file is not exist", FILE_NAME);
                return;
            }
            if (Files.isRegularFile(UPGRADE_STATE_FILE)) {
                try (InputStream is = Files.newInputStream(UPGRADE_STATE_FILE)) {
                    properties.load(is);
                }
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.error("Failed to load file " + UPGRADE_STATE_FILE, e);
            throw new IllegalStateException(e);
        }
    }
    
    private void writeToDisk() throws IOException {
        try (OutputStream os = Files.newOutputStream(UPGRADE_STATE_FILE,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            properties.store(os, null);
        }
    }
    
    /**
     * Cluster has been upgraded at recent process.
     *
     * @return Has been upgraded
     */
    public Boolean isUpgraded() {
        String value = properties.getProperty(UPGRADED_KEY);
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }
    
    @Override
    public void onEvent(UpgradeStateChangedEvent event) {
        properties.setProperty(UPGRADED_KEY, String.valueOf(event.isUpgraded));
        try {
            writeToDisk();
        } catch (IOException e) {
            Loggers.EVT_LOG.error("Failed to write " + FILE_NAME + " to disk", e);
        }
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return UpgradeStateChangedEvent.class;
    }
    
    public static class UpgradeStateChangedEvent extends Event {
        
        private final boolean isUpgraded;
        
        public UpgradeStateChangedEvent(boolean isUpgraded) {
            this.isUpgraded = isUpgraded;
        }
        
        public boolean isUpgraded() {
            return isUpgraded;
        }
        
    }
    
}
