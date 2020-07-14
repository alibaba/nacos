/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.remote;

import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.JacksonUtils;

/**
 * @author liuzunfei
 * @version $Id: ChangeListenResponseHandler.java, v 0.1 2020年07月14日 11:41 AM liuzunfei Exp $
 */
public abstract interface ChangeListenResponseHandler<T> {

    /**
     *
     * @param response
     */
    abstract public void responseReply(Response  response);

    /**
     *
     * @param bodyString
     * @param <T>
     * @return
     */
    public <T extends Response> T parseBodyString(String bodyString);

}
