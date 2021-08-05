package com.alibaba.nacos.auth.utils;
import com.alibaba.nacos.auth.model.User;
import com.alibaba.nacos.auth.model.UserFromRequest;

import java.util.Map;


public class GrpcNet implements NetUtils {
    /**
     * get User auth information from Grpc request
     */
    @Override
    public UserFromRequest resolveRequest(Object request){
            return null;
    }
    
    @Override
    public void responseLoginSuccess(Map<String,  String> successMessage) {
    
    }
    
    @Override
    public void responseLoginFail(Map<String,  String> failMessage) {
    
    }
    
}
 