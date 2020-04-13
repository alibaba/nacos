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
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Broadcast the node that the local node considers to be DOWN
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberDeadBroadcastTask extends Task {

    private final GenericType<RestResult<String>> reference
            = new GenericType<RestResult<String>>() {
    };

    public MemberDeadBroadcastTask(ServerMemberManager memberManager) {
        super(memberManager);
    }

    @Override
    protected void executeBody() {
        Collection<Member> members = memberManager.allMembers();
        Collection<Member> waitRemove = new ArrayList<>();
        members.forEach(member -> {
            if (NodeState.DOWN.equals(member.getState())) {
                waitRemove.add(member);
            }
        });

        List<Member> waitBroad = MemberUtils.kRandom(memberManager, member -> !NodeState.DOWN.equals(member.getState()));

        for (Member member : waitBroad) {
            final String url = HttpUtils.buildUrl(false, member.getAddress(),
                    memberManager.getContextPath(), Commons.NACOS_CORE_CONTEXT, "/cluster/server/leave");
            if (shutdown) {
                return;
            }

            asyncHttpClient.post(url, Header.EMPTY, Query.EMPTY, waitRemove, reference.getType(), new Callback<String>() {
                @Override
                public void onReceive(RestResult<String> result) {
                    if (result.ok()) {
                        Loggers.CLUSTER.debug("The node : [{}] success to process the request", member);
                        MemberUtils.onSuccess(member, memberManager);
                    } else {
                        Loggers.CLUSTER.warn("The node : [{}] failed to process the request, response is : {}", member, result);
                        MemberUtils.onFail(member, memberManager);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    Loggers.CLUSTER.error("Failed to communicate with the node : {}", member);
                    MemberUtils.onFail(member, memberManager);
                }
            });
        }
    }

    @Override
    protected void after() {
        GlobalExecutor.scheduleBroadCastJob(this, 5_000L);
    }
}
