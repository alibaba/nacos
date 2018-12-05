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
package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.naming.acl.AuthChecker;
import com.alibaba.nacos.naming.misc.Switch;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.AccessControlException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class AuthFilter implements Filter {

    @Autowired
    private AuthChecker authChecker;

    private static ConcurrentMap<String, Method> methodCache = new
            ConcurrentHashMap<String, Method>();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;

        try {
            String path = new URI(req.getRequestURI()).getPath();
            String target = getMethodName(path);

            Method method = methodCache.get(target);

            if (method == null) {
                if (path.contains(UtilsAndCommons.NACOS_NAMING_RAFT_CONTEXT)) {
                    method = RaftCommands.class.getMethod(target, HttpServletRequest.class, HttpServletResponse.class);
                } else {
                    method = ApiCommands.class.getMethod(target, HttpServletRequest.class);
                }
                methodCache.put(target, method);
            }

            if (method.isAnnotationPresent(NeedAuth.class) && !Switch.isEnableAuthentication()) {

                if (path.contains(UtilsAndCommons.NACOS_NAMING_RAFT_CONTEXT)) {
                    authChecker.doRaftAuth(req);
                } else {
                    authChecker.doAuth(req.getParameterMap(), req);
                }
            }

        } catch (AccessControlException e) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "access denied: " + UtilsAndCommons.getAllExceptionMsg(e));
            return;
        } catch (NoSuchMethodException e) {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "no such api");
            return;
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Server failed," + UtilsAndCommons.getAllExceptionMsg(e));
            return;
        }
        filterChain.doFilter(req, resp);
    }

    @Override
    public void destroy() {

    }

    static protected String getMethodName(String path) throws Exception {
        String target = path.substring(path.lastIndexOf("/") + 1).trim();

        if (StringUtils.isEmpty(target)) {
            throw new IllegalArgumentException("URL target required");
        }

        return target;
    }
}
