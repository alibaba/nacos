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

package com.alibaba.nacos.naming.consistency.persistent.raft;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;

/**
 * Raft store.
 *
 * @author nacos
 */
@Component
public class RaftStore {
    
    private final Properties meta = new Properties();
    
    private static final String META_FILE_NAME = UtilsAndCommons.DATA_BASE_DIR + File.separator + "meta.properties";
    
    private static final String CACHE_DIR = UtilsAndCommons.DATA_BASE_DIR + File.separator + "data";
    
    private static final String CACHE_FILE_SUFFIX = ".datum";
    
    /**
     * Load datum from cache file.
     *
     * @param notifier raft notifier
     * @param datums   cached datum map
     * @throws Exception any exception during load
     */
    public synchronized void loadDatums(RaftCore.Notifier notifier, ConcurrentMap<String, Datum> datums)
            throws Exception {
        
        Datum datum;
        long start = System.currentTimeMillis();
        for (File cache : listCaches()) {
            if (cache.isDirectory() && cache.listFiles() != null) {
                for (File datumFile : cache.listFiles()) {
                    datum = readDatum(datumFile, cache.getName());
                    if (datum != null) {
                        datums.put(datum.key, datum);
                        notifier.addTask(datum.key, ApplyAction.CHANGE);
                    }
                }
                continue;
            }
            datum = readDatum(cache, StringUtils.EMPTY);
            if (datum != null) {
                datums.put(datum.key, datum);
            }
        }
        
        Loggers.RAFT.info("finish loading all datums, size: {} cost {} ms.", datums.size(),
                (System.currentTimeMillis() - start));
    }
    
    /**
     * Load Metadata from cache file.
     *
     * @return metadata
     * @throws Exception any exception during load
     */
    public synchronized Properties loadMeta() throws Exception {
        File metaFile = new File(META_FILE_NAME);
        if (!metaFile.exists() && !metaFile.getParentFile().mkdirs() && !metaFile.createNewFile()) {
            throw new IllegalStateException("failed to create meta file: " + metaFile.getAbsolutePath());
        }
        
        try (FileInputStream inStream = new FileInputStream(metaFile)) {
            meta.load(inStream);
        }
        return meta;
    }
    
    /**
     * Load datum from cache file by key.
     *
     * @param key datum key
     * @return datum
     * @throws Exception any exception during load
     */
    public synchronized Datum load(String key) throws Exception {
        long start = System.currentTimeMillis();
        // load data
        for (File cache : listCaches()) {
            if (!cache.isFile()) {
                Loggers.RAFT.warn("warning: encountered directory in cache dir: {}", cache.getAbsolutePath());
            }
            
            if (!StringUtils.equals(cache.getName(), encodeDatumKey(key) + CACHE_FILE_SUFFIX)) {
                continue;
            }
            
            Loggers.RAFT.info("finish loading datum, key: {} cost {} ms.", key, (System.currentTimeMillis() - start));
            return readDatum(cache, StringUtils.EMPTY);
        }
        
        return null;
    }
    
    private boolean isDatumCacheFile(String fileName) {
        return fileName.endsWith(CACHE_FILE_SUFFIX);
    }
    
    private synchronized Datum readDatum(File file, String namespaceId) throws IOException {
        if (!isDatumCacheFile(file.getName())) {
            return null;
        }
        ByteBuffer buffer;
        try (FileChannel fc = new FileInputStream(file).getChannel()) {
            buffer = ByteBuffer.allocate((int) file.length());
            fc.read(buffer);
            
            String json = new String(buffer.array(), StandardCharsets.UTF_8);
            if (StringUtils.isBlank(json)) {
                return null;
            }
            
            if (KeyBuilder.matchSwitchKey(file.getName())) {
                return JacksonUtils.toObj(json, new TypeReference<Datum<SwitchDomain>>() {
                });
            }
            
            if (KeyBuilder.matchServiceMetaKey(file.getName())) {
                
                Datum<Service> serviceDatum;
                
                try {
                    serviceDatum = JacksonUtils.toObj(json.replace("\\", ""), new TypeReference<Datum<Service>>() {
                    });
                } catch (Exception e) {
                    JsonNode jsonObject = JacksonUtils.toObj(json);
                    
                    serviceDatum = new Datum<>();
                    serviceDatum.timestamp.set(jsonObject.get("timestamp").asLong());
                    serviceDatum.key = jsonObject.get("key").asText();
                    serviceDatum.value = JacksonUtils.toObj(jsonObject.get("value").toString(), Service.class);
                }
                
                if (StringUtils.isBlank(serviceDatum.value.getGroupName())) {
                    serviceDatum.value.setGroupName(Constants.DEFAULT_GROUP);
                }
                if (!serviceDatum.value.getName().contains(Constants.SERVICE_INFO_SPLITER)) {
                    serviceDatum.value.setName(
                            Constants.DEFAULT_GROUP + Constants.SERVICE_INFO_SPLITER + serviceDatum.value.getName());
                }
                
                return serviceDatum;
            }
            
            if (KeyBuilder.matchInstanceListKey(file.getName())) {
                
                Datum<Instances> instancesDatum;
                
                try {
                    instancesDatum = JacksonUtils.toObj(json, new TypeReference<Datum<Instances>>() {
                    });
                } catch (Exception e) {
                    JsonNode jsonObject = JacksonUtils.toObj(json);
                    instancesDatum = new Datum<>();
                    instancesDatum.timestamp.set(jsonObject.get("timestamp").asLong());
                    
                    String key = jsonObject.get("key").asText();
                    String serviceName = KeyBuilder.getServiceName(key);
                    key = key.substring(0, key.indexOf(serviceName)) + Constants.DEFAULT_GROUP
                            + Constants.SERVICE_INFO_SPLITER + serviceName;
                    
                    instancesDatum.key = key;
                    instancesDatum.value = new Instances();
                    instancesDatum.value.setInstanceList(
                            JacksonUtils.toObj(jsonObject.get("value").toString(), new TypeReference<List<Instance>>() {
                            }));
                    if (!instancesDatum.value.getInstanceList().isEmpty()) {
                        for (Instance instance : instancesDatum.value.getInstanceList()) {
                            instance.setEphemeral(false);
                        }
                    }
                }
                
                return instancesDatum;
            }
            
            return JacksonUtils.toObj(json, Datum.class);
            
        } catch (Exception e) {
            Loggers.RAFT.warn("waning: failed to deserialize key: {}", file.getName());
            throw e;
        }
    }
    
