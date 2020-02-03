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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.config.server.configuration.ClusterDataSourceV2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.distributed.raft.jraft.SnapshotOperate;
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.ZipUtils;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.zip.ZipOutputStream;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@ConditionalOnProperty(value = "nacos.config.store.type", havingValue = "inner", matchIfMissing = true)
@Component
public class DerbySnapshotOperate implements SnapshotOperate {

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


    private final DerbySnapShotMeta snapShotMeta = new DerbySnapShotMeta("com.alibaba.nacos.derby.snapshot");
    private final String queryAllDataByTable = "select * from %s";
    private final String SNAPSHOT_DIR = "db";
    private final String SNAPSHOT_ARCHIVE = "db-%s.zip";
    private final String fileSuffix = ".del";

    private ClusterDataSourceV2 dataSource;

    private Executor executor;

    @PostConstruct
    protected void init() {
        executor = ExecutorFactory.newSingleExecutorService(SnapshotOperate.class.getCanonicalName());
        dataSource = SpringUtils.getBean(ClusterDataSourceV2.class);
    }

    @Override
    public void onSnapshotSave(SnapshotWriter writer, Closure done) {
        executor.execute(() -> {
            try {
                final String writePath = writer.getPath();
                final String parentPath = Paths.get(writePath, SNAPSHOT_DIR).toString();
                final String outputFile = Paths.get(writePath, String.format(SNAPSHOT_ARCHIVE, LocalDateTime.now())).toString();
                final File file = new File(parentPath);
                FileUtils.deleteDirectory(file);
                FileUtils.forceMkdir(file);
                final List<String> sqls = new ArrayList<>();
                String sqlTemplate = "CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY('%s', '%s', null, null, null,)";
                for (String tableName : tableNames) {
                    final String queryAllData = String.format(queryAllDataByTable, tableNames);
                    final String exportFile = Paths.get(parentPath, tableName + fileSuffix).toString();
                    sqls.add(String.format(sqlTemplate, queryAllData, exportFile));
                }
                batchExec(sqls, "Snapshot save");
                try (final FileOutputStream fOut = new FileOutputStream(outputFile);
                     final ZipOutputStream zOut = new ZipOutputStream(fOut)) {
                    WritableByteChannel channel = Channels.newChannel(zOut);
                    ZipUtils.compressDirectoryToZipFile(writePath, SNAPSHOT_DIR, zOut,
                            channel);
                    FileUtils.deleteDirectory(file);
                }
                if (writer.addFile(SNAPSHOT_ARCHIVE, buildMetadata(snapShotMeta))) {
                    done.run(Status.OK());
                }
                else {
                    done.run(new Status(RaftError.EIO, "Fail to add snapshot file: %s",
                            parentPath));
                }
            } catch (Throwable t) {
                LogUtil.defaultLog.error("Fail to compress snapshot, path={}, file list={}, {}.",
                        writer.getPath(), writer.listFiles(), t);
                done.run(new Status(RaftError.EIO,
                        "Fail to compress snapshot at %s, error is %s", writer.getPath(),
                        t.getMessage()));
            }
        });
    }

    @Override
    public boolean onSnapshotLoad(SnapshotReader reader) {
        final String readerPath = reader.getPath();
        final String sourceFile = Paths.get(readerPath, SNAPSHOT_ARCHIVE).toString();
        try {
            ZipUtils.unzipFile(sourceFile, readerPath);
            final String loadPath = Paths.get(readerPath, SNAPSHOT_DIR).toString();
            LogUtil.defaultLog.info("snapshot load from : {}", loadPath);
            List<String> sqls = new ArrayList<>();
            final String sqlTemplate = "CALL SYSCS_UTIL.SYSCS_IMPORT_TABLE(null,'STAFF','%s',null,null,null,0);";
            for (String tableName : tableNames) {
                final String importFile = Paths.get(loadPath, tableName + fileSuffix).toString();
                sqls.add(String.format(sqlTemplate, importFile));
            }
            return batchExec(sqls, "Snapshot load");
        }
        catch (final Throwable t) {
            LogUtil.defaultLog.error("Fail to load snapshot, path={}, file list={}, {}.", readerPath,
                    reader.listFiles(), t);
            return false;
        }
    }

    private boolean batchExec(List<String> sqls, String type) {
        String sql = "";
        Connection holder = null;
        try (Connection connection = dataSource.getConnection()) {
            holder = connection;
            for (String t : sqls) {
                CallableStatement statement = connection.prepareCall(sql);
                sql = t;
                LogUtil.defaultLog.info("snapshot load exec sql : {}", sql);
                statement.execute();
            }
            connection.commit();
            return true;
        }
        catch (Exception e) {
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

    private static class DerbySnapShotMeta {

        private String name;
        private Map<String, Object> attributes = new HashMap<>(32);

        public DerbySnapShotMeta(String name) {
            this.name = name;
        }

        public DerbySnapShotMeta addAttribute(String key, Object value) {
            attributes.put(key, value);
            return this;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, Object> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, Object> attributes) {
            this.attributes = attributes;
        }

        @Override
        public String toString() {
            return "DerbySnapShotMeta{" +
                    "name='" + name + '\'' +
                    ", attributes=" + attributes +
                    '}';
        }
    }
}
