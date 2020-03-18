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
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.HttpRestResult;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import java.util.Random;
import java.util.Set;

/**
 * This task is only responsible for synchronizing the entire cluster's node
 * list information from other nodes, and the node's metadata information is out of sync
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberPullTask extends Task {

    private final TypeReference<RestResult<Member>> reference = new TypeReference<RestResult<Member>>(){};
    private Random random = new Random();

    private int cursor = 0;

    public MemberPullTask(ServerMemberManager memberManager) {
        super(memberManager);
    }

    @Override
    protected void executeBody() {

        boolean discovery = ApplicationUtils
				.getProperty("nacos.core.member.self-discovery", Boolean.class, false);

        if (!discovery) {
            return;
        }

        Set<String> members = memberManager.getMemberAddressInfos();
        this.cursor = (this.cursor + 1) % members.size();
        String[] ss = members.toArray(new String[0]);
        String target = ss[cursor];

        final String url = HttpUtils.buildUrl(false, target, memberManager.getContextPath(), Commons.NACOS_CORE_CONTEXT, "/cluster/self");

        asyncHttpClient.get(url, Header.EMPTY, Query.EMPTY, reference, new Callback<Member>() {
            @Override
            public void onReceive(HttpRestResult<Member> result) {
                if (result.ok()) {
                    Loggers.CLUSTER.info("success pull from node : {}, result : {}", target, result);
                    memberManager.update(result.getData());
                } else {
                    Loggers.CLUSTER.warn("failed to pull new info from target node : {}, result : {}", target, result);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                Loggers.CLUSTER.error("failed to pull new info from target node : {}, error : {}", target, throwable);
                MemberUtils.onFail(MemberUtils.parse(target), memberManager);
            }
        });
    }

    @Override
    protected void after() {
        GlobalExecutor.schedulePullJob(this, 2_000L);
    }
}
