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
import com.alibaba.nacos.api.common.Constants;
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

import static com.alibaba.nacos.core.utils.SystemUtils.NACOS_HOME;
import static com.alibaba.nacos.core.utils.SystemUtils.NACOS_HOME_KEY;

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
                    readDatum(datumFile, cache.getName());
                }
                continue;
            }
            readDatum(cache, StringUtils.EMPTY);
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
            readDatum(cache, StringUtils.EMPTY);
        }

        Loggers.RAFT.info("finish loading datum, key: {} cost {} ms.",
            key, (System.currentTimeMillis() - start));
    }

    public synchronized static void readDatum(File file, String namespaceId) throws IOException {

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

            if (StringUtils.isBlank(namespaceId)) {
                namespaceId = Constants.REQUEST_PARAM_DEFAULT_NAMESPACE_ID;
            }

            if (!datum.key.contains(UtilsAndCommons.SWITCH_DOMAIN_NAME) && !datum.key.contains(namespaceId)) {

                if (datum.key.startsWith(UtilsAndCommons.DOMAINS_DATA_ID_PRE)) {
                    datum.key = UtilsAndCommons.DOMAINS_DATA_ID_PRE + namespaceId +
                        UtilsAndCommons.SERVICE_GROUP_CONNECTOR +
                        datum.key.substring(UtilsAndCommons.DOMAINS_DATA_ID_PRE.length());
                }

                if (datum.key.startsWith(UtilsAndCommons.IPADDRESS_DATA_ID_PRE)) {
                    datum.key = UtilsAndCommons.IPADDRESS_DATA_ID_PRE + namespaceId +
                        UtilsAndCommons.SERVICE_GROUP_CONNECTOR +
                        datum.key.substring(UtilsAndCommons.IPADDRESS_DATA_ID_PRE.length());
                }
            }

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

        if (datum.key.contains(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)) {
            String[] segments = datum.key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[0].split("\\.");
            namespaceId = segments[segments.length - 1];
        }

        File cacheFile;
        File oldCacheFile = null;

        if (StringUtils.isNotBlank(namespaceId)) {
            cacheFile = new File(CACHE_DIR + File.separator + namespaceId + File.separator + encodeFileName(datum.key));
        } else {
            cacheFile = new File(CACHE_DIR + File.separator + encodeFileName(datum.key));
        }

        if (Constants.REQUEST_PARAM_DEFAULT_NAMESPACE_ID.equals(namespaceId)) {
            // remove old format file:
            String originDatumKey = null;
            if (datum.key.startsWith(UtilsAndCommons.DOMAINS_DATA_ID_PRE)) {
                originDatumKey = UtilsAndCommons.DOMAINS_DATA_ID_PRE +
                    datum.key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[1];
            } else if (datum.key.startsWith(UtilsAndCommons.IPADDRESS_DATA_ID_PRE)) {
                originDatumKey = UtilsAndCommons.IPADDRESS_DATA_ID_PRE +
                    datum.key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[1];
            }

            oldCacheFile = new File(CACHE_DIR + File.separator + encodeFileName(originDatumKey));
            if (oldCacheFile.exists() && !oldCacheFile.delete()) {
                throw new IllegalStateException("remove old format file failed, key:" + originDatumKey);
            }
        }

        if (!cacheFile.exists() && !cacheFile.getParentFile().mkdirs() && !cacheFile.createNewFile()) {
            MetricsMonitor.getDiskException().increment();

            throw new IllegalStateException("can not make cache file: " + cacheFile.getName());
        }

        FileChannel fc = null;
        ByteBuffer data = ByteBuffer.wrap(JSON.toJSONString(datum).getBytes("UTF-8"));

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

        if (datum.key.contains(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)) {
            // datum key contains namespace info:
            String namspaceId = null;
            String originDatumKey = null;
            if (datum.key.startsWith(UtilsAndCommons.DOMAINS_DATA_ID_PRE)) {
                namspaceId = datum.key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[0]
                    .substring(UtilsAndCommons.DOMAINS_DATA_ID_PRE.length());
                originDatumKey = UtilsAndCommons.DOMAINS_DATA_ID_PRE +
                    datum.key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[1];
            } else if (datum.key.startsWith(UtilsAndCommons.IPADDRESS_DATA_ID_PRE)) {
                namspaceId = datum.key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[0]
                    .substring(UtilsAndCommons.IPADDRESS_DATA_ID_PRE.length());
                originDatumKey = UtilsAndCommons.IPADDRESS_DATA_ID_PRE +
                    datum.key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[1];
            }

            if (StringUtils.isNotBlank(namspaceId)) {

                if (namspaceId.equals(Constants.REQUEST_PARAM_DEFAULT_NAMESPACE_ID)) {

                    File cacheFile = new File(CACHE_DIR + File.separator + encodeFileName(originDatumKey));
                    if (cacheFile.exists() && !cacheFile.delete()) {
                        Loggers.RAFT.error("[RAFT-DELETE] failed to delete datum: {}, value: {}", datum.key, datum.value);
                        throw new IllegalStateException("failed to delete datum: " + datum.key);
                    }
                }

                File cacheFile = new File(CACHE_DIR + File.separator + namspaceId + File.separator + encodeFileName(datum.key));
                if (cacheFile.exists() && !cacheFile.delete()) {
                    Loggers.RAFT.error("[RAFT-DELETE] failed to delete datum: {}, value: {}", datum.key, datum.value);
                    throw new IllegalStateException("failed to delete datum: " + datum.key);
                }
            }
        } else {
            File cacheFile = new File(CACHE_DIR + File.separator + encodeFileName(datum.key));
            if (cacheFile.exists() && !cacheFile.delete()) {
                Loggers.RAFT.error("[RAFT-DELETE] failed to delete datum: {}, value: {}", datum.key, datum.value);
                throw new IllegalStateException("failed to delete datum: " + datum.key);
            }
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
