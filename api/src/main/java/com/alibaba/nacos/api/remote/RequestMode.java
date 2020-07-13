/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.api.remote;

/**
 * @author liuzunfei
 * @version $Id: RequestMode.java, v 0.1 2020年07月13日 3:46 PM liuzunfei Exp $
 */
public enum RequestMode {

    COMMON("","");
    String mode;
    String desc;

    /**
     * Private constructor
     * @param mode
     * @param desc
     */
    private RequestMode(String mode,String desc){
        this.mode=mode;
        this.desc=desc;
    }


}
