package com.alibaba.nacos.api.remote;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;

import java.util.Set;

/**
 * @author dingjuntao
 * @date 2021/7/8 16:48
 */

public interface PayLoaderProvider {
    
    
    /**
     * get the Request classes.
     *
     * @return Set of class extends Request
     * @throws Exception exception throws .
     */
    Set<Class<? extends Request>> getPayLoadRequestSet() throws Exception;
    
    
    
    /**
     * get the Response classes.
     *
     * @return Set of class extends Response
     * @throws Exception exception throws .
     */
    Set<Class<? extends Response>> getPayLoadResponseSet() throws Exception;
    
}
