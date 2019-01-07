package com.alibaba.nacos.api.naming.loadbalancer;



import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;


/**
 *
 * abstract User-defined implementation of LoadBalancer
 * @author XCXCXCXCX
 */
public interface LoadBalancer{

    Instance doChoose(ServiceInfo serviceInfo);

}
