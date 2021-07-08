package com.alibaba.nacos.api.remote;

import com.alibaba.nacos.api.remote.impl.RemotePayLoaderProvider;
import com.alibaba.nacos.api.remote.request.Request;
import org.junit.Test;
import com.alibaba.nacos.api.remote.response.Response;

import java.util.Set;


/**
 * @author dingjuntao
 * @date 2021/7/8 21:23
 */
public class RemotePayLoaderProviderTest {
    
    @Test
    public void getPayLoadRequestSetTest() {
        RemotePayLoaderProvider remotePayLoaderProvider = new RemotePayLoaderProvider();
        try {
            Set<Class<? extends Request>> set = remotePayLoaderProvider.getPayLoadRequestSet();
            for (Class<? extends Request> newPayLoadRequest : set) {
                System.out.println(newPayLoadRequest.getSimpleName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Test
    public void getPayLoadResponseSetTest() {
        RemotePayLoaderProvider remotePayLoaderProvider = new RemotePayLoaderProvider();
        try {
            Set<Class<? extends Response>> set = remotePayLoaderProvider.getPayLoadResponseSet();
            for (Class<? extends Response> newPayLoadResponse : set) {
                System.out.println(newPayLoadResponse.getSimpleName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
