package com.alibaba.nacos.auth.authContext;

public interface AuthContextBuilder {
    
    AuthContext build(Object request);


}
