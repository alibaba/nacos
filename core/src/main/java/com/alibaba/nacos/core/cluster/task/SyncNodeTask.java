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
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.NSyncHttpClient;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.core.cluster.Node;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerNode;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.file.FileChangeEvent;
import com.alibaba.nacos.core.file.FileWatcher;
import com.alibaba.nacos.core.file.WatchFileCenter;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.SystemUtils;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.UndefineMagicConstantRule")
public class SyncNodeTask extends Task {

    private volatile int addressServerFailCount = 0;
    private int maxFailCount = 12;

    private NSyncHttpClient httpclient;

    private final ServletContext context;

    private volatile boolean alreadyLoadServer = false;

    public SyncNodeTask(final ServletContext context) {
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Integer.parseInt(SpringUtils.getProperty("notifyConnectTimeout", "100")))
                .setSocketTimeout(Integer.parseInt(SpringUtils.getProperty("notifySocketTimeout", "200"))).build();
        this.httpclient = HttpClientManager.newHttpClient(getClass().getCanonicalName(), requestConfig);
        this.maxFailCount = Integer.parseInt(SpringUtils.getProperty("maxHealthCheckFailCount", "12"));

        this.context = context;
    }

    @Override
    protected void init() {
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

    @Override
    protected void executeBody() {

        // Whether to enable the node self-discovery function that comes with nacos

        if (SystemUtils.NODE_SELF_DISCOVERY) {
            syncBySelfDiscovery();
        } else {
            syncFromAddressUrl();
        }
    }

    private void syncBySelfDiscovery() {
        List<Node> nodes = nodeManager.allNodes();
        for (Node node : nodes) {
            final String url = "http://" + node.address() + "/" + context.getContextPath() + Commons.NACOS_CORE_CONTEXT + "/cluster/nodes";

            try {
                ResResult<Collection<Node>> result = httpclient.get(url, Header.EMPTY,
                        Query.EMPTY, new TypeReference<ResResult<Collection<Node>>>() {
                        });

                if (result.ok()) {

                    Collection<Node> remoteNodes = result.getData();
                    updateCluster(remoteNodes);

                } else {
                    Loggers.CORE.error("[serverlist] failed to get serverlist from server : {}, error : {}", node.address(), result);
                }

            } catch (Exception e) {
                Loggers.CORE.error("[serverlist] exception, " + e.toString(), e);
            }
        }
    }

    private void syncFromAddressUrl() {
        if (!alreadyLoadServer && nodeManager.getUseAddressServer()) {
            try {
                ResResult<String> resResult = httpclient.get(nodeManager.getAddressServerUrl(), Header.EMPTY,
                        Query.EMPTY, new TypeReference<ResResult<String>>() {
                        });
                if (HttpServletResponse.SC_OK == resResult.getCode()) {
                    nodeManager.setAddressServerHealth(true);

                    Reader reader = new StringReader(resResult.getData());

                    readServerConf(SystemUtils.analyzeClusterConf(reader));

                    addressServerFailCount = 0;

                } else {
                    addressServerFailCount++;
                    if (addressServerFailCount >= maxFailCount) {
                        nodeManager.setAddressServerHealth(false);
                    }
                    Loggers.CORE.error("[serverlist] failed to get serverlist, error code {}", resResult.getCode());
                }
            } catch (Exception e) {
                addressServerFailCount++;
                if (addressServerFailCount >= maxFailCount) {
                    nodeManager.setAddressServerHealth(false);
                }
                Loggers.CORE.error("[serverlist] exception, " + e.toString(), e);
            }
        }
    }

    private void readServerConfFromDisk() {
        try {
            List<String> members = SystemUtils.readClusterConf();
            readServerConf(members);
            alreadyLoadServer = true;
        } catch (Exception e) {
            Loggers.CORE.error("nacos-XXXX", "[serverlist] failed to get serverlist from disk!", e);
            alreadyLoadServer = false;
        }
    }

    // 默认配置格式解析，只有nacos-server的ip:port or hostname:port 信息

    // example 192.168.16.1:8848?raft_port=8849&

    private void readServerConf(List<String> members) {
        Set<Node> nodes = new HashSet<>();
        int defaultPort = nodeManager.getPort();

        // Set the default Raft port information for security

        int defaultRaftPort = defaultPort + 1 > 65536 ? defaultPort + 1 : defaultPort - 1;

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
            }

            nodes.add(ServerNode.builder()
                    .ip(address)
                    .port(port)
                    .extendInfo(extendInfo)
                    .state(NodeState.UP)
                    .build());

        }

        Loggers.CORE.info("init node cluster : {}", nodes);

        updateCluster(nodes);
    }

    private void updateCluster(Collection<Node> nodes) {
        nodeManager.nodeJoin(nodes);
    }

    @Override
    public TaskType[] types() {
        return new TaskType[]{TaskType.NOW_THREAD, TaskType.SCHEDULE_TASK};
    }

    @Override
    public TaskInfo scheduleInfo() {
        return new TaskInfo(0L, 30L, TimeUnit.SECONDS);
    }

}
