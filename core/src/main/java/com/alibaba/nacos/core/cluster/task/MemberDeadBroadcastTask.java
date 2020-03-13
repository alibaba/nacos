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
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.HttpRestResult;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberDeadBroadcastTask extends Task {

    private final TypeReference<RestResult<String>> reference
            = new TypeReference<RestResult<String>>() {
    };

    public MemberDeadBroadcastTask(ServerMemberManager memberManager) {
        super(memberManager);
    }

    @Override
    protected void executeBody() {
        Collection<Member> members = memberManager.allMembers();

        Collection<Member> waitRemove = new ArrayList<>();

        members.forEach(member -> {
            if (member.getState() == NodeState.DOWN && member.getFailAccessCnt() > 6) {
                waitRemove.add(member);
            }
        });

        for (Member member : MemberUtils.kRandom(memberManager, member -> {
            NodeState state = member.state();
            return state != NodeState.DOWN;
        })) {
            final String url = "http://" + member.address() + memberManager.getContextPath() +
                    Commons.NACOS_CORE_CONTEXT + "/cluster/server/leave";
            asyncHttpClient.post(url, Header.EMPTY, Query.EMPTY, waitRemove, reference, new Callback<String>() {
                @Override
                public void onReceive(HttpRestResult<String> result) {
                    if (result.ok()) {
                        Loggers.CLUSTER.debug("The node : [{}] success to process the request", member);
                    } else {
                        Loggers.CLUSTER.warn("The node : [{}] failed to process the request, response is : {}", member, result);
                    }
                    MemberUtils.onSuccess(member, memberManager);
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
        GlobalExecutor.scheduleBroadCastJob(this, 10_000L);
    }
}
