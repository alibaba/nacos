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

import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.NAsyncHttpClient;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.HttpRestResult;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alibaba.nacos.core.file.FileChangeEvent;
import com.alibaba.nacos.core.file.FileWatcher;
import com.alibaba.nacos.core.file.WatchFileCenter;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.SystemUtils;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.UndefineMagicConstantRule")
public class ClusterConfSyncTask extends Task {

    private static final TypeReference<RestResult<String>> STRING_REFERENCE = new TypeReference<RestResult<String>>() {
    };
    private final ServletContext context;
    private int addressServerFailCount = 0;
    private int maxFailCount = 12;
    private volatile boolean alreadyLoadServer = false;

    private final String url = InetUtils.getSelfIp() + ":" + memberManager.getPort() + "?" + SpringUtils.getProperty("nacos.standalone.params");

    private Runnable standaloneJob = () -> readServerConf(Collections.singletonList(url));

    public ClusterConfSyncTask(final ServerMemberManager memberManager, final ServletContext context) {
        super(memberManager);
        this.maxFailCount = Integer.parseInt(SpringUtils.getProperty("maxHealthCheckFailCount", "12"));
        this.context = context;
    }

    @Override
    public void init() {

        if (SystemUtils.STANDALONE_MODE) {
            standaloneJob.run();
        } else {

            readServerConfFromDisk();

            // Use the inotify mechanism to monitor file changes and automatically trigger the reading of cluster.conf

            WatchFileCenter.registerWatcher(SystemUtils.getConfFilePath(),
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

        if (SystemUtils.STANDALONE_MODE) {
            standaloneJob.run();
            return;
        }

        // Whether to enable the node self-discovery function that comes with nacos
        // The reason why instance properties are not used here is so that
        // the hot update mechanism can be implemented later

        boolean discovery = SpringUtils.getProperty("nacos.core.member.self-discovery", Boolean.class, false);

        if (!discovery) {
            syncFromAddressUrl();
        }
    }

    @Override
    protected void after() {
        if (!SystemUtils.STANDALONE_MODE) {
            GlobalExecutor.scheduleSyncJob(this, 5_000L);
        }
    }

    private void syncFromAddressUrl() {
        if (!alreadyLoadServer && memberManager.getUseAddressServer()) {
            asyncHttpClient.get(memberManager.getAddressServerUrl(), Header.EMPTY, Query.EMPTY, STRING_REFERENCE, new Callback<String>() {
                @Override
                public void onReceive(HttpRestResult<String> result) {
                    if (HttpServletResponse.SC_OK == result.getCode()) {
                        memberManager.setAddressServerHealth(true);
                        Reader reader = new StringReader(result.getData());
                        try {
                            readServerConf(SystemUtils.analyzeClusterConf(reader));
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
            List<String> members = SystemUtils.readClusterConf();
            readServerConf(members);
            alreadyLoadServer = true;
        } catch (Exception e) {
            Loggers.CLUSTER.error("nacos-XXXX [serverlist] failed to get serverlist from disk!, error : {}", e);
            alreadyLoadServer = false;
        }
    }

    // 默认配置格式解析，只有nacos-server的ip:port or hostname:port 信息
    // example 192.168.16.1:8848?raft_port=8849&key=value

    private void readServerConf(List<String> members) {
        Set<Member> nodes = new HashSet<>();
        int selfPort = memberManager.getPort();

        // Nacos default port is 8848

        int defaultPort = 8848;

        // Set the default Raft port information for security

        int defaultRaftPort = selfPort + 1000 >= 65535 ? selfPort + 1 : selfPort + 1000;

        for (String member : members) {
            String[] memberDetails = member.split("\\?");
            String address = memberDetails[0];
            int port = defaultPort;
            if (address.contains(":")) {
                String[] info = address.split(":");
                address = info[0];
                port = Integer.parseInt(info[1]);
            }

            // example ip:port?raft_port=&node_name=

            Map<String, String> extendInfo = new HashMap<>(4);

            if (memberDetails.length == 2) {
                String[] parameters = memberDetails[1].split("&");
                for (String parameter : parameters) {
                    String[] info = parameter.split("=");
                    extendInfo.put(info[0].trim(), info[1].trim());
                }
            } else {

                // The Raft Port information needs to be set by default
                extendInfo.put(RaftSysConstants.RAFT_PORT, String.valueOf(defaultRaftPort));

            }

            nodes.add(Member.builder()
                    .ip(address)
                    .port(port)
                    .extendInfo(extendInfo)
                    .state(NodeState.UP)
                    .build());

        }

        Loggers.CLUSTER.info("have member join the cluster : {}", nodes);

        memberManager.memberJoin(nodes);

    }
}
