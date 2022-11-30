package com.alibaba.nacos.plugin.control.connection.response;

public class ConnectionCheckCode {
    
    /**
     * check pass.
     */
    public static final int PASS_BY_TOTAL = 200;
    
    /**
     * skip.
     */
    public static final int CHECK_SKIP = 100;
    
    /**
     * deny by total over limit.
     */
    public static final int DENY_BY_TOTAL_OVER = 300;
    
    /**
     * pass by monitor type.
     */
    public static final int PASS_BY_MONITOR = 205;
}
