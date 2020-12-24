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

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Service to notify other nodes to get the latest data. Monitor data change events and notify all servers.
 *
 * @author jiuRen
 */
public class NotifyService {
    
    @Autowired
    public NotifyService(ServerMemberManager memberManager) {
        notifyTaskManager = new TaskManager("com.alibaba.nacos.NotifyTaskManager");
        notifyTaskManager.setDefaultTaskProcessor(new NotifyTaskProcessor(memberManager));
    }
    
    protected NotifyService() {
    }
    
    /**
     * In order to facilitate the system beta, without changing the notify.do interface, the new lastModifed parameter
     * is passed through the Http header.
     */
    public static final String NOTIFY_HEADER_LAST_MODIFIED = "lastModified";
    
    public static final String NOTIFY_HEADER_OP_HANDLE_IP = "opHandleIp";
    
    private TaskManager notifyTaskManager;
    
    /**
     * Invoke http get request.
     *
     * @param url      url
     * @param headers  headers
     * @param encoding encoding
     * @return {@link com.alibaba.nacos.common.model.RestResult}
     * @throws Exception throw Exception
     */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public static RestResult<String> invokeURL(String url, List<String> headers, String encoding) throws Exception {
        Header header = Header.newInstance();
        header.addParam(HttpHeaderConsts.ACCEPT_CHARSET, encoding);
        if (CollectionUtils.isNotEmpty(headers)) {
            header.addAll(headers);
        }
        return HttpClientManager.getNacosRestTemplate().get(url, header, Query.EMPTY, String.class);
    }
}
