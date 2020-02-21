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
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.SerializeFactory;
import com.alibaba.nacos.common.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.snapshot.CallFinally;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperate;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.consistency.store.AfterHook;
import com.alibaba.nacos.consistency.store.BeforeHook;
import com.alibaba.nacos.consistency.store.KVStore;
import com.alibaba.nacos.consistency.store.StartHook;
import com.alibaba.nacos.core.utils.ZipUtils;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.pojo.Record;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipOutputStream;

/**
 * @author nacos
 * @author lessspring
 */
@Component
@DependsOn("serverNodeManager")
@SuppressWarnings("all")
public class RaftStore {

    private static final TypeReference<Map<String, KVStore.Item>> reference = new TypeReference<Map<String, KVStore.Item>>(){};

    private final String SNAPSHOT_DIR = "nacos-naming";
    private final String SNAPSHOT_ARCHIVE = "nacos-naming.zip";
    private final String SNAPSHOT_FILE = "naming-snapshot.dat";
    private final String cacheDir = UtilsAndCommons.DATA_BASE_DIR + File.separator + "data";
    public static final String STORE_NAME = "persistent_service";

    @Autowired
    private CPProtocol protocol;

    @Autowired
    private RaftConsistencyServiceImpl.Notifier notifier;

    private final Map<String, List<RecordListener>> listMap = new ConcurrentHashMap<>();

    private KVStore<Record> kvStore;

    private Serializer serializer;

    private boolean initialized = false;

    @PostConstruct
    protected void init() throws Exception {

        serializer = SerializeFactory.getDefault();

        kvStore = protocol.createKVStore(STORE_NAME, serializer, new NSnapshotOperate());

        kvStore.registerHook(new NStartHook(), new NBeforeHook(), new NAfterHook());

        kvStore.start();

        initialized = true;
    }

    public Record get(String key) {
        return kvStore.getByKeyAutoConvert(key);
    }

    public void put(String key, Record record) throws Exception {
        kvStore.put(key, record);
    }

    public void remove(String key) throws Exception {
        kvStore.remove(key);
    }

    void listener(String key, RecordListener listener) {
        listMap.computeIfAbsent(key, s -> new CopyOnWriteArrayList<>());
        listMap.get(key).add(listener);
    }

