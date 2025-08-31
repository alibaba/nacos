package com.alibaba.nacos.ai.form.mcp.admin;

/**
 * Mcp server update form.
 *
 * @author Daydreamer
 */
public class McpStatusForm extends McpForm {

    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
