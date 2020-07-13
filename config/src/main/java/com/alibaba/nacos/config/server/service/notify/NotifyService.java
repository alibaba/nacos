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

import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
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
    
    /**
     * Invoke http get request.
     *
     * @param url      url
     * @param headers  headers
     * @param encoding encoding
     * @return {@link HttpResult}
     * @throws IOException throw IOException
     */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public static HttpResult invokeURL(String url, List<String> headers, String encoding) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestMethod("GET");
            
            if (null != headers && !StringUtils.isEmpty(encoding)) {
                for (Iterator<String> iter = headers.iterator(); iter.hasNext(); ) {
                    conn.addRequestProperty(iter.next(), iter.next());
                }
            }
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + encoding);
            // establish TCP connection
            conn.connect();
            // Send request internally
            int respCode = conn.getResponseCode();
            String resp = null;
            
            if (HttpServletResponse.SC_OK == respCode) {
                resp = IoUtils.toString(conn.getInputStream(), encoding);
            } else {
                resp = IoUtils.toString(conn.getErrorStream(), encoding);
            }
            return new HttpResult(respCode, resp);
        } finally {
            IoUtils.closeQuietly(conn);
        }
    }
    
    public static class HttpResult {
        
        public final int code;
        
        public String content;
        
        public HttpResult(int code, String content) {
            this.code = code;
            this.content = content;
        }
    }
    
    /**
     * Connection timeout and socket timeout with other servers.
     */
    static final int TIMEOUT = 500;
    
    private TaskManager notifyTaskManager;
    
}
