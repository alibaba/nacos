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
import com.alibaba.nacos.common.model.HttpResResult;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.cluster.Node;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NodeHealthTask extends Task {

    private NAsyncHttpClient httpClient;

    private int maxFailCount = 12;

    public NodeHealthTask() {
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Integer.parseInt(SpringUtils.getProperty("notifyConnectTimeout", "100")))
                .setSocketTimeout(Integer.parseInt(SpringUtils.getProperty("notifySocketTimeout", "200"))).build();
        this.httpClient = HttpClientManager.newAsyncHttpClient(ServerNodeManager.class.getCanonicalName(), requestConfig);
        this.maxFailCount = Integer.parseInt(SpringUtils.getProperty("maxHealthCheckFailCount", "12"));
    }

    @Override
    protected void executeBody() {
        long startCheckTime = System.currentTimeMillis();
        for (Node node : nodeManager.getServerListHealth()) {

            // Compatible with old codes,use status.taobao

            String url = "http://" + node.address() + nodeManager.getServletContext().getContextPath() + "/health";

            // "/nacos/health";

            httpClient.get(url, Header.EMPTY, Query.EMPTY, new TypeReference<ResResult<String>>() {
            }, new AsyncCheckServerHealthCallBack(node));
        }
        long endCheckTime = System.currentTimeMillis();
        long cost = endCheckTime - startCheckTime;
        Loggers.CORE.debug("checkServerHealth cost: {}", cost);
    }

    class AsyncCheckServerHealthCallBack implements Callback<String> {

        private Node node;

        public AsyncCheckServerHealthCallBack(Node node) {
            this.node = node;
        }

        private void computeFailCount() {
            int failCount = nodeManager.getServerIp2UnHealthCount().compute(node.address(), (key, oldValue) -> oldValue == null ? 1 : oldValue + 1);
            if (failCount > maxFailCount) {
                if (!nodeManager.getServerListUnHealth().contains(node)) {
                    nodeManager.getServerListUnHealth().add(node);
                }
                Loggers.CORE.error("unHealthNode:{}, unHealthCount:{}", node, failCount);
            }
        }

        @Override
        public void onReceive(HttpResResult<String> result) {
            if (result.getCode() == HttpStatus.SC_OK) {
                nodeManager.getServerListUnHealth().remove(node);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            computeFailCount();
        }
    }

    @Override
    public TaskType[] types() {
        return new TaskType[]{TaskType.SCHEDULE_TASK};
    }

    @Override
    public TaskInfo scheduleInfo() {
        return new TaskInfo(0L, 5L, TimeUnit.SECONDS);
    }
}
