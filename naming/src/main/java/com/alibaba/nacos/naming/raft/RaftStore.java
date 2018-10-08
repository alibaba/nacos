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

import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Properties;

/**
 * @author nacos
 */
public class RaftStore {

    private static String BASE_DIR = System.getProperty("user.home") + File.separator + "nacos" + File.separator + "raft";

    private static String META_FILE_NAME;

    private static String CACHE_DIR;

    private static Properties meta = new Properties();

    static {

        String nacosHome = System.getProperty("nacos.home");
        if (StringUtils.isNotBlank(nacosHome)) {
            BASE_DIR = nacosHome + File.separator + "data" + File.separator + "naming";
        }

        META_FILE_NAME = BASE_DIR + File.separator + "meta.properties";
        CACHE_DIR = BASE_DIR + File.separator + "data";
    }

    public synchronized static void load() throws Exception{
        long start = System.currentTimeMillis();
        // load data
        for (File cache : listCaches()) {
            if (!cache.isFile()) {
                Loggers.RAFT.warn("warning: encountered directory in cache dir: " + cache.getAbsolutePath());
            }

            try (FileChannel fc = new FileInputStream(cache).getChannel()) {
                ByteBuffer buffer = ByteBuffer.allocate((int) cache.length());
                fc.read(buffer);

                String json = new String(buffer.array(), "UTF-8");
                if (StringUtils.isBlank(json)) {
                    continue;
                }

                Datum datum = JSON.parseObject(json, Datum.class);
                RaftCore.addDatum(datum);
            } catch (Exception e) {
                Loggers.RAFT.warn("waning: failed to deserialize key: " + cache.getName());
                throw e;
            }
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

        Loggers.RAFT.info("finish loading all datums, size: " + RaftCore.datumSize() + " cost " + (System.currentTimeMillis() - start) + "ms.");
    }

    public synchronized static void load(String key) throws Exception{
        long start = System.currentTimeMillis();
        // load data
        for (File cache : listCaches()) {
            if (!cache.isFile()) {
                Loggers.RAFT.warn("warning: encountered directory in cache dir: " + cache.getAbsolutePath());
            }

            if (!StringUtils.equals(cache.getName(), key)) {
                continue;
            }

            try (FileChannel fc = new FileInputStream(cache).getChannel()) {
                ByteBuffer buffer = ByteBuffer.allocate((int) cache.length());
                fc.read(buffer);

                String json = new String(buffer.array(), "UTF-8");
                if (StringUtils.isBlank(json)) {
                    continue;
                }

                Datum datum = JSON.parseObject(json, Datum.class);
                RaftCore.addDatum(datum);
            } catch (Exception e) {
                Loggers.RAFT.warn("waning: failed to deserialize key: " + cache.getName());
                throw e;
            }
        }

        Loggers.RAFT.info("finish loading datum, key: " + key +  " cost " + (System.currentTimeMillis() - start) + "ms.");
    }

    public synchronized static void write(final Datum datum) throws Exception {
        File cacheFile = new File(CACHE_DIR + File.separator + datum.key);
        if (!cacheFile.exists() && !cacheFile.getParentFile().mkdirs() && !cacheFile.createNewFile()) {
            throw new IllegalStateException("can not make cache file: " + cacheFile.getName());
        }

        ByteBuffer data = ByteBuffer.wrap(JSON.toJSONString(datum).getBytes("UTF-8"));

        try (FileChannel fc = new FileOutputStream(cacheFile, false).getChannel()) {
            fc.write(data, data.position());
            fc.force(true);
        } catch (Exception e) {
            Loggers.RAFT.warn("waning: failed to write datum: " + datum);
            throw e;
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
        File cacheFile = new File(CACHE_DIR + File.separator + datum.key);
        if (!cacheFile.delete()) {
            Loggers.RAFT.error("RAFT-DELETE", "failed to delete datum: " + datum.key + ", value: " + datum.value);
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
}
