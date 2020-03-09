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
package com.alibaba.nacos.core.distributed.id;

import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.Constants;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import com.alibaba.nacos.consistency.snapshot.CallFinally;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.utils.ByteUtils;
import com.alibaba.nacos.core.utils.ConvertUtils;
import com.alibaba.nacos.core.utils.DiskUtils;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.SystemUtils;
import com.alibaba.nacos.core.utils.TimerContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simple implementation of LeafID based on Raft+File
 *
 * @author <a href="mailto:liaochunyhm@live.com">liaochuntao</a>
 */
@ConditionalOnProperty(value = "nacos.core.idGenerator.type", havingValue = "default", matchIfMissing = true)
@Component
public class DefaultIdStore implements LogProcessor4CP {

    private static final File[] EMPTY = new File[0];
    private static final String SNAPSHOT_DIR = "id_generator";
    private static final String SNAPSHOT_ARCHIVE = "id_generator.zip";
    private static final String FILE_PATH = Paths.get(SystemUtils.NACOS_HOME, "data", "id_generator").toString();
    private static long ACQUIRE_STEP;
    private ConsistencyProtocol<? extends Config> protocol;
    private Map<String, IdStoreFile> storeFileMap;
    private Serializer serializer;

    private volatile boolean hasLeader = false;

    @PostConstruct
    protected void init() throws Exception {
        Loggers.ID_GENERATOR.info("The Leaf-ID start");
        this.storeFileMap = new ConcurrentHashMap<>(4);
        this.serializer = SerializeFactory.getDefault();
        ACQUIRE_STEP =
                ConvertUtils.toLong(SpringUtils.getProperty("nacos.core.idGenerator.default.acquire.step"), 1000);

        // Delete existing data, relying on raft's snapshot and log
        // playback to reply to the data is the correct behavior.

        DiskUtils.deleteDirectory(FILE_PATH);
        DiskUtils.forceMkdir(FILE_PATH);
    }

    public boolean isHasLeader() {
        return hasLeader;
    }

    public void acquireNewIdSequence(String resource, int maxRetryCnt, DefaultIdGenerator generator) {
        storeFileMap.computeIfAbsent(resource, s -> new IdStoreFile(resource));
        for (int i = 0; i < maxRetryCnt; i++) {
            // need read maxId from raft-leader
            long currentMaxId = (long) getData(GetRequest.builder()
                    .biz(bizInfo())
                    .ctx(ByteUtils.toBytes(resource))
                    .build()).getData();
            final long minId = currentMaxId + 1;
            final long maxId = minId + ACQUIRE_STEP;
            final AcquireId acquireId = AcquireId.builder()
                    .minId(minId)
                    .maxId(maxId)
                    .applicant(resource)
                    .build();
            try {
                if (commitAutoSetBiz(NLog.builder()
                        .data(serializer.serialize(acquireId))
                        .build())) {
                    generator.update(new long[]{minId, maxId});
                    Loggers.ID_GENERATOR.info("[{}] ID application successful, startId : {}, endId : {}", resource, minId, maxId);
                    return;
                }
            } catch (Exception e) {
                Loggers.ID_GENERATOR.error("[{}] An error occurred while applying for ID, error : {}", resource, e);
            }
        }
        throw new AcquireIdException("[" + resource + "] The maximum number of retries exceeded");
    }

    @Override
    public void injectProtocol(ConsistencyProtocol<? extends Config> protocol) {
        this.protocol = protocol;
        this.protocol.protocolMetaData()
                .subscribe(bizInfo(), Constants.LEADER_META_DATA, new Observer() {
                    @Override
                    public void update(Observable o, Object arg) {
                        System.out.println(arg);
                        hasLeader = true;
                    }
                });
    }

    @Override
    public ConsistencyProtocol<? extends Config> getProtocol() {
        return protocol;
    }

    @SuppressWarnings("all")
    @Override
    public <D> GetResponse<D> getData(GetRequest request) {
        String resources = ByteUtils.toString(request.getCtx());
        IdStoreFile file = storeFileMap.get(resources);
        return GetResponse.<D>builder()
                .data((D) file.getCurrentMaxId())
                .build();
    }

