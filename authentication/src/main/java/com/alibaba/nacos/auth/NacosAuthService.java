package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.authContext.AuthContextBuilder;
import com.alibaba.nacos.auth.model.UserFromRequest;
import com.alibaba.nacos.auth.model.UserFromService;
import com.alibaba.nacos.auth.utils.HttpNet;
import com.alibaba.nacos.auth.utils.NetContext;

import java.util.HashMap;
import java.util.Map;

public class NacosAuthService implements AuthService{
    
    @Override
    public Boolean login(AuthContextBuilder authContext){
        return true;
        /*
        // 这里需要判断是GRCP红色HTTP，暂定为HTTP
        NetContext netContext = new NetContext(new HttpNet());
        // 从request中获取鉴权信息，返回userFromRequest
        UserFromRequest userFromRequest = (UserFromRequest)netContext.getReuqestUser(request);
        
        // 根据username来查询nacos service中该用户的信息
        NacosUserDetailService nacosUserDetailService = new NacosUserDetailService();
        UserFromService userFromService = nacosUserDetailService.loadUserDetailByUsername(userFromRequest.getUserName());
        
        // 如果该请求已经携带了token
        if(userFromRequest.getAuthToken()!= null){
            Boolean authResult =  validation(userFromRequest.getAuthToken(), userFromService.getToken());
            if(!authResult){
                Map<String,  String> map = new HashMap<String,String>();
                // 状态码
                map.put("code","404");
                // 错误信息
                map.put("message","authentication error!");
                // 发送失败信息给客户端
                netContext.responseLoginFail(map);
            }
        }
        
        // 该请求不携带token，而是username和password
        
        
        if(userFromRequest.getPassword() == userFromService.getPassword()){
            Map<String,  String> map = new HashMap<String,String>();
            map.put("code","404");
            map.put("message","password error!");
            netContext.responseLoginSuccess(map);
            return false;
        }
        String token = generateToken(userFromRequest.getUserName(),userFromRequest.getPassword());
        Map<String,  String> map = new HashMap<String,String>();
        map.put("code","200");
        map.put("token",token);
        netContext.responseLoginSuccess(map);
        return true;
    }
    @Override
    public Boolean validation(Object object1, Object object2){
        if(object1.equals(object2)){
            return true;
        }
        return false;
        */

     
    }
    
    @Override
    public Boolean validation(Object object1, Object object2) {
        return null;
    }
    
    /**
     * generate token
     * 这里先简单的定义一个token，方便说明
     * @param username
     * @param password
     * @return
     */
    public String generateToken(String username, String password){
        return username+"_"+password+"_12345";
    }
    
    
}
