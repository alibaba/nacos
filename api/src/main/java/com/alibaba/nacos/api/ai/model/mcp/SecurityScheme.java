package com.alibaba.nacos.api.ai.model.mcp;

/**
 * SecurityScheme 表示安全认证方案的模型，包括类型、方案、位置、名称及默认凭证等信息.
 * 用于描述 API 的安全机制.
 *
 * @author xinluo
 */
public class SecurityScheme {
    
    private String id;
    
    private String type;
    
    private String scheme;
    
    private String in;
    
    private String name;
    
    private String defaultCredential;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getIn() {
        return in;
    }

    public void setIn(String in) {
        this.in = in;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDefaultCredential() {
        return defaultCredential;
    }

    public void setDefaultCredential(String defaultCredential) {
        this.defaultCredential = defaultCredential;
    }
}
