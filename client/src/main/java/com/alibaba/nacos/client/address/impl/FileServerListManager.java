/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.address.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.base.AbstractServerListManager;
import com.alibaba.nacos.client.address.base.Order;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Order(200)
public class FileServerListManager extends AbstractServerListManager {

    private static final String NAME_PREFIX = "file";

    private static final Logger LOGGER = LoggerFactory.getLogger(FileServerListManager.class);

    private String serverFile;

    private long lastModifiedTime;

    private final ScheduledExecutorService scheduledExecutorService;

    public FileServerListManager(NacosClientProperties properties) throws NacosException {
        super(properties);
        this.scheduledExecutorService = new ScheduledThreadPoolExecutor(1, new NameThreadFactory(this.getClass().getName()));
        initServerList();
    }

    @Override
    protected String initServerName(NacosClientProperties properties) {
        this.serverFile = initServerFile(properties);
        //windows prefix
        String serverFile = this.serverFile.replace(":", "");
        if (serverFile.startsWith("/")) {
            //linux prefix
            serverFile = serverFile.substring(1);
        }
        return NAME_PREFIX + "-" + serverFile.replaceAll("[/\\\\]", "_");
    }

    @Override
    public void shutdown() throws NacosException {
        ThreadUtils.shutdownThreadPool(scheduledExecutorService, LOGGER);
    }

    private String initServerFile(NacosClientProperties properties) {
        String serverFile = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_SERVER_FILE),
                () -> properties.getProperty(PropertyKeyConst.SERVER_FILE));
        return StringUtils.isNotBlank(serverFile) ? serverFile : "";
    }

    private void initServerList() {
        if (StringUtils.isNotBlank(serverFile)) {
            LOGGER.info("init server from local file: {}", serverFile);
            Runnable task = createUpdateServerListTask(this::readServerList);
            task.run();
            if (!getServerList().isEmpty()) {
                scheduledExecutorService.scheduleWithFixedDelay(task, 0L, 30L, TimeUnit.SECONDS);
            }
        }
    }

    private List<String> readServerList() {
        try {
            long lastModifiedTime = getLastModifiedTime(serverFile);
            if (lastModifiedTime > this.lastModifiedTime) {
                this.lastModifiedTime = lastModifiedTime;
                try (FileReader reader = new FileReader(serverFile)) {
                    return readServerList(reader);
                }
            }
        } catch (Exception e) {
            LOGGER.error("read local server list error, server file path: {}", serverFile, e);
        }
        return null;
    }

    private long getLastModifiedTime(String serverFile) throws IOException {
        BasicFileAttributes basicFileAttributes = Files.readAttributes(Paths.get(serverFile), BasicFileAttributes.class);
        return basicFileAttributes.lastModifiedTime().toMillis();
    }
}
