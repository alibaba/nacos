/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * Datum store for tree protocol.
 *
 * @author lostcharlie
 */
@Component
public class TreeDataStore {
    private String basePath;

    public String getBasePath() {
        return basePath;
    }

    @Value("${nacos.naming.tree.dataStore.basePath}")
    private void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public Datum read(String key) {
        return null;
    }

    public void write(Datum datum) throws IOException {
        File file = new File(this.getFileName(datum.key));
        if (!file.exists() && !file.getParentFile().mkdirs() && !file.createNewFile()) {
            MetricsMonitor.getDiskException().increment();
            throw new IllegalStateException("can not make file: " + file.getName());
        }

        FileChannel fileChannel = null;
        try {
            ByteBuffer data = ByteBuffer.wrap(JSON.toJSONString(datum).getBytes(StandardCharsets.UTF_8));
            fileChannel = new FileOutputStream(file, false).getChannel();
            fileChannel.write(data, data.position());
            fileChannel.force(true);
        } catch (IOException exception) {
            MetricsMonitor.getDiskException().increment();
            throw exception;
        } finally {
            if (fileChannel != null) {
                fileChannel.close();
            }
        }
    }

    public void remove(String key) {
        File file = new File(this.getFileName(key));
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("failed to delete datum: " + key);
        }
    }

    public String getFileName(String key) {
        String namespaceId = KeyBuilder.getNamespace(key);
        String fileName;
        if (StringUtils.isNotBlank(namespaceId)) {
            fileName = this.getBasePath() + File.separator + namespaceId + File.separator + encodeFileName(key);
        } else {
            fileName = this.getBasePath() + File.separator + encodeFileName(key);
        }
        return fileName;
    }

    private static String encodeFileName(String fileName) {
        return fileName.replace(':', '#');
    }

    private static String decodeFileName(String fileName) {
        return fileName.replace("#", ":");
    }
}
