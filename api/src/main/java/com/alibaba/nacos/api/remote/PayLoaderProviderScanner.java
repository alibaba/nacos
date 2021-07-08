package com.alibaba.nacos.api.remote;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;

import java.util.HashSet;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * PayLoaderProviderScanner.
 *
 * @author dingjuntao
 * @date 2021/7/8 16:44
 */

public class PayLoaderProviderScanner  {
    
    private static boolean initialized = false;
    
    private HashSet<PayLoaderProvider> payLoaderProviderSet = new HashSet<>();
    
    PayLoaderProviderScanner() {
        init();
    }
    
    private void init() {
        if (initialized) {
            return;
        }
        int providerCount = 0;
        
        ServiceLoader<PayLoaderProvider> payLoaderProviders = ServiceLoader.load(PayLoaderProvider.class);
        try {
            for (PayLoaderProvider each : payLoaderProviders) {
                boolean addFlag = payLoaderProviderSet.add(each);
                if (!addFlag) {
                    throw new RuntimeException(String.format("Fail to Load Service, clazz:%s ", each.getClass().getCanonicalName()));
                }
                providerCount ++;
            }
        } catch (ServiceConfigurationError e) {
            // 客户端就是会有这个错误
            if (providerCount != 3){
                throw new RuntimeException(e.fillInStackTrace());
            }
        }
        
        initialized = true;
    }

    public Set<Class<? extends Request>> getAllPayLoadRequestSet() throws Exception {
        Set<Class<? extends Request>> allPayLoadRequestSet = new HashSet<>();
        for (PayLoaderProvider eachPayLoaderProvider : payLoaderProviderSet) {
            Set<Class<? extends Request>> newPayLoadRequestSet = eachPayLoaderProvider.getPayLoadRequestSet();
            for (Class<? extends Request> newPayLoadRequest : newPayLoadRequestSet) {
                boolean addFlag = allPayLoadRequestSet.add(newPayLoadRequest);
                if (!addFlag) {
                    throw new RuntimeException(String.format("Fail to Load Request class, clazz:%s ", newPayLoadRequest.getCanonicalName()));
                }
            }
        }
        return allPayLoadRequestSet;
    }
    
    public Set<Class<? extends Response>> getAllPayLoadResponseSet() throws Exception {
        Set<Class<? extends Response>> allPayLoadResponseSet = new HashSet<>();
        for (PayLoaderProvider eachPayLoaderProvider : payLoaderProviderSet) {
            Set<Class<? extends Response>> newPayLoadResponseSet = eachPayLoaderProvider.getPayLoadResponseSet();
            for (Class<? extends Response> newPayLoadResponse : newPayLoadResponseSet) {
                boolean addFlag = allPayLoadResponseSet.add(newPayLoadResponse);
                if (!addFlag) {
                    throw new RuntimeException(String.format("Fail to Load Response class, clazz:%s ", newPayLoadResponse.getCanonicalName()));
                }
            }
        }
        return allPayLoadResponseSet;
    }
    
}
