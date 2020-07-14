/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.core.remote;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.nacos.api.remote.connection.Connection;

import org.springframework.stereotype.Service;

/**
 * @author liuzunfei
 * @version $Id: ConnectionManager.java, v 0.1 2020年07月13日 7:07 PM liuzunfei Exp $
 */
@Service
public class ConnectionManager {

    private Map<String, Connection> connetions=new HashMap<String,Connection>();

    public void register(String clientId,Connection connection){
        connetions.putIfAbsent(clientId,connection);
        System.out.println("connetions updated, connetions:"+ connetions);
    }

    public void unregister(String clientId){
        this.connetions.remove(clientId);
    }

    public void refreshActiveTime(String connnectionId){
        System.out.println("connetions activetime update , connnectionId:"+ connnectionId);

        Connection connection = connetions.get(connnectionId);
        if (connection!=null){
            connection.freshActiveTime();
        }
    }


}
