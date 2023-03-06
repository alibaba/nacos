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

import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

/**
 * ClientConnectionEventListener.
 *
 * @author liuzunfei
 * @version $Id: ClientConnectionEventListener.java, v 0.1 2020年07月16日 3:06 PM liuzunfei Exp $
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class ClientConnectionEventListener {
    
    /**
     * listener name.
     */
    private String name;
    
    @Autowired
    protected ClientConnectionEventListenerRegistry clientConnectionEventListenerRegistry;
    
    @PostConstruct
    public void init() {
        clientConnectionEventListenerRegistry.registerClientConnectionEventListener(this);
    }
    
    /**
     * Getter method for property <tt>name</tt>.
     *
     * @return property value of name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Setter method for property <tt>name</tt>.
     *
     * @param name value to be assigned to property name
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * notified when a client connected.
     *
     * @param connect connect.
     */
    public abstract void clientConnected(Connection connect);
    
    /**
     * notified when a client disconnected.
     *
     * @param connect connect.
     */
    public abstract void clientDisConnected(Connection connect);
    
}
