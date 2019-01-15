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
package com.alibaba.nacos.naming.raft;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Properties;

import static com.alibaba.nacos.common.util.SystemUtils.NACOS_HOME;
import static com.alibaba.nacos.common.util.SystemUtils.NACOS_HOME_KEY;

/**
 * @author nacos
 */
public class RaftStore {

    private static String BASE_DIR = NACOS_HOME + File.separator + "raft";

    private static String META_FILE_NAME;

    private static String CACHE_DIR;

    private static Properties meta = new Properties();

    static {

        if (StringUtils.isNotBlank(System.getProperty(NACOS_HOME_KEY))) {
            BASE_DIR = NACOS_HOME + File.separator + "data" + File.separator + "naming";
        }

        META_FILE_NAME = BASE_DIR + File.separator + "meta.properties";
        CACHE_DIR = BASE_DIR + File.separator + "data";
    }

    public synchronized static void load() throws Exception {
        long start = System.currentTimeMillis();
        for (File cache : listCaches()) {
            if (cache.isDirectory() && cache.listFiles() != null) {
                for (File datumFile : cache.listFiles()) {
                    readDatum(datumFile);
                }
                continue;
            }
            readDatum(cache);
        }

        // load meta
        File meta = new File(META_FILE_NAME);
        if (!meta.exists() && !meta.getParentFile().mkdirs() && !meta.createNewFile()) {
            throw new IllegalStateException("failed to create meta file: " + meta.getAbsolutePath());
        }

        try (FileInputStream inStream = new FileInputStream(meta)) {
            RaftStore.meta.load(inStream);
            RaftCore.setTerm(NumberUtils.toLong(RaftStore.meta.getProperty("term"), 0L));
        }

        Loggers.RAFT.info("finish loading all datums, size: {} cost {} ms.", RaftCore.datumSize(), (System.currentTimeMillis() - start));
    }

    public synchronized static void load(String key) throws Exception {
        long start = System.currentTimeMillis();
        // load data
        for (File cache : listCaches()) {
            if (!cache.isFile()) {
                Loggers.RAFT.warn("warning: encountered directory in cache dir: {}", cache.getAbsolutePath());
            }

            if (!StringUtils.equals(decodeFileName(cache.getName()), key)) {
                continue;
            }
            readDatum(cache);
        }

        Loggers.RAFT.info("finish loading datum, key: {} cost {} ms.",
            key, (System.currentTimeMillis() - start));
    }

    public synchronized static void readDatum(File file) throws IOException {

        ByteBuffer buffer;
        FileChannel fc = null;
        try {
            fc = new FileInputStream(file).getChannel();
            buffer = ByteBuffer.allocate((int) file.length());
            fc.read(buffer);

            String json = new String(buffer.array(), "UTF-8");
            if (StringUtils.isBlank(json)) {
                return;
            }

            Datum datum = JSON.parseObject(json, Datum.class);
            RaftCore.addDatum(datum);
        } catch (Exception e) {
            Loggers.RAFT.warn("waning: failed to deserialize key: {}", file.getName());
            throw e;
        } finally {
            if (fc != null) {
                fc.close();
            }
        }
    }

    public synchronized static void write(final Datum datum) throws Exception {

        String namespaceId = null;

        Datum writeDatum = new Datum();
        writeDatum.key = datum.key;
        writeDatum.value = datum.value;
        writeDatum.timestamp.set(datum.timestamp.get());

        if (writeDatum.key.contains(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)) {
            String[] segments = writeDatum.key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[0].split("\\.");
            namespaceId = segments[segments.length - 1];
            String newKey;
            segments = writeDatum.key.split(namespaceId + UtilsAndCommons.SERVICE_GROUP_CONNECTOR);
            newKey = segments[0] + segments[1];
            writeDatum.key = newKey;
        }

        File cacheFile;
        File oldCacheFile = null;

        if (StringUtils.isNotBlank(namespaceId)) {
            cacheFile = new File(CACHE_DIR + File.separator + namespaceId + File.separator + encodeFileName(writeDatum.key));
        } else {
            oldCacheFile = new File(CACHE_DIR + File.separator + encodeFileName(writeDatum.key));
            cacheFile = new File(CACHE_DIR + File.separator + UtilsAndCommons.getDefaultNamespaceId() + File.separator + encodeFileName(writeDatum.key));
        }

        if (!cacheFile.exists() && !cacheFile.getParentFile().mkdirs() && !cacheFile.createNewFile()) {
            MetricsMonitor.getDiskException().increment();

            throw new IllegalStateException("can not make cache file: " + cacheFile.getName());
        }

        FileChannel fc = null;
        ByteBuffer data = ByteBuffer.wrap(JSON.toJSONString(writeDatum).getBytes("UTF-8"));

        try {
            fc = new FileOutputStream(cacheFile, false).getChannel();
            fc.write(data, data.position());
            fc.force(true);
        } catch (Exception e) {
            MetricsMonitor.getDiskException().increment();
            throw e;
        } finally {
            if (fc != null) {
                fc.close();
            }
        }

        if (oldCacheFile != null) {
            oldCacheFile.delete();
        }

    }

    private static File[] listCaches() throws Exception {
        File cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IllegalStateException("cloud not make out directory: " + cacheDir.getName());
        }

        return cacheDir.listFiles();
    }

    public static void delete(Datum datum) {
        File cacheFile = new File(CACHE_DIR + File.separator + encodeFileName(datum.key));
        if (!cacheFile.delete()) {
            Loggers.RAFT.error("[RAFT-DELETE] failed to delete datum: {}, value: {}", datum.key, datum.value);
            throw new IllegalStateException("failed to delete datum: " + datum.key);
        }
    }

    public static void updateTerm(long term) throws Exception {
        File file = new File(META_FILE_NAME);
        if (!file.exists() && !file.getParentFile().mkdirs() && !file.createNewFile()) {
            throw new IllegalStateException("failed to create meta file");
        }

        try (FileOutputStream outStream = new FileOutputStream(file)) {
            // write meta
            meta.setProperty("term", String.valueOf(term));
            meta.store(outStream, null);
        }
    }

    private static String encodeFileName(String fileName) {
        return fileName.replace(':', '#');
    }

    private static String decodeFileName(String fileName) {
        return fileName.replace("#", ":");
    }
}
