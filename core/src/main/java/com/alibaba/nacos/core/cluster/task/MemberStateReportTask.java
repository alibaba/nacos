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
import com.alibaba.nacos.common.http.NAsyncHttpClient;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.HttpRestResult;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.RestResultUtils;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.TimerContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberStateReportTask extends Task {

    private final TypeReference<RestResult<String>> reference
            = new TypeReference<RestResult<String>>() {
    };

    private final TypeReference<Collection<Member>> memberReference = new TypeReference<Collection<Member>>() {
    };

    private NAsyncHttpClient httpClient;

    public MemberStateReportTask(ServerMemberManager memberManager) {
        super(memberManager);
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

        boolean discovery = SpringUtils.getProperty("nacos.core.member.self-discovery", Boolean.class, false);

        TimerContext.start("MemberStateReportTask");
        try {
            final Member self = memberManager.self();
            // self node information is not ready
            if (!self.check()) {
                return;
            }

            for (Member member : kRandomMember()) {
                final Query query = Query.newInstance().addParam("sync", discovery);

                // If the cluster self-discovery is turned on, the information is synchronized with the node

                String url = "http://" + member.address() + memberManager.getContextPath() +
                        Commons.NACOS_CORE_CONTEXT + "/cluster/server/report";

                httpClient.post(url, Header.EMPTY, query, RestResultUtils.success(self), reference, new Callback<String>() {
                    @Override
                    public void onReceive(HttpRestResult<String> result) {
                        if (result.ok()) {
                            Loggers.CLUSTER.debug("Successfully synchronizing information to node : {}, sync : {}," +
                                    " result : {}", discovery, member, result);

                            final String data = result.getData();
                            if (StringUtils.isNotBlank(data)) {
                                discovery(data);
                            }

                        } else {
                            Loggers.CLUSTER.warn("An exception occurred while reporting their " +
                                    "information to the node : {}, error : {}", member.address(), result.getMessage());
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        Loggers.CLUSTER.error("An exception occurred while reporting their " +
                                "information to the node : {}, error : {}", member.address(), e);
                    }
                });
            }
        } catch (Exception e) {
            Loggers.CLUSTER.error("node state report task has error : {}", ExceptionUtil.getAllExceptionMsg(e));
        } finally {
            TimerContext.end(Loggers.CLUSTER);
        }
    }

    @Override
    protected void after() {
        GlobalExecutor.scheduleCleanJob(this, 5_000L);
    }

    private List<Member> kRandomMember() {
        int k = SpringUtils.getProperty("nacos.core.member.report.random-num", Integer.class, 3);

        List<Member> members = new ArrayList<>();
        Collection<Member> have = memberManager.allMembers();

        // Here thinking similar consul gossip protocols random k node

        int totalSize = have.size();
        for (int i = 0; i < 3 * totalSize && members.size() <= k; i++) {
            for (Member member : have) {

                // local node or node check failed will not perform task processing
                if (memberManager.isSelf(member) || !member.check()) {
                    continue;
                }

                NodeState state = member.state();
                if (state == NodeState.DOWN || state == NodeState.SUSPICIOUS) {
                    continue;
                }
                members.add(member);
            }
        }

        return members;
    }

    private void discovery(String result) {
        try {
            Collection<Member> members = JSON.parseObject(result, memberReference);
            memberManager.memberJoin(members);
        } catch (Exception e) {
            Loggers.CLUSTER.error("The cluster self-detects a problem");
        }
    }
}
