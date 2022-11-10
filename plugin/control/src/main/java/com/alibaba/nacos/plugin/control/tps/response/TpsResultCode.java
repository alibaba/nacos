package com.alibaba.nacos.plugin.control.tps.response;

public enum TpsResultCode {
    
    PASS_BY_POINT(200, "check pass"),
    
    PASS_BY_MONITOR(201, "check fail ,but passed by monitor mode"),
    
    PASS_BY_PRE_INTERCEPTOR(202, "check fail ,but passed by monitor mode"),
    
    PASS_BY_POST_INTERCEPTOR(203, "check fail ,but passed by monitor mode"),
    
    DENY_BY_POINT(300, "tps check deny"),
    
    DENY_BY_PATTERN(301, "tps check deny"),
    
    DENY_BY_PRE_INTERCEPTOR(302, "tps check deny"),
    
    DENY_BY_POST_INTERCEPTOR(303, "tps check deny"),
    
    CHECK_SKIP(100, "check skip");
    
    int code;
    
    String desc;
    
    TpsResultCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
