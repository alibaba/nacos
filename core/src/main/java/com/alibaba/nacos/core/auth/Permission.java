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
package com.alibaba.nacos.core.auth;

import java.io.Serializable;

/**
 * Permission to auth
 *
 * @author nkorange
 * @since 1.2.0
 */
public class Permission implements Serializable {

    private static final long serialVersionUID = -3583076254743606551L;

    public Permission() {

    }

    public Permission(String resource, String action) {
        this.resource = resource;
        this.action = action;
    }

    /**
     * An unique key of resource
     */
    private String resource;

    /**
     * Action on resource, refer to class ActionTypes
     */
    private String action;

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return "Permission{" +
            "resource='" + resource + '\'' +
            ", action='" + action + '\'' +
            '}';
    }
}
