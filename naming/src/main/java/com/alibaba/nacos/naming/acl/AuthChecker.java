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
package com.alibaba.nacos.naming.acl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.security.AccessControlException;
import java.util.Map;

/**
 * @author nkorange
 */
@Component
public class AuthChecker {

    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private SwitchDomain switchDomain;

    static private String[] APP_WHITE_LIST = {};
    static private String[] TOKEN_WHITE_LIST = {"traffic-scheduling@midware"};

    public void doRaftAuth(HttpServletRequest req) throws Exception {
        String token = req.getParameter("token");
        if (StringUtils.equals(UtilsAndCommons.SUPER_TOKEN, token)) {
            return;
        }

        String agent = req.getHeader("Client-Version");
        if (StringUtils.startsWith(agent, UtilsAndCommons.NACOS_SERVER_HEADER)) {
            return;
        }

        agent = req.getHeader("User-Agent");
        if (StringUtils.startsWith(agent, UtilsAndCommons.NACOS_SERVER_HEADER)) {
            return;
        }

        throw new IllegalAccessException("illegal access,agent= " + agent + ", token=" + token);
    }

    public void doAuth(Map<String, String[]> params, HttpServletRequest req) throws Exception {

        String namespaceId = WebUtils.optional(req, CommonParams.NAMESPACE_ID,
            Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.optional(req, "name", "");
        if (StringUtils.isEmpty(serviceName)) {
            serviceName = WebUtils.optional(req, "serviceName", "");
        }

        if (StringUtils.isEmpty(serviceName)) {
            serviceName = WebUtils.optional(req, "tag", "");
        }

        Service service = serviceManager.getService(namespaceId, serviceName);

        if (service == null) {
            if (!req.getRequestURI().equals(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.API_SET_ALL_WEIGHTS)) {
                throw new IllegalStateException("auth failed, service does not exist: " + serviceName);
            }
        }

        String token = req.getParameter("token");
        String auth = req.getParameter("auth");
        String userName = req.getParameter("userName");
        if (StringUtils.isEmpty(auth) && StringUtils.isEmpty(token)) {
            throw new IllegalArgumentException("provide 'authInfo' or 'token' to access this service");
        }

        // try valid token
        if ((service != null && StringUtils.equals(service.getToken(), token))) {
            return;
        }

        if (ArrayUtils.contains(TOKEN_WHITE_LIST, token)) {
            return;
        }

        if (ArrayUtils.contains(APP_WHITE_LIST, userName)) {
            return;
        }

        // if token failed, try AuthInfo
        AuthInfo authInfo = AuthInfo.fromString(auth, WebUtils.getAcceptEncoding(req));
        if (authInfo == null) {
            throw new IllegalAccessException("invalid token or malformed auth info");
        }

        if (!ArrayUtils.contains(APP_WHITE_LIST, authInfo.getAppKey())) {
            throw new AccessControlException("un-registered SDK app");
        }

        if (!service.getOwners().contains(authInfo.getOperator())
            && !switchDomain.getMasters().contains(authInfo.getOperator())) {
            throw new AccessControlException("dom already exists and you're not among the owners");
        }
    }
}
