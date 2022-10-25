package com.alibaba.nacos.plugin.control.connection.response;

public enum ConnectionCheckCode {
    
    CHECK_PASS(200, "check pass"),
    
    PASS_BY_MONITOR(201, "check fail ,but passed by monitor mode"),
    
    CHECK_DENY(503, "connection check deny"),
    
    CHECK_SKIP(202, "check skip");
    
    int code;
    
    String desc;
    
    ConnectionCheckCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
