package com.alibaba.nacos.api.ai.model.mcp;

/**
 * SecurityScheme 表示安全认证方案的模型，包括类型、方案、位置、名称及默认凭证等信息.
 * 用于描述 API 的安全机制.
 *
 * @author xinluo
 */
public class SecurityScheme {
    
    /**
     * ID of the security scheme. Will be used and reference by tools.
     */
    private String id;
    
    /**
     * Type of the security scheme. Possible values are: 'http', 'apiKey', 'localEnv' or other custom extension.
     */
    private String type;
    
    /**
     * Scheme of the security scheme. Used when {@link #type} is `http`. Possible values are: `basic` 或 `bearer`.
     */
    private String scheme;
    
    /**
     * Location of the security scheme. Possible values are: `query`, `header`.
     */
    private String in;
    
    /**
     * Name of the security scheme. Used when {@link #type} is `apiKey` or `localEnv`.
     * e.g. the key name for `apiKey` or environment name for `localEnv`.
     */
    private String name;
    
    /**
     * The default credential when leak input identity by properties. Optional.
     */
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
