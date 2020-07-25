/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.storage;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.exception.RocksStorageException;
import com.alibaba.nacos.core.utils.DiskUtils;
import org.rocksdb.BackupEngine;
import org.rocksdb.BackupableDBOptions;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RestoreOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

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
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class RocksStorage {

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

    public static RocksStorage createDefault(final String group, String baseDir) {
        return createCustomer(group, baseDir, new WriteOptions().setSync(true),
                new ReadOptions().setTotalOrderSeek(true));
    }

    public static RocksStorage createCustomer(final String group, String baseDir, WriteOptions writeOptions,
            ReadOptions readOptions) {

        RocksStorage storage = new RocksStorage();

        try {
            DiskUtils.forceMkdir(baseDir);
        } catch (IOException e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
        }
        createRocksDB(baseDir, group, writeOptions, readOptions, storage);
        return storage;
    }

    public void destroyAndOpenNew() throws RocksStorageException {
        try (final Options options = new Options()) {
            RocksDB.destroyDB(dbPath, options);
            createRocksDB(dbPath, group, writeOptions, readOptions, this);
        } catch (RocksDBException ex) {
            Status status = ex.getStatus();
            throw createRocksStorageException(status);
        }
    }

    private static void createRocksDB(final String baseDir, final String group, WriteOptions writeOptions,
                                      ReadOptions readOptions, final RocksStorage storage) {
        storage.cfOptions.clear();

        final DBOptions options = RocksDBUtils.getDefaultRocksDBOptions();
        final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
        final List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();
        final ColumnFamilyOptions cfOption = RocksDBUtils.createColumnFamilyOptions();
        storage.cfOptions.add(cfOption);
        columnFamilyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOption));
        try {
            RocksDB db = RocksDB.open(options, baseDir, columnFamilyDescriptors, columnFamilyHandles);
            storage.dbPath = baseDir;
            storage.group = group;
            storage.writeOptions = writeOptions;
            storage.readOptions = readOptions;
            storage.options = options;
            storage.db = db;
            storage.defaultHandle = columnFamilyHandles.get(0);
        } catch (RocksDBException e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
        }
    }

    public void write(byte[] key, byte[] value) throws RocksStorageException {
        try {
            this.db.put(defaultHandle, writeOptions, key, value);
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            throw createRocksStorageException(status);
        }
    }

    public void batchWrite(List<byte[]> key, List<byte[]> values) throws RocksStorageException {
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
            throw createRocksStorageException(status);
        }
    }

    public byte[] get(byte[] key) throws RocksStorageException {
        try {
            return db.get(defaultHandle, readOptions, key);
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            throw createRocksStorageException(status);
        }
    }

    public Map<byte[], byte[]> batchGet(List<byte[]> key) throws RocksStorageException {
        try {
            return db.multiGet(readOptions, key);
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            throw createRocksStorageException(status);
        }
    }

    public void delete(byte[] key) throws RocksStorageException {
        try {
            db.delete(defaultHandle, writeOptions, key);
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            throw createRocksStorageException(status);
        }
    }

    public void batchDelete(List<byte[]> key) throws RocksStorageException {
        try {
            for (byte[] k : key) {
                db.delete(defaultHandle, writeOptions, k);
            }
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            throw createRocksStorageException(status);
        }
    }

    public void snapshotSave(final String backupPath) throws RocksStorageException {
        final String path = Paths.get(backupPath, group).toString();
        Throwable ex = DiskUtils.forceMkdir(path, (aVoid, ioe) -> {
            BackupableDBOptions backupOpt = new BackupableDBOptions(path).setSync(true).setShareTableFiles(false);
            try {
                final BackupEngine backupEngine = BackupEngine.open(RocksStorage.this.options.getEnv(), backupOpt);
                backupEngine.createNewBackup(db, true);
                RocksBackupInfo backupInfo = Collections
                        .max(backupEngine.getBackupInfo().stream().map(RocksDBUtils::convertToRocksBackupInfo)
                                .collect(Collectors.toList()), Comparator.comparingInt(RocksBackupInfo::getBackupId));
                final File file = Paths.get(path, "meta_snapshot").toFile();
                DiskUtils.touch(file);
                DiskUtils.writeFile(file, JacksonUtils.toJsonBytes(backupInfo), false);
                return null;
            } catch (RocksDBException e) {
                Status status = e.getStatus();
                return createRocksStorageException(status);
            } catch (Throwable throwable) {
                return throwable;
            }
        }); if (ex != null) {
            throw new RocksStorageException(NacosException.SERVER_ERROR, ex);
        }
    }

    public void snapshotLoad(final String backupPath) throws RocksStorageException {
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
            backupEngine
                    .restoreDbFromBackup(info.getBackupId(), dbPath, dbOptions.walDir(),
                            options);
        } catch (RocksDBException ex) {
            Status status = ex.getStatus();
            throw createRocksStorageException(status);
        } catch (Throwable ex) {
            throw new RocksStorageException(NacosException.SERVER_ERROR, ex);
        }
    }

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

    private static RocksStorageException createRocksStorageException(Status status) {
        RocksStorageException exception = new RocksStorageException();
        exception.setErrCode(status.getCode().getValue());
        exception.setErrMsg(String.format("RocksDB error msg : code=%s, subCode=%s, state=%s", status.getCode(),
                status.getSubCode(), status.getState()));
        return exception;
    }

}
