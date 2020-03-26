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
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.Loggers;
import java.util.Collection;
import java.util.Collections;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberShutdownTask extends Task {

    private NSyncHttpClient httpClient;

    private final TypeReference<RestResult<String>> typeReference = new TypeReference<RestResult<String>>() {
    };

    public MemberShutdownTask(ServerMemberManager memberManager) {
        super(memberManager);
        this.httpClient = HttpClientManager.newHttpClient(ServerMemberManager.class.getCanonicalName());
    }

    @Override
    public void run() {
        try {
            executeBody();
        } finally {
            after();
        }
    }

    @Override
    public void executeBody() {
        Collection<Member> body = Collections.singletonList(memberManager.self());

        Loggers.CLUSTER.info("Start broadcasting this node logout");

        memberManager.allMembers().forEach(member -> {

            final String url = "http://" + member.getAddress() + memberManager.getContextPath() +
                    Commons.NACOS_CORE_CONTEXT + "/cluster/server/leave";

            try {
                RestResult<String> result = httpClient.post(url, Header.EMPTY, Query.EMPTY, body, typeReference);
                Loggers.CLUSTER.info("{} the response of the target node to this logout operation : {}", member, result);
            } catch (Exception e) {
                Loggers.CLUSTER.error("shutdown execute has error : {}", e);
            }
        });
    }

    @Override
    protected void after() {
        try {
            httpClient.close();
        } catch (Exception ignore) {

        }
    }
}
