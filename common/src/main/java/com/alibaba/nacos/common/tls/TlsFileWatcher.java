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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.utils.ClassUtils;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Certificate file update monitoring
 *
 * <p>Considering that the current client needs to support jdk 1.6 and module dependencies ,
 * the WatchFileCenter in the core module is not used
 *
 * @author wangwei
 */
public final class TlsFileWatcher {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TlsFileWatcher.class);
    
    private AtomicBoolean started = new AtomicBoolean(false);
    
    private final int checkInterval = TlsSystemConfig.tlsFileCheckInterval;
    
    private Map<String, String> fileMd5Map = new HashMap<String, String>();
    
    private ConcurrentHashMap<String, FileChangeListener> watchFilesMap = new ConcurrentHashMap<String, FileChangeListener>();
    
    private final ScheduledExecutorService service = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(TlsFileWatcher.class),
                    new NameThreadFactory("com.alibaba.nacos.core.common.tls"));
    
    private static TlsFileWatcher tlsFileWatcher = new TlsFileWatcher();
    
    private TlsFileWatcher() {
        start();
    }
    
    public static TlsFileWatcher getInstance() {
        return tlsFileWatcher;
    }
    
    /**
     * Add file change listener for specified path.
     *
     * @param fileChangeListener listener
     * @param filePaths          file paths
     * @throws IOException If an I/O error occurs
     */
    public void addFileChangeListener(FileChangeListener fileChangeListener, String... filePaths) throws IOException {
        for (String filePath : filePaths) {
            if (filePath != null && new File(filePath).exists()) {
                watchFilesMap.put(filePath, fileChangeListener);
                InputStream in = null;
                try {
                    in = new FileInputStream(filePath);
                    fileMd5Map.put(filePath, MD5Utils.md5Hex(IoUtils.toString(in, Constants.ENCODE), Constants.ENCODE));
                } finally {
                    IoUtils.closeQuietly(in);
                }
            }
        }
    }
    
    /**
     * start file watch task. Notify when the MD5 of file changed
     */
    public void start() {
        if (started.compareAndSet(false, true)) {
            service.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    for (Map.Entry<String, FileChangeListener> item : watchFilesMap.entrySet()) {
                        String filePath = item.getKey();
                        String newHash;
                        InputStream in = null;
                        try {
                            in = new FileInputStream(filePath);
                            newHash = MD5Utils.md5Hex(IoUtils.toString(in, Constants.ENCODE), Constants.ENCODE);
                        } catch (Exception ignored) {
                            LOGGER.warn(" service has exception when calculate the file MD5. " + ignored);
                            continue;
                        } finally {
                            IoUtils.closeQuietly(in);
                        }
                        if (!newHash.equals(fileMd5Map.get(filePath))) {
                            LOGGER.info(filePath + " file hash changed,need reload sslcontext");
                            fileMd5Map.put(filePath, newHash);
                            item.getValue().onChanged(filePath);
                            LOGGER.info(filePath + " onChanged success!");
                        }
                    }
                }
            }, 1, checkInterval, TimeUnit.MINUTES);
        }
    }
    
    public interface FileChangeListener {
        
        /**
         * listener onChanged event.
         *
         * @param filePath Path of changed file
         */
        void onChanged(String filePath);
    }
    
}
