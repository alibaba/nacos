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
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;

import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.AccessControlException;

/**
 * @author nkorange
 */
public class AuthFilter implements Filter {

    @Autowired
    private AuthChecker authChecker;

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private FilterBase filterBase;

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
            Method method = filterBase.getMethod(req.getMethod(), path);

            if (method == null) {
                throw new NoSuchMethodException();
            }

            if (method.isAnnotationPresent(NeedAuth.class) && !switchDomain.isEnableAuthentication()) {

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
}
