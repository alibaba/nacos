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
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.pojo.Record;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
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

    @Value("${nacos.naming.tree.dataStore.basePath:null}")
    private void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    @PostConstruct
    private void initialize() {
        if (this.getBasePath() == null || StringUtils.isBlank(this.getBasePath())) {
            this.setBasePath(UtilsAndCommons.DATA_BASE_DIR + File.separator + "tree" + File.separator + "data");
        }
    }

    public Datum read(String key, Class<? extends Record> valueType) throws IOException {
        synchronized (this.getLock(key)) {
            FileChannel fileChannel = null;
            try {
                File file = new File(this.getFileName(key));
                fileChannel = new FileInputStream(file).getChannel();
                ByteBuffer buffer = ByteBuffer.allocate((int) file.length());
                fileChannel.read(buffer);

                String json = new String(buffer.array(), StandardCharsets.UTF_8);
                if (StringUtils.isBlank(json)) {
                    return null;
                }
                JSONObject jsonObject = JSON.parseObject(json);
                Datum datum = new Datum();
                datum.timestamp.set(jsonObject.getLongValue("timestamp"));
                datum.key = jsonObject.getString("key");
                datum.value = JSON.parseObject(jsonObject.getString("value"), valueType);
                return datum;
            } catch (IOException exception) {
                MetricsMonitor.getDiskException().increment();
                throw exception;
            } finally {
                if (fileChannel != null) {
                    fileChannel.close();
                }
            }
        }
    }

    public void write(Datum datum) throws IOException {
        synchronized (this.getLock(datum.key)) {
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
    }

    public void remove(String key) {
        synchronized (this.getLock(key)) {
            File file = new File(this.getFileName(key));
            if (file.exists() && !file.delete()) {
                throw new IllegalStateException("failed to delete datum: " + key);
            }
        }
    }

    public String getFileName(String key) {
        String namespaceId = KeyBuilder.getNamespace(key);
        String fileName;
        if (StringUtils.isNotBlank(namespaceId)) {
            fileName = this.getBasePath() + File.separator + namespaceId + File.separator + this.encodeFileName(key);
        } else {
            fileName = this.getBasePath() + File.separator + this.encodeFileName(key);
        }
        return fileName;
    }

    private String getLock(String key) {
        return this.getFileName(key).intern();
    }

    private String encodeFileName(String fileName) {
        return fileName.replace(':', '#');
    }

    private String decodeFileName(String fileName) {
        return fileName.replace("#", ":");
    }


}
