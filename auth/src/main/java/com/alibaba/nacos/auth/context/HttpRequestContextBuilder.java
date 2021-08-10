package com.alibaba.nacos.auth.context;

import com.alibaba.nacos.auth.AuthConfig;

import javax.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class HttpRequestContextBuilder implements RequestContextBuilder<HttpServletRequest> {
    
    /**
     * get auth context from http.
     *
     * @param request user request
     * @return AuthContext request context
     */
    @Override
    public RequestContext build(HttpServletRequest request) {
        RequestContext authContext = new RequestContext();
        Set<String> keySet = new HashSet<String>(Arrays.asList(AuthConfig.AUTHORITY_KEY));
        if ("HEADER".equals(AuthConfig.AUTHORIZATION_REGION)) {
            Enumeration<String> enu = request.getHeaderNames();
            while (enu.hasMoreElements()) {
                String paraName =  enu.nextElement();
                if (keySet.contains(paraName)) {
                    authContext.setParameter(paraName, request.getParameter(paraName));
                }
            }
        } else if ("PARAMETER".equals(AuthConfig.AUTHORIZATION_REGION)) {
            Enumeration<String> enu = request.getParameterNames();
            while (enu.hasMoreElements()) {
                String paraName =  enu.nextElement();
                if (keySet.contains(paraName)) {
                    authContext.setParameter(paraName, request.getParameter(paraName));
                }
            }
        } else {
            Enumeration<String> enu1 = request.getParameterNames();
            while (enu1.hasMoreElements()) {
                String paraName =  enu1.nextElement();
                if (keySet.contains(paraName)) {
                    authContext.setParameter(paraName, request.getParameter(paraName));
                }
            }
            Enumeration<String> enu2 = request.getHeaderNames();
            while (enu2.hasMoreElements()) {
                String paraName =  enu2.nextElement();
                if (keySet.contains(paraName)) {
                    authContext.setParameter(paraName, request.getParameter(paraName));
                }
            }
        }
        return authContext;
    }
}