    private String cacheFileName(String namespaceId, Datum datum) {
        String fileName;
        if (StringUtils.isNotBlank(namespaceId)) {
            fileName = CACHE_DIR + File.separator + namespaceId + File.separator + encodeDatumKey(datum.key);
        } else {
            fileName = CACHE_DIR + File.separator + encodeDatumKey(datum.key);
        }
        fileName += CACHE_FILE_SUFFIX;
        return fileName;
    }
    
    /**
     * Write datum to cache file.
     *
     * @param datum datum
     * @throws Exception any exception during writing
     */
    public synchronized void write(final Datum datum) throws Exception {
        
        String namespaceId = KeyBuilder.getNamespace(datum.key);
        
        File cacheFile = new File(cacheFileName(namespaceId, datum));
        
        if (!cacheFile.exists() && !cacheFile.getParentFile().mkdirs() && !cacheFile.createNewFile()) {
            MetricsMonitor.getDiskException().increment();
            
            throw new IllegalStateException("can not make cache file: " + cacheFile.getName());
        }
        
        FileChannel fc = null;
        ByteBuffer data;
        
        data = ByteBuffer.wrap(JacksonUtils.toJson(datum).getBytes(StandardCharsets.UTF_8));
        
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
        
        // remove old format file:
        if (StringUtils.isNoneBlank(namespaceId)) {
            if (datum.key.contains(Constants.DEFAULT_GROUP + Constants.SERVICE_INFO_SPLITER)) {
                String oldFormatKey = datum.key
                        .replace(Constants.DEFAULT_GROUP + Constants.SERVICE_INFO_SPLITER, StringUtils.EMPTY);
                
                cacheFile = new File(cacheFileName(namespaceId, datum));
                if (cacheFile.exists() && !cacheFile.delete()) {
                    Loggers.RAFT.error("[RAFT-DELETE] failed to delete old format datum: {}, value: {}", datum.key,
                            datum.value);
                    throw new IllegalStateException("failed to delete old format datum: " + datum.key);
                }
            }
        }
    }
    
    private File[] listCaches() throws Exception {
        File cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IllegalStateException("cloud not make out directory: " + cacheDir.getName());
        }
        
        return cacheDir.listFiles();
    }
    
    /**
     * Delete datum from cache file.
     *
     * @param datum datum
     */
    public void delete(Datum datum) {
        
        // datum key contains namespace info:
        String namespaceId = KeyBuilder.getNamespace(datum.key);
        
        if (StringUtils.isNotBlank(namespaceId)) {
            
            File cacheFile = new File(cacheFileName(namespaceId, datum));
            if (cacheFile.exists() && !cacheFile.delete()) {
                Loggers.RAFT.error("[RAFT-DELETE] failed to delete datum: {}, value: {}", datum.key, datum.value);
                throw new IllegalStateException("failed to delete datum: " + datum.key);
            }
        }
    }
    
    /**
     * Update term Metadata.
     *
     * @param term term
     * @throws Exception any exception during update
     */
    public void updateTerm(long term) throws Exception {
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
    
    private static String encodeDatumKey(String datumKey) {
        return datumKey.replace(':', '#');
    }
    
    private static String decodeDatumKey(String datumKey) {
        return datumKey.replace("#", ":");
    }
}
