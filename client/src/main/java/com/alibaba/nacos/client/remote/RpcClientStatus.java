/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.remote;

/**
 * @author liuzunfei
 * @version $Id: RpcClientStatus.java, v 0.1 2020年07月14日 3:49 PM liuzunfei Exp $
 */
public enum RpcClientStatus {



    WAIT_INIT(0,"wait to  init serverlist factory... "),
    INITED(1,"server list factory is ready,wait to start"),
    STARTING(2, "server list factory is ready,wait to start"),
    RUNNING(3, "client is running...");

    int status;
    String desc;

    RpcClientStatus(int status,String desc){
        this.status=status;
        this.desc=desc;
    }
}
