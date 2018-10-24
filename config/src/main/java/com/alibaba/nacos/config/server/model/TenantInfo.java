package com.alibaba.nacos.config.server.model;

/**
 *  tenant info
 * @author Nacos
 *
 */
public class TenantInfo {

	private String tenantId;
	private String tenantName;
	private String tenantDesc;
	
	public String getTenantId() {
		return tenantId;
	}
	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}
	public String getTenantName() {
		return tenantName;
	}
	public void setTenantName(String tenantName) {
		this.tenantName = tenantName;
	}
	public String getTenantDesc() {
		return tenantDesc;
	}
	public void setTenantDesc(String tenantDesc) {
		this.tenantDesc = tenantDesc;
	}
	
}
