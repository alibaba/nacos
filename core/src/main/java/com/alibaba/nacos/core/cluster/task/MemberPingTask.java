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
import com.alibaba.nacos.common.http.handler.ResponseHandler;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.common.utils.TimerContext;
import java.util.Collection;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

/**
 * This task is responsible for randomly communicating with an UP
 * node to obtain the latest data information of the node
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberPingTask extends Task {

    private final GenericType<RestResult<String>> reference
            = new GenericType<RestResult<String>>() {
    };

    private final GenericType<Collection<String>> memberReference = new GenericType<Collection<String>>() {
    };

    public MemberPingTask(ServerMemberManager memberManager) {
        super(memberManager);
    }

    @Override
    public void executeBody() {

        // If the cluster is self-discovering, there is no need to broadcast self-information

        boolean discovery = ApplicationUtils
				.getProperty("nacos.core.member.self-discovery", Boolean.class, false);

        TimerContext.start("MemberPingTask");
        try {
            final Member self = memberManager.getSelf();
            // self node information is not ready
            if (!self.check()) {
                return;
            }

            for (Member member : MemberUtils.kRandom(memberManager, member -> {
                // local node or node check failed will not perform task processing
                if (memberManager.isSelf(member) || !member.check()) {
                    return false;
                }
                NodeState state = member.getState();
                return !(state == NodeState.DOWN || state == NodeState.SUSPICIOUS);
            })) {
                final Query query = Query.newInstance().addParam("sync", discovery);

                // If the cluster self-discovery is turned on, the information is synchronized with the node

                String url = "http://" + member.getAddress() + memberManager.getContextPath() +
                        Commons.NACOS_CORE_CONTEXT + "/cluster/server/report";

                if (shutdown) {
                    return;
                }

                asyncHttpClient.post(url, Header.EMPTY, query, self, reference.getType(), new Callback<String>() {
                    @Override
                    public void onReceive(RestResult<String> result) {
                        if (result.ok()) {
                            Loggers.CLUSTER.debug("success ping to node : {}, sync : {}," +
                                    " result : {}", member, discovery, result);

                            final String data = result.getData();
                            if (StringUtils.isNotBlank(data)) {
                                discovery(data);
                            }
                            MemberUtils.onSuccess(member, memberManager);
                        } else {
                            Loggers.CLUSTER.warn("An exception occurred while reporting their " +
                                    "information to the node : {}, error : {}", member.getAddress(), result.getMessage());
                            MemberUtils.onFail(member, memberManager);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        Loggers.CLUSTER.error("An exception occurred while reporting their " +
                                "information to the node : {}, error : {}", member.getAddress(), e);
                        MemberUtils.onFail(member, memberManager);
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
        GlobalExecutor.schedulePingJob(this, 5_000L);
    }

    private void discovery(String result) {
        try {
            Collection<String> members = ResponseHandler.convert(result, memberReference.getType());
            MemberUtils.readServerConf(Objects.requireNonNull(members), memberManager);
        } catch (Exception e) {
            Loggers.CLUSTER.error("The cluster self-detects a problem");
        }
    }
}
