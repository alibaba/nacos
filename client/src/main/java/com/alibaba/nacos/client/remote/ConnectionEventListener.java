/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.remote;

/**
 * @author liuzunfei
 * @version $Id: ConnectionEventListener.java, v 0.1 2020年07月14日 10:59 AM liuzunfei Exp $
 */
public interface ConnectionEventListener {


    /**
     *
     */
    public void onConnected();

    /**
     *
     */
    public void onReconnected();

    /**
     *
     */
    public void onDisConnect();
}
