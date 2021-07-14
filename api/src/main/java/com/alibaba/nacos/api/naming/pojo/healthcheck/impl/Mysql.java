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

package com.alibaba.nacos.api.naming.pojo.healthcheck.impl;

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.utils.StringUtils;
import com.google.common.base.Objects;

/**
 * Implementation of health checker for MYSQL.
 *
 * @author yangyi
 */
public class Mysql extends AbstractHealthChecker {
    
    public static final String TYPE = "MYSQL";
    
    private static final long serialVersionUID = 7928108094599401491L;
    
    private String user;
    
    private String pwd;
    
    private String cmd;
    
    public Mysql() {
        super(Mysql.TYPE);
    }
    
    public String getCmd() {
        return this.cmd;
    }
    
    public String getPwd() {
        return this.pwd;
    }
    
    public String getUser() {
        return this.user;
    }
    
    public void setUser(final String user) {
        this.user = user;
    }
    
    public void setCmd(final String cmd) {
        this.cmd = cmd;
    }
    
    public void setPwd(final String pwd) {
        this.pwd = pwd;
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(user, pwd, cmd);
    }
    
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Mysql)) {
            return false;
        }
        
        final Mysql other = (Mysql) obj;
        
        if (!StringUtils.equals(user, other.getUser())) {
            return false;
        }
        
        if (!StringUtils.equals(pwd, other.getPwd())) {
            return false;
        }
        
        return StringUtils.equals(cmd, other.getCmd());
    }
    
    @Override
    public Mysql clone() throws CloneNotSupportedException {
        final Mysql config = new Mysql();
        config.setUser(getUser());
        config.setPwd(getPwd());
        config.setCmd(getCmd());
        return config;
    }
}
