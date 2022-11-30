package com.alibaba.nacos.plugin.control.tps.response;

public class TpsResultCode {
    
    public static final int PASS_BY_POINT = 200;
    
    /**
     * rule denied,but pass by monitor.
     */
    public static final int PASS_BY_MONITOR = 201;
    
    /**
     * deny by point rule.
     */
    public static final int DENY_BY_POINT = 300;
    
    /**
     * skip.
     */
    public static final int CHECK_SKIP = 100;
    
    
}
