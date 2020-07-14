/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.remote;

/**
 * @author liuzunfei
 * @version $Id: ServerListFactory.java, v 0.1 2020年07月14日 1:11 PM liuzunfei Exp $
 */
public interface ServerListFactory {


    String genNextServer();

    String getCurrentServer();

}
