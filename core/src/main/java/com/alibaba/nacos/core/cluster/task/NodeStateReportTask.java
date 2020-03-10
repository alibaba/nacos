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
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.ResResultUtils;
import com.alibaba.nacos.core.utils.SpringUtils;
import java.util.Objects;
import org.apache.http.client.config.RequestConfig;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NodeStateReportTask extends Task {

    private final TypeReference<ResResult<String>> reference
            = new TypeReference<ResResult<String>>() {
    };

    private NAsyncHttpClient httpClient;

    public NodeStateReportTask() {
        final RequestConfig requestConfig = RequestConfig.custom()
                // Time in milliseconds
                .setConnectTimeout(Integer.parseInt(SpringUtils.getProperty("notifyConnectTimeout", "10000")))
                .setSocketTimeout(Integer.parseInt(SpringUtils.getProperty("notifySocketTimeout", "20000")))
                .build();

        this.httpClient = HttpClientManager
                .newAsyncHttpClient(ServerMemberManager.class.getCanonicalName(), requestConfig);
    }

    @Override
    public void executeBody() {

        // If the cluster is self-discovering, there is no need to broadcast self-information

        if (SpringUtils.getProperty("nacos.core.member.self-discovery", Boolean.class, false)) {
            GlobalExecutor.scheduleCleanJob(this::executeBody, 5_000L);
            return;
        }

        try {
            long startCheckTime = System.currentTimeMillis();

            final Member self = memberManager.self();

            // self node information is not ready

            if (!self.check()) {
                return;
            }

            int weight = Runtime.getRuntime().availableProcessors() / 2;
            if (weight <= 0) {
                weight = 1;
            }

            self.setExtendVal(Member.WEIGHT, String.valueOf(weight));

            memberManager.update(self);

            for (Member member : memberManager.allMembers()) {

                // local node or node check failed will not perform task processing

                if (Objects.equals(self, member) || !member.check()) {
                    continue;
                }

                // Compatible with old codes,use status.taobao

                String url = "http://" + member.address() + memberManager.getContextPath() +
                        Commons.NACOS_CORE_CONTEXT + "/cluster/server/report";

                // "/nacos/server/report";

                httpClient.post(url, Header.EMPTY, Query.EMPTY
                        , ResResultUtils.success(self), reference, new Callback<String>() {
                            @Override
                            public void onReceive(HttpResResult<String> result) {
                                if (result.ok()) {
                                    Loggers.CORE.debug("Successfully synchronizing information to node : {}," +
                                            " result : {}", member, result);

                                } else {
                                    Loggers.CORE.warn("An exception occurred while reporting their " +
                                            "information to the node : {}, error : {}", member.address(), result.getErrMsg());
                                }
                                memberManager.getServerListUnHealth().remove(member);
                                memberManager.getLastRefreshTimeRecord().put(member.address(), System.currentTimeMillis());
                            }

                            @Override
                            public void onError(Throwable e) {
                                Loggers.CORE.error("An exception occurred while reporting their " +
                                        "information to the node : {}, error : {}", member.address(), e);
                            }
                        });
            }
            long endCheckTime = System.currentTimeMillis();
            long cost = endCheckTime - startCheckTime;
            Loggers.CORE.debug("task report job cost: {}", cost);
        } catch (Exception e) {
            Loggers.CORE.error("node state report task has error : {}", ExceptionUtil.getAllExceptionMsg(e));
        }
        GlobalExecutor.scheduleCleanJob(this::executeBody, 5_000L);
    }

}
