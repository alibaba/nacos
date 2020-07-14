/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;

import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.request.Request;

/**
 * @author liuzunfei
 * @version $Id: RpcClient.java, v 0.1 2020年07月13日 9:15 PM liuzunfei Exp $
 */
public abstract class RpcClient {

    private ServerListFactory serverListFactory;

    protected String connectionId;

    protected  RpcClientStatus rpcClientStatus=RpcClientStatus.WAIT_INIT;

    /**
     * check is this client is inited
     * @return
     */
    public boolean isInited(){
        return this.rpcClientStatus!=RpcClientStatus.WAIT_INIT;
    }
    /**
     *  listener called where connect status changed
     */
    List<ConnectionEventListener> connectionEventListeners=new ArrayList<ConnectionEventListener>();

    /**
     * change listeners handler registry
     */
    List<ChangeListenResponseHandler> changeListenReplyListeners=new ArrayList<ChangeListenResponseHandler>();

    public RpcClient(){

    }

    public RpcClient(ServerListFactory serverListFactory){
        this.serverListFactory=serverListFactory;
        this.connectionId= UUID.randomUUID().toString();
        this.rpcClientStatus=RpcClientStatus.INITED;
    }


    /**
     * Start this client
     */
    @PostConstruct
    abstract public void start() throws Exception;


    /**
     *
     */
    abstract public void switchServer();

    /**
     *
     * @param request
     * @param <T>
     * @return
     */
    abstract  public <T extends Response> T request( Request  request);

    /**
     *
     * @param request
     * @param <T>
     * @return
     */
    abstract  public <T extends Response> T listenChange( Request  request);



    /**
     *
     * @param connectionEventListener
     */
    public void registerConnectionListener(ConnectionEventListener connectionEventListener){
        this.connectionEventListeners.add(connectionEventListener);
    }

    /**
     *
     * @param changeListenResponseHandler
     */
    public void registerChangeListenHandler(ChangeListenResponseHandler changeListenResponseHandler){
        this.changeListenReplyListeners.add(changeListenResponseHandler);
    }

    /**
     * Getter method for property <tt>serverListFactory</tt>.
     *
     * @return property value of serverListFactory
     */
    public ServerListFactory getServerListFactory() {
        return serverListFactory;
    }


}
