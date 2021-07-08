package com.alibaba.nacos.api.remote;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.utils.ClassUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * interface of request and response scanner.
 *
 * @author dingjuntao
 * @date 2021/7/8 16:48
 */

public abstract class PayLoaderProvider {
    
    
    /**
     * get the Request classes.
     *
     * @return Set of class extends Request
     * @throws Exception exception throws .
     */
    public Set<Class<? extends Request>> getPayLoadRequestSet() throws Exception{
        String packageName = this.getClass().getPackage().getName();
        int lastIndex = packageName.lastIndexOf(".");
        String requestPackageName = packageName.substring(0, lastIndex + 1) + "request";
        ArrayList<Class> PayLoadRequestList  = ClassUtils.getAllClassByAbstractClass(Request.class, requestPackageName);
        Set<Class<? extends Request>> PayLoadRequestSet = new HashSet<>();
        for (Class clazz: PayLoadRequestList){
            boolean addFlag = PayLoadRequestSet.add(clazz);
            if (!addFlag) {
                throw new RuntimeException(String.format("Fail to Load Request class, clazz:%s ", clazz.getCanonicalName()));
            }
        }
        return PayLoadRequestSet;
    }
    
    
    
    /**
     * get the Response classes.
     *
     * @return Set of class extends Response
     * @throws Exception exception throws .
     */
    public Set<Class<? extends Response>> getPayLoadResponseSet() throws Exception{
        String packageName = this.getClass().getPackage().getName();
        int lastIndex = packageName.lastIndexOf(".");
        String ResponsePackageName = packageName.substring(0, lastIndex + 1) + "response";
        ArrayList<Class> PayLoadResponseList  = ClassUtils.getAllClassByAbstractClass(Response.class, ResponsePackageName);
        Set<Class<? extends Response>> PayLoadResponseSet = new HashSet<>();
        for (Class clazz: PayLoadResponseList){
            boolean addFlag = PayLoadResponseSet.add(clazz);
            if (!addFlag) {
                throw new RuntimeException(String.format("Fail to Load Response class, clazz:%s ", clazz.getCanonicalName()));
            }
        }
        return PayLoadResponseSet;
    }
    
}
