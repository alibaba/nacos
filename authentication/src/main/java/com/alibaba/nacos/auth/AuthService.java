package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.model.UserFromRequest;
import com.alibaba.nacos.auth.model.UserFromService;

public interface AuthService {
    
    // HTTP
    Boolean login(Object request);//throws AccessException;
    
    
    // get username and password from request
    Boolean validation(Object object1, Object object2);
    
    //
    
    
    
    
}
