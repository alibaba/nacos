/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.api.remote.connection;

import java.util.Date;

import com.alibaba.nacos.api.remote.response.Response;

/**
 * @author liuzunfei
 * @version $Id: Connection.java, v 0.1 2020年07月13日 7:08 PM liuzunfei Exp $
 */
public abstract class Connection {


    private ConnectionMetaInfo metaInfo;


    public Connection(ConnectionMetaInfo metaInfo){
        this.metaInfo=metaInfo;
    }


    /**
     * Send response to this client that associated to this connection
     * @param reponse
     */
    public abstract void sendResponse(Response reponse);

    /**
     * Close this connection, if this connection is not active yet,
     */
    public  abstract void closeGrapcefully();

    /**
     * Update last Active Time to now.
     */
    public void freshActiveTime(){
        metaInfo.lastActiveTime=new Date();
    }

    /**
     * return last active time, include request occurs and
     * @return
     */
    public Date getLastActiveTime(){
        return metaInfo.lastActiveTime;
    }


}

