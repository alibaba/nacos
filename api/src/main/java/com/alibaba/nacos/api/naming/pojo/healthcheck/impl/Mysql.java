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

import org.apache.commons.lang3.StringUtils;

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.google.common.base.Objects;

/**
 * Implementation of health checker for MYSQL.
 *
 * @author yangyi
 */
public class Mysql extends AbstractHealthChecker {
    public static final String TYPE = "MYSQL";

    private String user;

    private String pwd;

    private String cmd;

    public Mysql() {
        super(TYPE);
    }

    public String getCmd() {
        return cmd;
    }

    public String getPwd() {
        return pwd;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(user, pwd, cmd);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Mysql)) {
            return false;
        }

        Mysql other = (Mysql) obj;

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
        Mysql config = new Mysql();
        config.setUser(this.getUser());
        config.setPwd(this.getPwd());
        config.setCmd(this.getCmd());
        return config;
    }
}
