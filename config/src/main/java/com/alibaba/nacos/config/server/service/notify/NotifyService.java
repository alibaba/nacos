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
package com.alibaba.nacos.config.server.service.notify;

import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.NSyncHttpClient;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.core.cluster.MemberManager;
import org.apache.http.client.config.RequestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通知其他节点取最新数据的服务。 监听数据变更事件，通知所有的server。
 *
 * @author jiuRen
 */
@Component
public class NotifyService {

    /**
     * 和其他server的连接超时和socket超时
     */
    private static final int TIMEOUT = 500;

    private TaskManager notifyTaskManager;

    private static NSyncHttpClient syncHttpClient;

    static {
        syncHttpClient = HttpClientManager.newHttpClient(NotifyService.class.getCanonicalName(), RequestConfig.custom()
                .setConnectTimeout(TIMEOUT)
                .setSocketTimeout(TIMEOUT)
                .build());
    }

    @Autowired
    public NotifyService(MemberManager memberManager) {
        notifyTaskManager = new TaskManager("com.alibaba.nacos.NotifyTaskManager");
        notifyTaskManager.setDefaultTaskProcessor(new NotifyTaskProcessor(memberManager));
    }

    /**
     * 為了方便系统beta，不改变notify.do接口，新增lastModifed参数通过Http header传递
     */
    static public final String NOTIFY_HEADER_LAST_MODIFIED = "lastModified";
    static public final String NOTIFY_HEADER_OP_HANDLE_IP = "opHandleIp";

    static public <T> ResResult<T> invokeURL(final String url,
                                             final List<String> headers,
                                             final String encoding,
                                             final TypeReference<ResResult<T>> reference) throws Exception {
        final Header header = Header.newInstance();
        header.addAll(headers);
        header.addParam("Content-Type", "application/x-www-form-urlencoded;charset=" + encoding);
        return syncHttpClient.get(url, header, Query.EMPTY, reference);
    }

}
