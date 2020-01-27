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
import com.alibaba.nacos.core.cluster.ClusterConfConstants;
import com.alibaba.nacos.core.cluster.Node;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerNode;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.file.WatchFileManager;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.SystemUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.core.utils.SystemUtils.LOCAL_IP;
import static com.alibaba.nacos.core.utils.SystemUtils.STANDALONE_MODE;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class SyncNodeTask extends Task {

    private volatile int addressServerFailCount = 0;
    private int maxFailCount = 12;

    private NSyncHttpClient httpclient;

    private final ServletContext context;

    public SyncNodeTask(final ServletContext context) {
        WatchFileManager.registerWatchJob(SystemUtils.getConfFilePath(), fileChangeEvent -> readServerConfFromDisk());

        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Integer.parseInt(SpringUtils.getProperty("notifyConnectTimeout", "100")))
                .setSocketTimeout(Integer.parseInt(SpringUtils.getProperty("notifySocketTimeout", "200"))).build();
        this.httpclient = HttpClientManager.newHttpClient(getClass().getCanonicalName(), requestConfig);
        this.maxFailCount = Integer.parseInt(SpringUtils.getProperty("maxHealthCheckFailCount", "12"));

        this.context = context;

        readServerConfFromDisk();
    }

    @Override
    protected void executeBody() {
        if (nodeManager.getUseAddressServer()) {
            try {
                ResResult<String> resResult = httpclient.get(nodeManager.getAddressServerUrl(), Header.EMPTY,
                        Query.EMPTY, new TypeReference<ResResult<String>>() {
                });
                if (HttpServletResponse.SC_OK == resResult.getCode()) {
                    nodeManager.setAddressServerHealth(true);

                    Properties conf = new Properties();

                    conf.load(new StringReader(resResult.getData()));

                    readServerConf(conf);

                    addressServerFailCount = 0;

                } else {
                    addressServerFailCount++;
                    if (addressServerFailCount >= maxFailCount) {
                        nodeManager.setAddressServerHealth(false);
                    }
                    Loggers.CORE.error("[serverlist] failed to get serverlist, error code {}", resResult.getCode());
                    nodeManager.nodeJoin(Collections.emptyList());
                }
            } catch (Exception e) {
                addressServerFailCount++;
                if (addressServerFailCount >= maxFailCount) {
                    nodeManager.setAddressServerHealth(false);
                }
                Loggers.CORE.error("[serverlist] exception, " + e.toString(), e);
                nodeManager.nodeJoin(Collections.emptyList());
            }
        }
    }

    private void readServerConfFromDisk() {
        try(Reader reader = new InputStreamReader(new FileInputStream(new File(SystemUtils.CLUSTER_CONF_FILE_PATH)),
                StandardCharsets.UTF_8)) {

            Properties properties = new Properties();
            properties.load(reader);

            readServerConf(properties);
        } catch (Exception e) {
            Loggers.CORE.error("nacos-XXXX", "[serverlist] failed to get serverlist from disk!", e);
        }
    }

    private void readServerConf(Properties properties) {
            Set<Node> nodes = new HashSet<>();
            boolean needStop = false;

            for (int i = 0; !needStop; i ++) {
                final String nodeAddressKey = String.format(ClusterConfConstants.NODE_ADDRESS, i);
                String nodeAddressValue = properties.getProperty(nodeAddressKey);
                if (StringUtils.isBlank(nodeAddressValue)) {
                    needStop = true;

                    // 意味着单机模式

                    if (i == 0 && STANDALONE_MODE) {
                        nodeAddressValue = LOCAL_IP;
                        if (!nodeAddressValue.contains(":")) {
                            nodeAddressValue += ":" + SpringUtils.getProperty("server.port","8848");
                        }
                    }
                }
                final String[] nodeAddressInfo = nodeAddressValue.split(":");
                final String ip = nodeAddressInfo[0].trim();
                final int port = Integer.parseInt(nodeAddressInfo[1].trim());
                final String nodeExtendInfoPrefix = String.format(ClusterConfConstants.NODE_EXTEND_DATA, i);
                Map<String, String> extendInfo = new HashMap<>(8);
                Iterator<Map.Entry<Object, Object>> iterator = properties.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Object, Object> entry = iterator.next();
                    final String key = String.valueOf(entry.getKey());
                    if (key.startsWith(nodeExtendInfoPrefix)) {
                        extendInfo.put(key, String.valueOf(entry.getValue()));
                        iterator.remove();
                    }
                }
                nodes.add(ServerNode.builder()
                        .ip(ip)
                        .port(port)
                        .extendInfo(extendInfo)
                        .state(NodeState.UP)
                        .build());
            }

            nodeManager.nodeJoin(nodes);
    }

    @Override
    public TaskType[] types() {
        return new TaskType[]{TaskType.NOW_THREAD, TaskType.SCHEDULE_TASK};
    }

    @Override
    public TaskInfo scheduleInfo() {
        return new TaskInfo(0L, 5L, TimeUnit.SECONDS);
    }

}
