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
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.Commons;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberShutdownTask extends Task {

    private NSyncHttpClient httpClient;

    private final TypeReference<ResResult<String>> typeReference = new TypeReference<ResResult<String>>(){};

    public MemberShutdownTask() {
        this.httpClient = HttpClientManager.newHttpClient(MemberShutdownTask.class.getCanonicalName());
    }

    @Override
    public void executeBody() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            final Query query = Query.newInstance()
                    .addParam("member", memberManager.self().address());

            memberManager.allMembers().forEach(member -> {

                final String url = "http://" + member.address() + memberManager.getContextPath() +
                        Commons.NACOS_CORE_CONTEXT + "/cluster/server/leave";

                try {
                    httpClient.delete(url, Header.EMPTY, query, typeReference);
                } catch (Exception ignore) {

                }
            });

        }));
    }
}
