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

package com.alibaba.nacos.core.cluster.task;

import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.file.FileChangeEvent;
import com.alibaba.nacos.core.file.FileWatcher;
import com.alibaba.nacos.core.file.WatchFileCenter;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.ApplicationUtils;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;

/**
 * Responsible for handling address server logic and cluster.conf file change monitoring
 *
 * If the user sets the address of the address server, it will automatically turn to
 * synchronize the information of the entire cluster node from the address server.
 * Otherwise, the file change of cluster.conf will be monitored. When the cluster.conf
 * file changes, the inotify mechanism will be used to change the node information
 * at the same time
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.UndefineMagicConstantRule")
public class ClusterConfSyncTask extends Task {

    private static final GenericType<RestResult<String>> STRING_REFERENCE = new GenericType<RestResult<String>>() {
    };
    private int addressServerFailCount = 0;
    private int maxFailCount = 12;
    private volatile boolean alreadyLoadServer = false;

    private Runnable standaloneJob = () -> {
        String url = InetUtils.getSelfIp() + ":" + memberManager.getPort() + "?" + ApplicationUtils
                .getProperty("nacos.standalone.params", "");
        MemberUtils.readServerConf(Collections.singletonList(url), memberManager);
    };

    public ClusterConfSyncTask(final ServerMemberManager memberManager) {
        super(memberManager);
        this.maxFailCount = Integer.parseInt(
                ApplicationUtils.getProperty("maxHealthCheckFailCount", "12"));
    }

    @Override
    public void init() {

        if (ApplicationUtils.getStandaloneMode()) {
            standaloneJob.run();
        } else {

            readServerConfFromDisk();

            // Use the inotify mechanism to monitor file changes and automatically trigger the reading of cluster.conf

            WatchFileCenter.registerWatcher(ApplicationUtils.getConfFilePath(),
                    new FileWatcher() {
                        @Override
                        public void onChange(FileChangeEvent event) {
                            readServerConfFromDisk();
                        }

                        @Override
                        public boolean interest(String context) {
                            return StringUtils.contains(context, "cluster.conf");
                        }
                    });
        }
    }

    @Override
    protected void executeBody() {

        if (ApplicationUtils.getStandaloneMode()) {
            standaloneJob.run();
            return;
        }

        // Whether to enable the node self-discovery function that comes with nacos
        // The reason why instance properties are not used here is so that
        // the hot update mechanism can be implemented later

        boolean discovery = ApplicationUtils
                .getProperty("nacos.core.member.self-discovery", Boolean.class, false);

        if (!discovery) {
            syncFromAddressUrl();
        }
    }

    @Override
    protected void after() {
        if (!ApplicationUtils.getStandaloneMode()) {
            GlobalExecutor.scheduleSyncJob(this, 5_000L);
        }
    }

    private void syncFromAddressUrl() {
        if (!alreadyLoadServer && memberManager.getUseAddressServer()) {
            asyncHttpClient.get(memberManager.getAddressServerUrl(), Header.EMPTY, Query.EMPTY, STRING_REFERENCE.getType(), new Callback<String>() {
                @Override
                public void onReceive(RestResult<String> result) {
                    if (HttpServletResponse.SC_OK == result.getCode()) {
                        memberManager.setAddressServerHealth(true);
                        Reader reader = new StringReader(result.getData());
                        try {
                            MemberUtils.readServerConf(ApplicationUtils.analyzeClusterConf(reader), memberManager);
                        } catch (Exception e) {
                            Loggers.CLUSTER.error("[serverlist] exception for analyzeClusterConf, error : {}", e);
                        }
                        addressServerFailCount = 0;
                        memberManager.setAddressServerHealth(true);
                    } else {
                        addressServerFailCount++;
                        if (addressServerFailCount >= maxFailCount) {
                            memberManager.setAddressServerHealth(false);
                        }
                        Loggers.CLUSTER.error("[serverlist] failed to get serverlist, error code {}", result.getCode());
                    }
                }

                @Override
                public void onError(Throwable e) {
                    addressServerFailCount++;
                    if (addressServerFailCount >= maxFailCount) {
                        memberManager.setAddressServerHealth(false);
                    }
                    Loggers.CLUSTER.error("[serverlist] exception, error : {}", e);
                }
            });
        }
    }

    private void readServerConfFromDisk() {
        try {
            List<String> members = ApplicationUtils.readClusterConf();
            MemberUtils.readServerConf(members, memberManager);
            alreadyLoadServer = true;
        } catch (FileNotFoundException e) {

            // Just to test

            String clusters = ApplicationUtils.getProperty("nacos.cluster");
            if (StringUtils.isNotBlank(clusters)) {
                String[] details = clusters.split(",");
                List<String> members = new ArrayList<>();
                for (String item : details) {
                    members.add(item.trim());
                }
                MemberUtils.readServerConf(members, memberManager);
                alreadyLoadServer = true;
            }
        } catch (Exception e) {
            Loggers.CLUSTER.error("nacos-XXXX [serverlist] failed to get serverlist from disk!, error : {}", e);
            alreadyLoadServer = false;
        }
    }

}
