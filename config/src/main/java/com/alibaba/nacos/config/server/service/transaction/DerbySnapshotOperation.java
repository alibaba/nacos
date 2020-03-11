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

package com.alibaba.nacos.config.server.service.transaction;

import com.alibaba.nacos.config.server.service.DataSourceService;
import com.alibaba.nacos.config.server.service.DynamicDataSource;
import com.alibaba.nacos.config.server.service.LocalDataSourceServiceImpl;
import com.alibaba.nacos.config.server.utils.GlobalExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.consistency.snapshot.CallFinally;
import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.utils.DiskUtils;
import com.alibaba.nacos.core.utils.SpringUtils;
import java.io.File;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.concurrent.Callable;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import javax.sql.DataSource;

import static com.alibaba.nacos.core.utils.SystemUtils.NACOS_HOME;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DerbySnapshotOperation implements SnapshotOperation {

    private final String SNAPSHOT_DIR = "derby_data";
    private final String SNAPSHOT_ARCHIVE = "derby_data.zip";

    private static final String DERBY_BASE_DIR = Paths.get(NACOS_HOME, "data", "derby-data").toString();

    private final String restoreDB = "jdbc:derby:" + DERBY_BASE_DIR;

    @Override
    public void onSnapshotSave(Writer writer, CallFinally callFinally) {
        GlobalExecutor.executeOnSnapshot(() -> {
            try {
                final String writePath = writer.getPath();
                final String parentPath = Paths.get(writePath, SNAPSHOT_DIR).toString();
                DiskUtils.deleteDirectory(parentPath);
                DiskUtils.forceMkdir(parentPath);

                doDerbyBackup(parentPath);

                final String outputFile = Paths.get(writePath, SNAPSHOT_ARCHIVE).toString();
                final Checksum checksum = new CRC32();
                DiskUtils.compress(writePath, SNAPSHOT_DIR, outputFile, checksum);
                DiskUtils.deleteDirectory(parentPath);

                final LocalFileMeta meta = new LocalFileMeta();
                meta.append("checkSum", checksum.getValue());

                callFinally.run(writer.addFile(SNAPSHOT_ARCHIVE, meta), null);
            } catch (Throwable t) {
                LogUtil.fatalLog.error("Fail to compress snapshot, path={}, file list={}, {}.",
                        writer.getPath(), writer.listFiles(), t);
                callFinally.run(false, t);
            }
        });
    }

    @Override
    public boolean onSnapshotLoad(Reader reader) {
        final String readerPath = reader.getPath();
        final String sourceFile = Paths.get(readerPath, SNAPSHOT_ARCHIVE).toString();
        try {

            final Checksum checksum = new CRC32();
            DiskUtils.decompress(sourceFile, readerPath, checksum);

            final String loadPath = Paths.get(readerPath, SNAPSHOT_DIR, "derby-data").toString();
            LogUtil.fatalLog.info("snapshot load from : {}, and copy to : {}", loadPath, DERBY_BASE_DIR);

            doDerbyRestoreFromBackup(() -> {
                final File srcDir = new File(loadPath);
                final File destDir = new File(DERBY_BASE_DIR);

                DiskUtils.copyDirectory(srcDir, destDir);
                return null;
            });
            DiskUtils.deleteDirectory(loadPath);

            return true;
        } catch (final Throwable t) {
            LogUtil.fatalLog.error("Fail to load snapshot, path={}, file list={}, {}.", readerPath,
                    reader.listFiles(), t);
            return false;
        }
    }

    private void doDerbyBackup(String backupDirectory) throws Exception {
        DataSourceService sourceService = SpringUtils.getBean(DynamicDataSource.class).getDataSource();
        DataSource dataSource = sourceService.getJdbcTemplate().getDataSource();
        try (Connection holder = dataSource.getConnection()) {
            CallableStatement cs = holder.prepareCall("CALL SYSCS_UTIL.SYSCS_BACKUP_DATABASE(?)");
            cs.setString(1, backupDirectory);
            cs.execute();
        }
    }

    private void doDerbyRestoreFromBackup(Callable<Void> callable) throws Exception {
        DataSourceService sourceService = SpringUtils.getBean(DynamicDataSource.class).getDataSource();
        LocalDataSourceServiceImpl localDataSourceService = (LocalDataSourceServiceImpl) sourceService;
        localDataSourceService.reopenDerby(restoreDB, callable);
    }

}
