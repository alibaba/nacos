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

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.snapshot.CallFinally;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.consistency.store.AfterHook;
import com.alibaba.nacos.consistency.store.BeforeHook;
import com.alibaba.nacos.consistency.store.KVStore;
import com.alibaba.nacos.consistency.store.StartHook;
import com.alibaba.nacos.core.utils.ConcurrentHashSet;
import com.alibaba.nacos.core.utils.DiskUtils;
import com.alibaba.nacos.core.utils.TimerContext;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.pojo.Record;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * @author nacos
 * @author lessspring
 */
@Component
@DependsOn("serverMemberManager")
@SuppressWarnings("all")
public class RaftStore {

    public static final String STORE_NAME = "persistent_service";
    private final String SNAPSHOT_DIR = "nacos_naming";

    // example : ../naming/data/ or ../namimh/data/{namespace_id}/
    private final String SNAPSHOT_ARCHIVE = "nacos_naming.zip";
    private final String cacheDir = Paths.get(UtilsAndCommons.DATA_BASE_DIR,"data").toString();
    private final Map<String, Set<RecordListener>> listMap = new ConcurrentHashMap<>();
    @Autowired
    private CPProtocol protocol;
    @Autowired
    private RaftConsistencyServiceImpl.Notifier notifier;
    private KVStore<Record> kvStore;

    private Serializer serializer;

    private boolean initialized = false;

    private static String encodeFileName(String fileName) {
        return fileName.replace(':', '#');
    }

    private static String decodeFileName(String fileName) {
        return fileName.replace("#", ":");
    }

    @PostConstruct
    protected void init() throws Exception {
        serializer = SerializeFactory.getDefault();
        kvStore = protocol.createKVStore(STORE_NAME, serializer, new NSnapshotOperation());
        kvStore.registerHook(new StartHook() {
            @Override
            public void hook(Map dataStore, KVStore kvStore) throws Exception {

                // Delete existing data, relying on raft's snapshot and log
                // playback to reply to the data is the correct behavior.

                DiskUtils.deleteDirectory(cacheDir);
                DiskUtils.forceMkdir(cacheDir);

            }
        }, new NBeforeHook(), new NAfterHook());
        kvStore.start();
        initialized = true;
    }

    public Record get(String key) throws NacosException {
        Record record = kvStore.getByKeyAutoConvert(key);
        if (record == null) {
            try {
                loadByKey(key);
                record = kvStore.getByKeyAutoConvert(key);
            } catch (Exception e) {
                throw new NacosException(NacosException.SERVER_ERROR, "Failed to load data from file :" + key);
            }
        }
        return record;
    }

    public void put(String key, Record record) throws Exception {
        kvStore.put(key, record);
    }

    public void remove(String key) throws Exception {
        kvStore.remove(key);
        listMap.remove(key);
    }

    void listener(String key, RecordListener listener) {
        listMap.computeIfAbsent(key, s -> new ConcurrentHashSet<>());
        Set<RecordListener> set = listMap.get(key);
        if (!set.contains(listener)) {
            set.add(listener);
        }
    }

