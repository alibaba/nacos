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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.NAsyncHttpClient;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.HttpResResult;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alibaba.nacos.core.file.FileChangeEvent;
import com.alibaba.nacos.core.file.FileWatcher;
import com.alibaba.nacos.core.file.WatchFileCenter;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.SystemUtils;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
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
public class SyncNodeTask extends Task {

    private static final TypeReference<ResResult<Collection<Member>>> TYPE_REFERENCE = new TypeReference<ResResult<Collection<Member>>>() {
    };

    private static final TypeReference<ResResult<String>> REFERENCE = new TypeReference<ResResult<String>>() {
    };
    private final ServletContext context;
    private int addressServerFailCount = 0;
    private int maxFailCount = 12;
    private NAsyncHttpClient httpclient;
    private volatile boolean alreadyLoadServer = false;

    private Runnable standaloneJob = () -> {
        final String url = InetUtils.getSelfIp() + ":" + memberManager.getPort() + "?" + SpringUtils.getProperty("nacos.standalone.params");
        readServerConf(Collections.singletonList(url));
    };

    public SyncNodeTask(final ServletContext context) {
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Integer.parseInt(SpringUtils.getProperty("notifyConnectTimeout", "100")))
                .setSocketTimeout(Integer.parseInt(SpringUtils.getProperty("notifySocketTimeout", "200"))).build();
        this.httpclient = HttpClientManager.newAsyncHttpClient(getClass().getCanonicalName(), requestConfig);
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

        if (SpringUtils.getProperty("nacos.core.member.self-discovery", Boolean.class, false)) {
            syncBySelfDiscovery();
        } else {
            syncFromAddressUrl();
        }

        GlobalExecutor.scheduleSyncJob(this::executeBody, 10_000L);
    }

    private void syncBySelfDiscovery() {
        Collection<Member> members = memberManager.allMembers();

        final Query selfInfo = Query.newInstance()
                .addParam("self", JSON.toJSONString(memberManager.self()));

        for (Member member : members) {

            if (memberManager.isSelf(member)) {
                continue;
            }

            final String url = HttpUtils.buildUrl(false, member.address(), context.getContextPath(), Commons.NACOS_CORE_CONTEXT, "/cluster/nodes");

            httpclient.get(url, Header.EMPTY, selfInfo, TYPE_REFERENCE, new Callback<Collection<Member>>() {
                @Override
                public void onReceive(HttpResResult<Collection<Member>> result) {
                    if (result.ok()) {

                        Collection<Member> remoteMembers = result.getData();
                        if (remoteMembers != null) {
                            updateCluster(remoteMembers);
                        }

                    } else {
                        Loggers.CORE.error("[serverlist] failed to get serverlist from server : {}, error : {}", member.address(), result);
                    }
                    memberManager.getServerListUnHealth().remove(member);
                    memberManager.getLastRefreshTimeRecord().put(member.address(), System.currentTimeMillis());
                }

                @Override
                public void onError(Throwable e) {
                    Loggers.CORE.error("[serverlist] exception : {}, node : {}", e, member.address());
                }
            });
        }
    }

    private void syncFromAddressUrl() {
        if (!alreadyLoadServer && memberManager.getUseAddressServer()) {
            httpclient.get(memberManager.getAddressServerUrl(), Header.EMPTY, Query.EMPTY, REFERENCE, new Callback<String>() {
                @Override
                public void onReceive(HttpResResult<String> result) {
                    if (HttpServletResponse.SC_OK == result.getCode()) {
                        memberManager.setAddressServerHealth(true);
                        Reader reader = new StringReader(result.getData());
                        try {
                            readServerConf(SystemUtils.analyzeClusterConf(reader));
                        } catch (Exception e) {
                            Loggers.CORE.error("[serverlist] exception for analyzeClusterConf, error : {}", e);
                        }
                        addressServerFailCount = 0;
                    } else {
                        addressServerFailCount++;
                        if (addressServerFailCount >= maxFailCount) {
                            memberManager.setAddressServerHealth(false);
                        }
                        Loggers.CORE.error("[serverlist] failed to get serverlist, error code {}", result.getCode());
                    }
                }

                @Override
                public void onError(Throwable e) {
                    addressServerFailCount++;
                    if (addressServerFailCount >= maxFailCount) {
                        memberManager.setAddressServerHealth(false);
                    }
                    Loggers.CORE.error("[serverlist] exception, error : {}", e);
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
            Loggers.CORE.error("nacos-XXXX [serverlist] failed to get serverlist from disk!, error : {}", e);
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

        Loggers.CORE.info("init node cluster : {}", nodes);

        updateCluster(nodes);
    }

    private void updateCluster(Collection<Member> members) {
        memberManager.memberJoin(members);
    }

}
