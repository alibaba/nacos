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
package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;

/**
 * Unified filter to handle authentication and authorization
 *
 * @author nkorange
 * @since 1.2.0
 */
public class AuthFilter implements Filter {

    @Autowired
    private AuthConfigs authConfigs;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private ControllerMethodsCache methodsCache;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        if (!authConfigs.isAuthEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (Loggers.AUTH.isDebugEnabled()) {
            Loggers.AUTH.debug("auth filter start, request: {}", req.getRequestURI());
        }

        try {

            String path = new URI(req.getRequestURI()).getPath();
            Method method = methodsCache.getMethod(req.getMethod(), path);

            if (method == null) {
                throw new NoSuchMethodException();
            }

            if (method.isAnnotationPresent(Secured.class) && authConfigs.isAuthEnabled()) {

                Secured secured = method.getAnnotation(Secured.class);
                String action = secured.action().toString();
                String name = secured.name();

                if (StringUtils.isBlank(name)) {
                    ResourceParser parser = secured.parser().newInstance();
                    name = parser.parseName(req);
                }

                if (StringUtils.isBlank(name)) {
                    // deny we don't find an valid resource:
                    throw new AccessException("resource name invalid!");
                }

                Resource resource = new Resource(name + Resource.SPLITTER + action);

                authManager.auth(resource, authManager.login(req));
            }

        } catch (AccessException e) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, e.getErrMsg());
            return;
        } catch (NoSuchMethodException e) {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
                "no such api:" + req.getMethod() + ":" + req.getRequestURI());
            return;
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, ExceptionUtil.getAllExceptionMsg(e));
            return;
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server failed," + e.getMessage());
            return;
        }

        chain.doFilter(request, response);
    }
}