    @SuppressWarnings("all")
    @Override
    public boolean onApply(Log log) {
        byte[] data = log.getData();
        final AcquireId acquireId = serializer.deSerialize(data, AcquireId.class);
        final String resources = acquireId.getApplicant();
        final long minId = acquireId.getMinId();
        final long maxId = acquireId.getMaxId();
        IdStoreFile storeFile = storeFileMap.get(resources);
        if (storeFile == null) {
            return false;
        }
        return storeFile.canAccept(maxId);
    }

    @Override
    public void onError(Throwable throwable) {
        Loggers.ID_GENERATOR.error("An error occurred while onApply for ID, error : {}", throwable);
    }

    @Override
    public String bizInfo() {
        return "default_id_generator";
    }

    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(new IdSnapshotOperation());
    }

    void loadFromFile(File parentFile) throws IOException {
        if (parentFile == null) {
            throw new NullPointerException();
        }

        if (!parentFile.isDirectory()) {
            throw new NotDirectoryException(parentFile.getPath() + " not a file directory");
        }

        for (File file : Optional.ofNullable(parentFile.listFiles()).orElse(EMPTY)) {
            String resourceName = file.getName();
            storeFileMap.computeIfAbsent(resourceName, s -> new IdStoreFile(resourceName));
            IdStoreFile storeFile = storeFileMap.get(resourceName);
            storeFile.forceWrite(file);
        }
    }

    private static class IdStoreFile {

        private final File file;

        public IdStoreFile(String resourceName) {
            try {
                file = new File(Paths.get(FILE_PATH, resourceName).toUri());
                DiskUtils.touch(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean canAccept(long maxId) {
            long currentMaxId = getCurrentMaxId();
            if (maxId > currentMaxId) {
                DiskUtils.writeFile(file, ByteUtils.toBytes(maxId), false);
                return true;
            }
            return false;
        }

        void forceWrite(File remoteFile) throws IOException {
            DiskUtils.copyFile(file, remoteFile);
        }

        public Long getCurrentMaxId() {
            String data = DiskUtils.readFile(file);
            if (StringUtils.isBlank(data)) {
                return -1L;
            }
            return Long.parseLong(data);
        }

    }

    class IdSnapshotOperation implements SnapshotOperation {

        @Override
        public void onSnapshotSave(Writer writer, CallFinally callFinally) {
            GlobalExecutor.executeByCommon(() -> {

                boolean result = false;
                Throwable throwable = null;
                final String writePath = writer.getPath();

                try {
                    final String parentPath = Paths.get(writePath, SNAPSHOT_DIR).toString();
                    DiskUtils.deleteDirectory(parentPath);
                    DiskUtils.forceMkdir(parentPath);
                    DiskUtils.copyDirectory(new File(FILE_PATH), new File(parentPath));
                    final String outputFile = Paths.get(writePath, SNAPSHOT_ARCHIVE).toString();
                    DiskUtils.compress(writePath, SNAPSHOT_DIR, outputFile, new CRC32());
                    DiskUtils.deleteDirectory(parentPath);
                    writer.addFile(SNAPSHOT_ARCHIVE);

                    result = true;
                } catch (Exception e) {
                    Loggers.ID_GENERATOR.error("path : {}, snapshot execution error : {}", writePath, e);
                    throwable = e;
                }

                callFinally.run(result, throwable);

            });
        }

        @Override
        public boolean onSnapshotLoad(Reader reader) {
            final String readerPath = reader.getPath();
            final String sourceFile = Paths.get(readerPath, SNAPSHOT_ARCHIVE).toString();
            TimerContext.start("[DefaultIdStore] RaftStore snapshot load job");
            try {
                DiskUtils.decompress(sourceFile, readerPath, new CRC32());
                final String loadPath = Paths.get(readerPath, SNAPSHOT_DIR).toString();
                Loggers.ID_GENERATOR.info("snapshot load from : {}", loadPath);
                DiskUtils.copyDirectory(new File(loadPath), new File(FILE_PATH));
                return true;
            } catch (final Throwable t) {
                Loggers.ID_GENERATOR.error("Fail to load snapshot, path={}, file list={}, error : {}.", readerPath,
                        reader.listFiles(), t);
                return false;
            } finally {
                TimerContext.end(Loggers.ID_GENERATOR);
            }
        }
    }
}
