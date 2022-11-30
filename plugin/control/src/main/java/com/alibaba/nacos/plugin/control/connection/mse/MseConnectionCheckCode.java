package com.alibaba.nacos.plugin.control.connection.mse;

public class MseConnectionCheckCode {
    
    /**
     * check fail ,but passed by ip mode.
     */
    public static final int PASS_BY_IP = 201;
    
    /**
     * pass by pre intercept
     */
    public static final int PASS_BY_PRE_INTERCEPT = 202;
    
    /**
     *
     */
    public static final int PASS_BY_POST_INTERCEPT = 203;
    
    /**
     *
     */
    public static final int DENY_BY_IP_OVER = 301;
    
    /**
     *
     */
    public static final int DENY_BY_PRE_INTERCEPT = 302;
    
    /**
     *
     */
    public static final int DENY_BY_POST_INTERCEPT = 303;
}