    void unlisten(String key, RecordListener listener) {
        if (listMap.containsKey(key)) {
            listMap.get(key).remove(listener);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    class NSnapshotOperate implements SnapshotOperate {

        @Override
        public void onSnapshotSave(Writer writer, CallFinally callFinally) {
            GlobalExecutor.execute(() -> {

                boolean result = false;
                Throwable throwable = null;

                try {
                    final String writePath = writer.getPath();
                    final String parentPath = Paths.get(writePath, SNAPSHOT_DIR).toString();
                    final File file = new File(parentPath);
                    FileUtils.deleteDirectory(file);
                    FileUtils.forceMkdir(file);

                    byte[] data = serializer.serialize(kvStore.getAll());
                    final String fileName = Paths.get(parentPath, "naming-snapshot.dat").toString();
                    FileUtils.writeByteArrayToFile(new File(fileName), data, false);
                    final String outputFile = Paths.get(writePath, SNAPSHOT_ARCHIVE).toString();

                    try (final FileOutputStream fOut = new FileOutputStream(outputFile);
                         final ZipOutputStream zOut = new ZipOutputStream(fOut)) {
                        WritableByteChannel channel = Channels.newChannel(zOut);
                        ZipUtils.compressDirectoryToZipFile(writePath, SNAPSHOT_DIR, zOut,
                                channel);
                        FileUtils.deleteDirectory(file);
                    }

                    writer.addFile(SNAPSHOT_ARCHIVE);

                    result = true;
                } catch (Exception e) {
                    throwable = e;
                }

                callFinally.run(result, throwable);

            });
        }

        @Override
        public boolean onSnapshotLoad(Reader reader) {
            final String readerPath = reader.getPath();
            final String sourceFile = Paths.get(readerPath, SNAPSHOT_ARCHIVE).toString();
            try {
                ZipUtils.unzipFile(sourceFile, readerPath);
                final String loadPath = Paths.get(readerPath, SNAPSHOT_DIR).toString()
                        + File.separator;
                Loggers.RAFT.info("snapshot load from : {}", loadPath);

                String file = Paths.get(loadPath, SNAPSHOT_FILE).toString();

                byte[] bytes = FileUtils.readFileToByteArray(new File(file));

                kvStore.load(serializer.deSerialize(bytes, Map.class));

                return true;
            }
            catch (final Throwable t) {
                Loggers.RAFT.error("Fail to load snapshot, path={}, file list={}, {}.", readerPath,
                        reader.listFiles(), t);
                return false;
            }
        }
    }

    class NStartHook implements StartHook<Record> {

        @Override
        public void hook(Map<String, KVStore.Item> dataStore, KVStore<Record> kvStore) throws Exception {
            long start = System.currentTimeMillis();

            KVStore.Item item;

            Map<String, KVStore.Item> tmp = new HashMap<>();

            for (File cache : RaftStore.this.listCaches()) {
                if (cache.isDirectory() && cache.listFiles() != null) {
                    for (File itemFile : cache.listFiles()) {
                        item = readItem(itemFile, cache.getName());
                        if (item != null) {
                            tmp.put(decodeFileName(itemFile.getName()), item);
                        }
                    }
                }
            }

            kvStore.load(tmp);

            Loggers.RAFT.info("finish loading all datums, size: {} cost {} ms.", dataStore.size(), (System.currentTimeMillis() - start));
        }
    }

    class NBeforeHook implements BeforeHook<Record> {

        @Override
        public void hook(String key, Record data, KVStore.Item item, boolean isPut) {
            if (isPut) {
                try {
                    write(key, item);
                } catch (Exception e) {
                }
            }
        }
    }

    class NAfterHook implements AfterHook<Record> {

        @Override
        public void hook(String key, Record data, KVStore.Item item, boolean isPut) {
            if (!isPut) {
                delete(key, item);
            }
            notifier.addTask(key, isPut ? ApplyAction.CHANGE : ApplyAction.DELETE);
        }
    }

    KVStore.Item readItem(File file, String namespaceId) throws IOException {

        ByteBuffer buffer;
        FileChannel fc = null;
        try {
            fc = new FileInputStream(file).getChannel();
            buffer = ByteBuffer.allocate((int) file.length());
            fc.read(buffer);

            byte[] bytes = buffer.array();

            return serializer.deSerialize(bytes, KVStore.Item.class);

        } catch (Exception e) {
            Loggers.RAFT.warn("waning: failed to deserialize key: {}", file.getName());
            throw e;
        } finally {
            if (fc != null) {
                fc.close();
            }
        }
    }

    File[] listCaches() throws Exception {
        File cacheDir = new File(this.cacheDir);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IllegalStateException("cloud not make out directory: " + cacheDir.getName());
        }

        return cacheDir.listFiles();
    }

    void delete(String key, KVStore.Item item) {

        // datum key contains namespace info:
        String namespaceId = KeyBuilder.getNamespace(key);

        if (StringUtils.isNotBlank(namespaceId)) {

            File cacheFile = new File(cacheDir + File.separator + namespaceId + File.separator + encodeFileName(key));
            if (cacheFile.exists() && !cacheFile.delete()) {
                Loggers.RAFT.error("[RAFT-DELETE] failed to delete datum: {}, value: {}", key, item);
                throw new IllegalStateException("failed to delete datum: " + key);
            }
        }
    }

    synchronized void write(String key, final KVStore.Item item) throws Exception {

        String namespaceId = KeyBuilder.getNamespace(key);

        File cacheFile;

        if (StringUtils.isNotBlank(namespaceId)) {
            cacheFile = new File(cacheDir + File.separator + namespaceId + File.separator + encodeFileName(key));
        } else {
            cacheFile = new File(cacheDir + File.separator + encodeFileName(key));
        }

        if (!cacheFile.exists() && !cacheFile.getParentFile().mkdirs() && !cacheFile.createNewFile()) {
            MetricsMonitor.getDiskException().increment();

            throw new IllegalStateException("can not make cache file: " + cacheFile.getName());
        }

        FileChannel fc = null;
        ByteBuffer data;

        data = ByteBuffer.wrap(JSON.toJSONString(item).getBytes(StandardCharsets.UTF_8));

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

    public Map<String, List<RecordListener>> getListMap() {
        return listMap;
    }

    private static String encodeFileName(String fileName) {
        return fileName.replace(':', '#');
    }

    private static String decodeFileName(String fileName) {
        return fileName.replace("#", ":");
    }

}
