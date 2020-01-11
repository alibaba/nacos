package com.alibaba.nacos.config.server.auth;

/**
 * Role Info
 *
 * @author nkorange
 * @since 1.2.0
 */
public class RoleInfo {

    private String role;

    private String username;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
