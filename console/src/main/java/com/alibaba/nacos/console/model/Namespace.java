package com.alibaba.nacos.console.model;

/**
 * 命名空间
 * @author diamond
 *
 */
public class Namespace {

	private String namespace;

	private String namespaceShowName;

	private int quota;

	private int configCount;
    /**
     *  0 全局配置， 1 默认私有命名空间 ，2 自定义命名空间
     */
	private int type;

	public String getNamespaceShowName() {
		return namespaceShowName;
	}
	public void setNamespaceShowName(String namespaceShowName) {
		this.namespaceShowName = namespaceShowName;
	}
	public String getNamespace() {
		return namespace;
	}
	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}
	
	public Namespace() {
	}

	public Namespace(String namespace, String namespaceShowName) {
		this.namespace = namespace;
		this.namespaceShowName = namespaceShowName;
	}
	
	public Namespace(String namespace, String namespaceShowName, int quota, int configCount, int type) {
		this.namespace = namespace;
		this.namespaceShowName = namespaceShowName;
		this.quota = quota;
		this.configCount = configCount;
		this.type = type;
	}

	public int getQuota() {
		return quota;
	}

	public void setQuota(int quota) {
		this.quota = quota;
	}

	public int getConfigCount() {
		return configCount;
	}
	public void setConfigCount(int configCount) {
		this.configCount = configCount;
	}
	public int getType() {
		return type;
	}
	public void setType(int type) {
		this.type = type;
	}

}
