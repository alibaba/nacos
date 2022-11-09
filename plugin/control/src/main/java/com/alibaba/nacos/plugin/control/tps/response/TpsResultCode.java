package com.alibaba.nacos.plugin.control.tps.response;

public enum TpsResultCode {
    
    CHECK_PASS(200, "check pass"),
    
    PASS_BY_MONITOR(201, "check fail ,but passed by monitor mode"),
    
    PASS_BY_INTERCEPTOR(202, "check fail ,but passed by monitor mode"),
    
    CHECK_DENY(503, "tps check deny"),
    
    CHECK_SKIP(100, "check skip");
    
    int code;
    
    String desc;
    
    TpsResultCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
