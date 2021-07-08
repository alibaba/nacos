package com.alibaba.nacos.api.remote.impl;

import com.alibaba.nacos.api.remote.PayLoaderProvider;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;

import java.util.Set;

/**
 * @author dingjuntao
 * @date 2021/7/8 16:43
 */
public class RemotePayLoaderProvider implements PayLoaderProvider {
    
    @Override
    public Set<Class<? extends Request>> getPayLoadRequestSet() throws Exception {
        return null;
    }
    
    @Override
    public Set<Class<? extends Response>> getPayLoadResponseSet() throws Exception {
        return null;
    }
}
