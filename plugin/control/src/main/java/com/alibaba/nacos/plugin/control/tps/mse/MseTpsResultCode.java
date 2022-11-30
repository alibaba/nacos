package com.alibaba.nacos.plugin.control.tps.mse;

public class MseTpsResultCode {
    
    /**
     *
     */
    public static final int PASS_BY_PRE_INTERCEPTOR = 202;
    
    /**
     *
     */
    public static final int PASS_BY_POST_INTERCEPTOR = 203;
    
    /**
     *
     */
    public static final int PASS_BY_PATTERN = 204;
    
    /**
     *
     */
    public static final int DENY_BY_PATTERN = 301;
    
    /**
     *
     */
    public static final int DENY_BY_PRE_INTERCEPTOR = 302;
    
    /**
     *
     */
    public static final int DENY_BY_POST_INTERCEPTOR = 303;
}
