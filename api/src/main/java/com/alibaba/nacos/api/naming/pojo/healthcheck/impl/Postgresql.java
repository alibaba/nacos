package com.alibaba.nacos.api.naming.pojo.healthcheck.impl;

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;

import java.util.Objects;

/**
 * Implementation of health checker for PostgreSQL.
 *
 * @author richwxd
 */
public class Postgresql extends AbstractHealthChecker {
    private static final long serialVersionUID = 1L;

    public static final String TYPE = "POSTGRESQL";

    private String user;

    private String pwd;

    private String cmd;

    protected Postgresql() {
        super(Postgresql.TYPE);
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
        return Objects.hash(user, pwd, cmd);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Postgresql)) {
            return false;
        }
        Postgresql that = (Postgresql) o;
        return Objects.equals(getUser(), that.getUser()) && Objects.equals(getPwd(), that.getPwd()) && Objects.equals(getCmd(), that.getCmd());
    }

    @Override
    public Postgresql clone() throws CloneNotSupportedException {
        Postgresql config = new Postgresql();
        config.setUser(getUser());
        config.setPwd(getPwd());
        config.setCmd(getCmd());
        return config;
    }
}
