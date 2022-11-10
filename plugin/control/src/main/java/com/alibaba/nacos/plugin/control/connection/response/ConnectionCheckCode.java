package com.alibaba.nacos.plugin.control.connection.response;

public enum ConnectionCheckCode {
    
    PASS_BY_TOTAL(200, "check pass"),
    
    PASS_BY_IP(201, "check fail ,but passed by monitor mode"),
    
    PASS_BY_PRE_INTERCEPT(202, "check fail ,but passed by monitor mode"),
    
    PASS_BY_POST_INTERCEPT(203, "check fail ,but passed by monitor mode"),
    
    DENY_BY_TOTAL_OVER(300, "connection check deny"),
    
    DENY_BY_IP_OVER(301, "connection check deny"),
    
    DENY_BY_PRE_INTERCEPT(302, "connection check deny"),
    
    DENY_BY_POST_INTERCEPT(303, "connection check deny"),
    
    CHECK_SKIP(100, "check skip");
    
    int code;
    
    String desc;
    
    ConnectionCheckCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
