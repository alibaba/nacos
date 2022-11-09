package com.alibaba.nacos.plugin.control.tps.interceptor;

public enum InterceptResult {
    
    CHECK_PASS(200, "check pass"),
    
    CHECK_DENY(503, "check deny"),
    
    CHECK_SKIP(202, "check skip");
    
    int code;
    
    String desc;
    
    InterceptResult(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
