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

import com.alibaba.nacos.config.server.service.DynamicDataSource;
import com.alibaba.nacos.config.server.utils.GlobalExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.consistency.snapshot.CallFinally;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.utils.DiskUtils;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.SpringUtils;
import java.io.FileOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DerbySnapshotOperation implements SnapshotOperation {

    private final String queryAllDataByTable = "select * from %s";
    private final String SNAPSHOT_DIR = "derby_data";
    private final String SNAPSHOT_ARCHIVE = "derby_data.zip";
    private final String fileSuffix = ".del";
    private final String[] tableNames = new String[]{
            "config_info",
            "his_config_info",
            "config_info_beta",
            "config_info_tag",
            "config_info_aggr",
            "app_list",
            "app_configdata_relation_subs",
            "app_configdata_relation_pubs",
            "config_tags_relation",
            "group_capacity",
            "tenant_capacity",
            "tenant_info",
            "users",
            "roles",
            "permissions",
    };

    @Override
    public void onSnapshotSave(Writer writer, CallFinally callFinally) {
        GlobalExecutor.executeOnSnapshot(() -> {
            try {
                final String writePath = writer.getPath();
                final String parentPath = Paths.get(writePath, SNAPSHOT_DIR).toString();
                DiskUtils.deleteDirectory(parentPath);
                DiskUtils.forceMkdir(parentPath);
                final List<String> sqls = new ArrayList<>();
                String sqlTemplate = "CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY('%s', '%s', null, null, null)";
                for (String tableName : tableNames) {
                    final String queryAllData = String.format(queryAllDataByTable, tableNames);
                    final String exportFile = Paths.get(parentPath, tableName + fileSuffix).toString();
                    sqls.add(String.format(sqlTemplate, queryAllData, exportFile));
                }
                batchExec(sqls, "Snapshot save");
                final String outputFile = Paths.get(writePath, SNAPSHOT_ARCHIVE).toString();
                try (final FileOutputStream fOut = new FileOutputStream(outputFile);
                     final ZipOutputStream zOut = new ZipOutputStream(fOut)) {
                    WritableByteChannel channel = Channels.newChannel(zOut);
                    DiskUtils.compressDirectoryToZipFile(writePath, SNAPSHOT_DIR, zOut,
                            channel);
                    DiskUtils.deleteDirectory(parentPath);
                }
                callFinally.run(writer.addFile(SNAPSHOT_ARCHIVE), null);
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
            DiskUtils.unzipFile(sourceFile, readerPath);
            final String loadPath = Paths.get(readerPath, SNAPSHOT_DIR).toString();
            LogUtil.defaultLog.info("snapshot load from : {}", loadPath);
            List<String> sqls = new ArrayList<>();
            final String sqlTemplate = "CALL SYSCS_UTIL.SYSCS_IMPORT_TABLE(null,'STAFF','%s',null,null,null,0);";
            for (String tableName : tableNames) {
                final String importFile = Paths.get(loadPath, tableName + fileSuffix).toString();
                sqls.add(String.format(sqlTemplate, importFile));
            }
            return batchExec(sqls, "Snapshot load");
        } catch (final Throwable t) {
            LogUtil.defaultLog.error("Fail to load snapshot, path={}, file list={}, {}.", readerPath,
                    reader.listFiles(), t);
            return false;
        }
    }

    private boolean batchExec(List<String> sqls, String type) {
        DataSource dataSource = SpringUtils.getBean(DynamicDataSource.class).getDataSource()
                .getJdbcTemplate().getDataSource();

        if (dataSource == null) {
            throw new NullPointerException("The DataSource object does not exist in the Spring container");
        }

        String sql = "";
        Connection holder = null;
        try (Connection connection = dataSource.getConnection()) {
            holder = connection;
            for (String t : sqls) {
                sql = t;
                CallableStatement statement = connection.prepareCall(sql);
                LogUtil.defaultLog.info("snapshot load exec sql : {}", sql);
                statement.execute();
            }
            connection.commit();
            return true;
        } catch (Exception e) {
            if (Objects.nonNull(holder)) {
                try {
                    holder.rollback();
                } catch (SQLException ex) {
                    LogUtil.defaultLog.error("transaction rollback has error : {}", ExceptionUtil.getAllExceptionMsg(e));
                }
            }
            LogUtil.defaultLog.error(type + " exec sql : {} has some error : {}", sql, e);
            return false;
        }
    }
}
