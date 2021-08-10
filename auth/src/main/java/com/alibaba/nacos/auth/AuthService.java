package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.context.RequestContext;
import com.alibaba.nacos.auth.exception.AccessException;

/**
 * Auth service.
 *
 * @author Wuyfee
 */
public interface AuthService {
    
    /**
     * Authentication of request, identify the user who request the resource.
     *
     * @param requestContext where we can find the user information
     * @return boolean if the user identify success
     * @throws AccessException if authentication is failed
     */
    Boolean login(RequestContext requestContext) throws AccessException;
    
    
    /**
     * identity whether the user has the resource authority.
     * @param requestContext  where we can find the user information.
     * @param resourceProvider provider user resource.
     * @return Boolean if the user has the resource authority.
     */
    Boolean authorityAccess(RequestContext requestContext, ResourceProvider resourceProvider);
    
}
