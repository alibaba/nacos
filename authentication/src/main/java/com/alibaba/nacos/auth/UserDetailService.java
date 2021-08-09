package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.model.UserFromService;

public interface UserDetailService {
    
    /**
     * 根据username，从Nacos service端获取用户鉴权信息
     * @param username
     * @return
     */
    UserFromService loadUserDetailByUsername(String username);
    
}
