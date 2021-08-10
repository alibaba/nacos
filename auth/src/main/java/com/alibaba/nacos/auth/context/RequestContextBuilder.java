package com.alibaba.nacos.auth.context;

public interface RequestContextBuilder<T> {
    
    /**
     * build auth context from request.
     * @param request user request
     * @return AuthContext request context
     */
    RequestContext build(T request);
    
}