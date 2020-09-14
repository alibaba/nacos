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

package com.alibaba.nacos.core.storage.kv;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.core.exception.KVStorageException;
import com.alibaba.nacos.core.utils.DiskUtils;
import com.alipay.sofa.jraft.util.Platform;
import com.alipay.sofa.jraft.util.Utils;
import org.rocksdb.BackupEngine;
import org.rocksdb.BackupInfo;
import org.rocksdb.BackupableDBOptions;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompactionStyle;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.IndexType;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RestoreOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Statistics;
import org.rocksdb.Status;
import org.rocksdb.StringAppendOperator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.rocksdb.util.SizeUnit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Encapsulate rocksDB operations.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class RocksStorage implements KvStorage {
    
    private String group;
    
    private DBOptions options;
    
    private RocksDB db;
    
    private WriteOptions writeOptions;
    
    private ReadOptions readOptions;
    
    private ColumnFamilyHandle defaultHandle;
    
    private String dbPath;
    
    private final List<ColumnFamilyOptions> cfOptions = new ArrayList<>();
    
    static {
        RocksDB.loadLibrary();
    }
    
    private RocksStorage() {
    }
    
    /**
     * create rocksdb storage with default operation.
     *
     * @param group   group
     * @param baseDir base dir
     * @return {@link RocksStorage}
     */
    public static RocksStorage createDefault(final String group, final String baseDir) {
        return createCustomer(group, baseDir, new WriteOptions().setSync(true),
                new ReadOptions().setTotalOrderSeek(true));
    }
    
    /**
     * create rocksdb storage and set customer operation.
     *
     * @param group        group
     * @param baseDir      base dir
     * @param writeOptions {@link WriteOptions}
     * @param readOptions  {@link ReadOptions}
     * @return {@link RocksStorage}
     */
    public static RocksStorage createCustomer(final String group, String baseDir, WriteOptions writeOptions,
            ReadOptions readOptions) {
        RocksStorage storage = new RocksStorage();
        try {
            DiskUtils.forceMkdir(baseDir);
        } catch (IOException e) {
            throw new NacosRuntimeException(ErrorCode.IOMakeDirError.getCode(), e);
        }
        createRocksDB(baseDir, group, writeOptions, readOptions, storage);
        return storage;
    }
    
    /**
     * destroy old rocksdb and open new one.
     *
     * @throws KVStorageException RocksStorageException
     */
    public void destroyAndOpenNew() throws KVStorageException {
        try (final Options options = new Options()) {
            RocksDB.destroyDB(dbPath, options);
            createRocksDB(dbPath, group, writeOptions, readOptions, this);
        } catch (RocksDBException ex) {
            Status status = ex.getStatus();
            throw createRocksStorageException(ErrorCode.KVStorageResetError, status);
        }
    }
    
    /**
     * create rocksdb.
     *
     * @param baseDir      base dir
     * @param group        group
     * @param writeOptions {@link WriteOptions}
     * @param readOptions  {@link ReadOptions}
     * @param storage      {@link RocksStorage}
     */
    private static void createRocksDB(final String baseDir, final String group, WriteOptions writeOptions,
            ReadOptions readOptions, final RocksStorage storage) {
        storage.cfOptions.clear();
        
        final DBOptions options = getDefaultRocksDBOptions();
        final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
        final List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();
        final ColumnFamilyOptions cfOption = createColumnFamilyOptions();
        storage.cfOptions.add(cfOption);
        columnFamilyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOption));
        try {
            storage.dbPath = baseDir;
            storage.group = group;
            storage.writeOptions = writeOptions;
            storage.readOptions = readOptions;
            storage.options = options;
            storage.db = RocksDB.open(options, baseDir, columnFamilyDescriptors, columnFamilyHandles);
            storage.defaultHandle = columnFamilyHandles.get(0);
        } catch (RocksDBException e) {
            throw new NacosRuntimeException(ErrorCode.KVStorageCreateError.getCode(), e);
        }
    }
    
    @Override
    public void put(byte[] key, byte[] value) throws KVStorageException {
        try {
            this.db.put(defaultHandle, writeOptions, key, value);
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            throw createRocksStorageException(ErrorCode.KVStorageWriteError, status);
        }
    }
    
    @Override
    public void batchPut(List<byte[]> key, List<byte[]> values) throws KVStorageException {
        if (key.size() != values.size()) {
            throw new IllegalArgumentException("key size and values size must be equals!");
        }
        try (final WriteBatch batch = new WriteBatch()) {
            for (int i = 0; i < key.size(); i++) {
                batch.put(defaultHandle, key.get(i), values.get(i));
            }
            db.write(writeOptions, batch);
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            throw createRocksStorageException(ErrorCode.KVStorageWriteError, status);
        }
    }
    
    @Override
    public byte[] get(byte[] key) throws KVStorageException {
        try {
            return db.get(defaultHandle, readOptions, key);
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            throw createRocksStorageException(ErrorCode.KVStorageReadError, status);
        }
    }
    
    @Override
    public Map<byte[], byte[]> batchGet(List<byte[]> keys) throws KVStorageException {
        try {
            return db.multiGet(readOptions, keys);
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            throw createRocksStorageException(ErrorCode.KVStorageReadError, status);
        }
    }
    
    @Override
    public void delete(byte[] key) throws KVStorageException {
        try {
            db.delete(defaultHandle, writeOptions, key);
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            throw createRocksStorageException(ErrorCode.KVStorageDeleteError, status);
        }
    }
    
    @Override
    public void batchDelete(List<byte[]> key) throws KVStorageException {
        try {
            for (byte[] k : key) {
                db.delete(defaultHandle, writeOptions, k);
            }
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            throw createRocksStorageException(ErrorCode.KVStorageDeleteError, status);
        }
    }
    
    @Override
    public void shutdown() {
        this.defaultHandle.close();
        this.db.close();
        for (final ColumnFamilyOptions opt : this.cfOptions) {
            opt.close();
        }
        this.options.close();
        this.writeOptions.close();
        this.readOptions.close();
    }
    
    /**
     * do snapshot save operation.
     *
     * @param backupPath backup path
     * @throws KVStorageException RocksStorageException
     */
    @Override
    public void doSnapshot(final String backupPath) throws KVStorageException {
        final String path = Paths.get(backupPath, group).toString();
        Throwable ex = DiskUtils.forceMkdir(path, (aVoid, ioe) -> {
            BackupableDBOptions backupOpt = new BackupableDBOptions(path).setSync(true).setShareTableFiles(false);
            try {
                final BackupEngine backupEngine = BackupEngine.open(RocksStorage.this.options.getEnv(), backupOpt);
                backupEngine.createNewBackup(db, true);
                RocksBackupInfo backupInfo = Collections
                        .max(backupEngine.getBackupInfo().stream().map(RocksStorage::convertToRocksBackupInfo)
                                .collect(Collectors.toList()), Comparator.comparingInt(RocksBackupInfo::getBackupId));
                final File file = Paths.get(path, "meta_snapshot").toFile();
                DiskUtils.touch(file);
                DiskUtils.writeFile(file, JacksonUtils.toJsonBytes(backupInfo), false);
                return null;
            } catch (RocksDBException e) {
                Status status = e.getStatus();
                return createRocksStorageException(ErrorCode.KVStorageSnapshotSaveError, status);
            } catch (Throwable throwable) {
                return throwable;
            }
        });
        if (ex != null) {
            throw new KVStorageException(ErrorCode.UnKnowError, ex);
        }
    }
    
    /**
     * do snapshot load operation.
     *
     * @param backupPath backup path
     * @throws KVStorageException RocksStorageException
     */
    @Override
    public void snapshotLoad(final String backupPath) throws KVStorageException {
        try {
            final String path = Paths.get(backupPath, group).toString();
            final File file = Paths.get(path, "meta_snapshot").toFile();
            final String content = DiskUtils.readFile(file);
            if (StringUtils.isBlank(content)) {
                throw new IllegalStateException("snapshot file not exist");
            }
            RocksBackupInfo info = JacksonUtils.toObj(content, RocksBackupInfo.class);
            BackupableDBOptions backupOpt = new BackupableDBOptions(path).setSync(true).setShareTableFiles(false);
            final BackupEngine backupEngine = BackupEngine.open(RocksStorage.this.options.getEnv(), backupOpt);
            final RestoreOptions options = new RestoreOptions(true);
            final DBOptions dbOptions = RocksStorage.this.options;
            backupEngine.restoreDbFromBackup(info.getBackupId(), dbPath, dbOptions.walDir(), options);
        } catch (RocksDBException ex) {
            Status status = ex.getStatus();
            throw createRocksStorageException(ErrorCode.KVStorageSnapshotLoadError, status);
        } catch (Throwable ex) {
            throw new KVStorageException(ErrorCode.UnKnowError, ex);
        }
    }
    
    private static KVStorageException createRocksStorageException(ErrorCode code, Status status) {
        KVStorageException exception = new KVStorageException();
        exception.setErrCode(code.getCode());
        exception.setErrMsg(
                String.format("RocksDB error msg : code=%s, subCode=%s, state=%s", status, status.getSubCode(),
                        status.getState()));
        return exception;
    }
    
    /**
     * RocksDB backup info.
     *
     * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
     */
    public static class RocksBackupInfo {
        
        private int backupId;
        
        private long timestamp;
        
        private long size;
        
        private int numberFiles;
        
        private String appMetadata;
        
        public int getBackupId() {
            return backupId;
        }
        
        public void setBackupId(int backupId) {
            this.backupId = backupId;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
        
        public long getSize() {
            return size;
        }
        
        public void setSize(long size) {
            this.size = size;
        }
        
        public int getNumberFiles() {
            return numberFiles;
        }
        
        public void setNumberFiles(int numberFiles) {
            this.numberFiles = numberFiles;
        }
        
        public String getAppMetadata() {
            return appMetadata;
        }
        
        public void setAppMetadata(String appMetadata) {
            this.appMetadata = appMetadata;
        }
        
        @Override
        public String toString() {
            return "RocksBackupInfo{" + "backupId=" + backupId + ", timestamp=" + timestamp + ", size=" + size
                    + ", numberFiles=" + numberFiles + ", appMetadata='" + appMetadata + '\'' + '}';
        }
    }
    
    public static RocksBackupInfo convertToRocksBackupInfo(BackupInfo info) {
        RocksBackupInfo backupInfo = new RocksBackupInfo();
        backupInfo.setBackupId(info.backupId());
        backupInfo.setAppMetadata(info.appMetadata());
        backupInfo.setNumberFiles(info.numberFiles());
        backupInfo.setSize(info.size());
        backupInfo.setTimestamp(info.timestamp());
        return backupInfo;
    }
    
    public static DBOptions getDefaultRocksDBOptions() {
        // Turn based on https://github.com/facebook/rocksdb/wiki/RocksDB-Tuning-Guide
        final DBOptions opts = new DBOptions();
        
        // If this value is set to true, then the database will be created if it is
        // missing during {@code RocksDB.open()}.
        opts.setCreateIfMissing(true);
        
        // If true, missing column families will be automatically created.
        opts.setCreateMissingColumnFamilies(true);
        
        // Number of open files that can be used by the DB.  You may need to increase
        // this if your database has a large working set. Value -1 means files opened
        // are always kept open.
        opts.setMaxOpenFiles(-1);
        
        // The maximum number of concurrent background compactions. The default is 1,
        // but to fully utilize your CPU and storage you might want to increase this
        // to approximately number of cores in the system.
        opts.setMaxBackgroundCompactions(Math.min(Utils.cpus(), 4));
        
        // The maximum number of concurrent flush operations. It is usually good enough
        // to set this to 1.
        opts.setMaxBackgroundFlushes(1);
        
        opts.setStatistics(new Statistics());
        opts.setEnv(Env.getDefault());
        return opts;
    }
    
    public static ColumnFamilyOptions createColumnFamilyOptions() {
        ColumnFamilyOptions options = getDefaultRocksDBColumnFamilyOptions();
        return options.useFixedLengthPrefixExtractor(8) //
                .setTableFormatConfig(getDefaultRocksDBTableConfig()) //
                .setMergeOperator(new StringAppendOperator());
    }
    
    public static BlockBasedTableConfig getDefaultRocksDBTableConfig() {
        // See https://github.com/sofastack/sofa-jraft/pull/156
        return new BlockBasedTableConfig() //
                // Begin to use partitioned index filters
                // https://github.com/facebook/rocksdb/wiki/Partitioned-Index-Filters#how-to-use-it
                .setIndexType(IndexType.kTwoLevelIndexSearch) //
                .setFilter(new BloomFilter(16, false)) //
                .setPartitionFilters(true) //
                .setMetadataBlockSize(8 * SizeUnit.KB) //
                .setCacheIndexAndFilterBlocks(false) //
                .setCacheIndexAndFilterBlocksWithHighPriority(true) //
                .setPinL0FilterAndIndexBlocksInCache(true) //
                // End of partitioned index filters settings.
                .setBlockSize(4 * SizeUnit.KB)//
                .setBlockCacheSize(512 * SizeUnit.MB) //
                .setCacheNumShardBits(8);
    }
    
    public static ColumnFamilyOptions getDefaultRocksDBColumnFamilyOptions() {
        final ColumnFamilyOptions opts = new ColumnFamilyOptions();
        
        // Flushing options:
        // write_buffer_size sets the size of a single mem_table. Once mem_table exceeds
        // this size, it is marked immutable and a new one is created.
        opts.setWriteBufferSize(64 * SizeUnit.MB);
        
        // Flushing options:
        // max_write_buffer_number sets the maximum number of mem_tables, both active
        // and immutable.  If the active mem_table fills up and the total number of
        // mem_tables is larger than max_write_buffer_number we stall further writes.
        // This may happen if the flush process is slower than the write rate.
        opts.setMaxWriteBufferNumber(3);
        
        // Flushing options:
        // min_write_buffer_number_to_merge is the minimum number of mem_tables to be
        // merged before flushing to storage. For example, if this option is set to 2,
        // immutable mem_tables are only flushed when there are two of them - a single
        // immutable mem_table will never be flushed.  If multiple mem_tables are merged
        // together, less data may be written to storage since two updates are merged to
        // a single key. However, every Get() must traverse all immutable mem_tables
        // linearly to check if the key is there. Setting this option too high may hurt
        // read performance.
        opts.setMinWriteBufferNumberToMerge(1);
        
        // Level Style Compaction:
        // level0_file_num_compaction_trigger -- Once level 0 reaches this number of
        // files, L0->L1 compaction is triggered. We can therefore estimate level 0
        // size in stable state as
        // write_buffer_size * min_write_buffer_number_to_merge * level0_file_num_compaction_trigger.
        opts.setLevel0FileNumCompactionTrigger(10);
        
        // Soft limit on number of level-0 files. We start slowing down writes at this
        // point. A value 0 means that no writing slow down will be triggered by number
        // of files in level-0.
        opts.setLevel0SlowdownWritesTrigger(20);
        
        // Maximum number of level-0 files.  We stop writes at this point.
        opts.setLevel0StopWritesTrigger(40);
        
        // Level Style Compaction:
        // max_bytes_for_level_base and max_bytes_for_level_multiplier
        //  -- max_bytes_for_level_base is total size of level 1. As mentioned, we
        // recommend that this be around the size of level 0. Each subsequent level
        // is max_bytes_for_level_multiplier larger than previous one. The default
        // is 10 and we do not recommend changing that.
        opts.setMaxBytesForLevelBase(512 * SizeUnit.MB);
        
        // Level Style Compaction:
        // target_file_size_base and target_file_size_multiplier
        //  -- Files in level 1 will have target_file_size_base bytes. Each next
        // level's file size will be target_file_size_multiplier bigger than previous
        // one. However, by default target_file_size_multiplier is 1, so files in all
        // L1..LMax levels are equal. Increasing target_file_size_base will reduce total
        // number of database files, which is generally a good thing. We recommend setting
        // target_file_size_base to be max_bytes_for_level_base / 10, so that there are
        // 10 files in level 1.
        opts.setTargetFileSizeBase(64 * SizeUnit.MB);
        
        // If prefix_extractor is set and memtable_prefix_bloom_size_ratio is not 0,
        // create prefix bloom for memtable with the size of
        // write_buffer_size * memtable_prefix_bloom_size_ratio.
        // If it is larger than 0.25, it is santinized to 0.25.
        opts.setMemtablePrefixBloomSizeRatio(0.125);
        
        // Seems like the rocksDB jni for Windows doesn't come linked with any of the
        // compression type
        if (!Platform.isWindows()) {
            opts.setCompressionType(CompressionType.LZ4_COMPRESSION) //
                    .setCompactionStyle(CompactionStyle.LEVEL) //
                    .optimizeLevelStyleCompaction();
        }
        
        // https://github.com/facebook/rocksdb/pull/5744
        opts.setForceConsistencyChecks(true);
        
        return opts;
    }
    
}
