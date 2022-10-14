/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * <p>
 * collect client attributes for 1.x.
 * </p>
 *
 * @author hujun
 */
public class ClientAttributesFilter implements Filter {
    
    private static final String BEAT_URI = "/beat";
    
    private static final String IP = "ip";
    
    private static final String PORT = "port";
    
    private static final String ZERO = "0";
    
    @Autowired
    private ClientManager clientManager;
    
    public static ThreadLocal<ClientAttributes> threadLocalClientAttributes = new ThreadLocal<>();
    
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        try {
            try {
                if ((UtilsAndCommons.NACOS_SERVER_CONTEXT + UtilsAndCommons.NACOS_NAMING_CONTEXT
                        + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT).equals(request.getRequestURI())
                        && request.getMethod().equals(HttpMethod.POST)) {
                    //register
                    ClientAttributes requestClientAttributes = getClientAttributes(request);
                    threadLocalClientAttributes.set(requestClientAttributes);
                } else if ((UtilsAndCommons.NACOS_SERVER_CONTEXT + UtilsAndCommons.NACOS_NAMING_CONTEXT
                        + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT + BEAT_URI).equals(request.getRequestURI())) {
                    //beat
                    String ip = WebUtils.optional(request, IP, StringUtils.EMPTY);
                    int port = Integer.parseInt(WebUtils.optional(request, PORT, ZERO));
                    String clientId = IpPortBasedClient.getClientId(ip + InternetAddressUtil.IP_PORT_SPLITER + port,
                            true);
                    IpPortBasedClient client = (IpPortBasedClient) clientManager.getClient(clientId);
                    if (client != null) {
                        ClientAttributes requestClientAttributes = getClientAttributes(request);
                        //update clientAttributes,when client version attributes is null,then update.
                        if (canUpdateClientAttributes(client, requestClientAttributes)) {
                            client.setAttributes(requestClientAttributes);
                        }
                    }
                }
            } catch (Exception e) {
                Loggers.SRV_LOG.error("handler client attributes error", e);
            }
            try {
                filterChain.doFilter(request, servletResponse);
            } catch (ServletException e) {
                throw new RuntimeException(e);
            }
        } finally {
            if (threadLocalClientAttributes.get() != null) {
                threadLocalClientAttributes.remove();
            }
        }
    }
    
    private static boolean canUpdateClientAttributes(IpPortBasedClient client,
            ClientAttributes requestClientAttributes) {
        if (requestClientAttributes.getClientAttribute(HttpHeaderConsts.CLIENT_VERSION_HEADER) == null) {
            return false;
        }
        if (client.getClientAttributes() != null
                && client.getClientAttributes().getClientAttribute(HttpHeaderConsts.CLIENT_VERSION_HEADER) != null) {
            return false;
        }
        return true;
    }
    
    public static ClientAttributes getClientAttributes(HttpServletRequest request) {
        String version = request.getHeader(HttpHeaderConsts.CLIENT_VERSION_HEADER);
        String app = request.getHeader(HttpHeaderConsts.APP_FILED);
        String clientIp = request.getRemoteAddr();
        ClientAttributes clientAttributes = new ClientAttributes();
        if (version != null) {
            clientAttributes.addClientAttribute(HttpHeaderConsts.CLIENT_VERSION_HEADER, version);
        }
        if (app != null) {
            clientAttributes.addClientAttribute(HttpHeaderConsts.APP_FILED, app);
        }
        if (clientIp != null) {
            clientAttributes.addClientAttribute(HttpHeaderConsts.CLIENT_IP, clientIp);
        }
        return clientAttributes;
    }
}
