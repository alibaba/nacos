/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.core.remote;

/**
 * @author liuzunfei
 * @version $Id: RPCServer.java, v 0.1 2020年07月13日 3:41 PM liuzunfei Exp $
 */
public abstract class RPCServer {



    /**
     *
     */
    abstract  public void init();

    /**
     *
     * @param requestHandlerReactor
     */
    abstract void initRequestHandlerReactor(RequestHandlerReactor requestHandlerReactor);
}