    void unlisten(String key, RecordListener listener) {
        if (listMap.containsKey(key)) {
            listMap.get(key).remove(listener);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    KVStore.Item readItem(File file, String namespaceId) throws IOException {
        try {
            byte[] bytes = DiskUtils.readFileBytes(file);
            return serializer.deSerialize(bytes, KVStore.Item.class);
        } catch (Exception e) {
            Loggers.RAFT.warn("waning: failed to deserialize key: {}", file.getName());
            throw e;
        }
    }

    File[] listCaches() throws Exception {
        File cacheDir = new File(this.cacheDir);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            MetricsMonitor.getDiskException().increment();
            throw new IllegalStateException("can not make out directory: " + cacheDir.getName());
        }

        return cacheDir.listFiles();
    }

    void delete(String key, KVStore.Item item) {

        // datum key contains namespace info:
        String namespaceId = KeyBuilder.getNamespace(key);

        if (StringUtils.isNotBlank(namespaceId)) {

            File cacheFile = new File(cacheDir + File.separator + namespaceId +
                    File.separator + encodeFileName(key));
            if (cacheFile.exists() && !cacheFile.delete()) {
                Loggers.RAFT.error("[RAFT-DELETE] failed to delete datum: {}, value: {}", key, item);
                throw new IllegalStateException("failed to delete datum: " + key);
            }
        }
    }

    void loadByKey(String key) throws Exception {
        String namespaceId = KeyBuilder.getNamespace(key);

        File cacheFile;

        if (StringUtils.isNotBlank(namespaceId)) {
            cacheFile = new File(cacheDir + File.separator +
                    namespaceId + File.separator + encodeFileName(key));
        } else {
            cacheFile = new File(cacheDir + File.separator + encodeFileName(key));
        }

        byte[] data = DiskUtils.readFileBytes(cacheFile);
        KVStore.Item item = JSON.parseObject(data, KVStore.Item.class);
        Record record = serializer.deSerialize(item.getBytes(), item.getClassName());
        kvStore.put(key, record);
    }

    synchronized void write(String key, final KVStore.Item item) throws Exception {

        String namespaceId = KeyBuilder.getNamespace(key);

        File cacheFile;

        if (StringUtils.isNotBlank(namespaceId)) {
            cacheFile = new File(cacheDir + File.separator +
                    namespaceId + File.separator + encodeFileName(key));
        } else {
            cacheFile = new File(cacheDir + File.separator + encodeFileName(key));
        }

        if (!cacheFile.exists() && !cacheFile.getParentFile().mkdirs() && !cacheFile.createNewFile()) {
            MetricsMonitor.getDiskException().increment();

            throw new IllegalStateException("can not make cache file: " + cacheFile.getName());
        }

        byte[] data = JSON.toJSONString(item).getBytes(StandardCharsets.UTF_8);

        try {
            DiskUtils.writeFile(cacheFile, data, false);
        } catch (Exception e) {
            MetricsMonitor.getDiskException().increment();
            throw e;
        }

        // remove old format file:
        if (StringUtils.isNoneBlank(namespaceId)) {
            if (key.contains(Constants.DEFAULT_GROUP + Constants.SERVICE_INFO_SPLITER)) {
                String oldFormatKey =
                        key.replace(Constants.DEFAULT_GROUP + Constants.SERVICE_INFO_SPLITER, StringUtils.EMPTY);

                cacheFile = new File(cacheDir + File.separator + namespaceId + File.separator + encodeFileName(oldFormatKey));
                if (cacheFile.exists() && !cacheFile.delete()) {
                    Loggers.RAFT.error("[RAFT-DELETE] failed to delete old format datum: {}, value: {}",
                            key, item);
                    throw new IllegalStateException("failed to delete old format datum: " + key);
                }
            }
        }
    }

    public Map<String, Set<RecordListener>> getListMap() {
        return listMap;
    }

    private void loadFromFile(File parent) throws IOException {

        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("cloud not make out directory: " + parent.getName());
        }

        KVStore.Item item;

        Map<String, KVStore.Item> tmp = new HashMap<>();

        for (File cache : parent.listFiles()) {
            if (cache.isDirectory() && cache.listFiles() != null) {
                for (File itemFile : cache.listFiles()) {
                    item = readItem(itemFile, itemFile.getName());
                    if (item != null) {
                        tmp.put(decodeFileName(itemFile.getName()), item);
                    }
                }
            } else {
                item = readItem(cache, cache.getName());
                if (item != null) {
                    tmp.put(decodeFileName(cache.getName()), item);
                }
            }
        }

        kvStore.load(tmp);
    }

    class NSnapshotOperation implements SnapshotOperation {

        @Override
        public void onSnapshotSave(Writer writer, CallFinally callFinally) {
            GlobalExecutor.execute(() -> {

                boolean result = false;
                Throwable throwable = null;
                TimerContext.start("[Naming] RaftStore snapshot save job");
                try {
                    final String writePath = writer.getPath();
                    final String parentPath = Paths.get(writePath, SNAPSHOT_DIR).toString();
                    final File file = new File(parentPath);
                    DiskUtils.deleteDirectory(parentPath);
                    DiskUtils.forceMkdir(parentPath);

                    DiskUtils.copyDirectory(new File(cacheDir), new File(parentPath));

                    final String outputFile = Paths.get(writePath, SNAPSHOT_ARCHIVE).toString();
                    DiskUtils.compress(writePath, SNAPSHOT_DIR, outputFile, new CRC32());
                    DiskUtils.deleteDirectory(parentPath);
                    writer.addFile(SNAPSHOT_ARCHIVE);
                    result = true;
                } catch (Exception e) {
                    throwable = e;
                    Loggers.RAFT.error("An error occurred while saving the snapshot : {}", throwable);
                } finally {
                    callFinally.run(result, throwable);
                    TimerContext.end(Loggers.RAFT);
                }
            });
        }

        @Override
        public boolean onSnapshotLoad(Reader reader) {
            final String readerPath = reader.getPath();
            final String sourceFile = Paths.get(readerPath, SNAPSHOT_ARCHIVE).toString();
            TimerContext.start("[Naming] RaftStore snapshot load job");
            try {
                DiskUtils.decompress(sourceFile, readerPath, new CRC32());
                final String loadPath = Paths.get(readerPath, SNAPSHOT_DIR).toString();
                Loggers.RAFT.info("snapshot load from : {}", loadPath);
                File loadDir = new File(loadPath);
                loadFromFile(loadDir);
                return true;
            } catch (final Throwable t) {
                Loggers.RAFT.error("Fail to load snapshot, path={}, file list={}, {}.", readerPath,
                        reader.listFiles(), t);
                return false;
            } finally {
                TimerContext.end(Loggers.RAFT);
            }
        }
    }

    class NBeforeHook implements BeforeHook<Record> {

        @Override
        public void hook(String key, Record data, KVStore.Item item, boolean isPut) {
            if (isPut) {
                try {

                    // We need to make sure the data drops before we can proceed

                    write(key, item);
                } catch (Exception e) {
                    Loggers.RAFT.error("Data persistence error : {}", e);
                }
            }
        }
    }

    // This method is limited to data read operations when the snapshot is loaded

    class NAfterHook implements AfterHook<Record> {

        @Override
        public void hook(String key, Record data, KVStore.Item item, boolean isPut) {
            if (!isPut) {
                delete(key, item);
            }
            notifier.addTask(key, isPut ? ApplyAction.CHANGE : ApplyAction.DELETE);
        }
    }

}
