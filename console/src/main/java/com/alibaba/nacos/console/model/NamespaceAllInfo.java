package com.alibaba.nacos.console.model;

/**
 * all namespace info
 * @author Nacos
 *
 */
public class NamespaceAllInfo extends Namespace {

	private String namespaceDesc;

	public String getNamespaceDesc() {
		return namespaceDesc;
	}

	public void setNamespaceDesc(String namespaceDesc) {
		this.namespaceDesc = namespaceDesc;
	}

	public NamespaceAllInfo() {
	};

	public NamespaceAllInfo(String namespace, String namespaceShowName, int quota, int configCount, int type,
			String namespaceDesc) {
		super(namespace, namespaceShowName, quota, configCount, type);
		this.namespaceDesc = namespaceDesc;
	}

}
