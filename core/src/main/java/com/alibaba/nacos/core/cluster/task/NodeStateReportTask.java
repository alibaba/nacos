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
import com.alibaba.nacos.common.http.param.Body;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.consistency.cluster.Node;
import com.alibaba.nacos.core.cluster.ServerNodeManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.SystemUtils;
import org.apache.http.client.config.RequestConfig;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NodeStateReportTask extends Task {

    private NSyncHttpClient httpClient;

    public NodeStateReportTask() {
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Integer.parseInt(SpringUtils.getProperty("notifyConnectTimeout", "100")))
                .setSocketTimeout(Integer.parseInt(SpringUtils.getProperty("notifySocketTimeout", "200"))).build();
        this.httpClient = HttpClientManager.newHttpClient(ServerNodeManager.class.getCanonicalName(), requestConfig);
    }

    @Override
    protected void executeBody() {
        long startCheckTime = System.currentTimeMillis();

        final Node self = nodeManager.self();

        int weight = Runtime.getRuntime().availableProcessors() / 2;
        if (weight <= 0) {
            weight = 1;
        }

        self.setExtendVal(Node.WEIGHT, String.valueOf(weight));

        nodeManager.update(self);

        for (Node node : nodeManager.getServerListHealth()) {

            if (node.address().contains(SystemUtils.LOCAL_IP)) {
                continue;
            }

            // Compatible with old codes,use status.taobao

            String url = "http://" + node.address() + nodeManager.getServletContext().getContextPath() + "/server/report";

            // "/nacos/server/report";

            try {
                httpClient.post(url, Header.EMPTY, Query.EMPTY, Body.objToBody(self),
                        new TypeReference<ResResult<String>>() {});
            } catch (Exception e) {

            }
        }
        long endCheckTime = System.currentTimeMillis();
        long cost = endCheckTime - startCheckTime;
        Loggers.CORE.debug("task report job cost: {}", cost);
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
