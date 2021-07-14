package com.alibaba.nacos.api.remote;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import org.junit.Test;
import java.util.Set;

/**
 * PayLoaderProviderScannerTest.
 *
 * @author dingjuntao
 * @date 2021/7/8 21:39
 */
public class PayLoaderProviderScannerTest {
    
    @Test
    public void getAllPayLoadRequestSetTest() {
        PayLoaderProviderScanner payLoaderProviderScanner = new PayLoaderProviderScanner();
        payLoaderProviderScanner.init();
        try {
            Set<Class<? extends Request>> set = payLoaderProviderScanner.getAllPayLoadRequestSet();
            for (Class<? extends Request> newPayLoadRequest : set) {
                System.out.println(newPayLoadRequest.getSimpleName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Test
    public void getAllPayLoadResponseSetTest() {
        PayLoaderProviderScanner payLoaderProviderScanner = new PayLoaderProviderScanner();
        payLoaderProviderScanner.init();
        try {
            Set<Class<? extends Response>> set = payLoaderProviderScanner.getAllPayLoadResponseSet();
            for (Class<? extends Response> newPayLoadRequest : set) {
                System.out.println(newPayLoadRequest.getSimpleName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
