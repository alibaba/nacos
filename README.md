## Nacos 


[![Gitter](https://badges.gitter.im/alibaba/nacos.svg)](https://gitter.im/alibaba/nacos?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)   [![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Gitter](https://travis-ci.org/alibaba/nacos.svg?branch=master)](https://travis-ci.org/alibaba/nacos)

-------
<img src="doc/Nacos_Logo.png" width="50%" height="50%" />
Nacos is an easy-to-use platform designed for dynamic service discovery and configuration and service management. It helps you to build cloud native applications and microservices platform easily.

Service is a first-class citizen in Nacos. Nacos supports almost all type of services，for example，[Dubbo/gRPC service](https://nacos.io/en-us/docs/use-nacos-with-dubbo.html)、[Spring Cloud RESTFul service](https://nacos.io/en-us/docs/use-nacos-with-springcloud.html) or [Kubernetes service](https://nacos.io/en-us/docs/use-nacos-with-kubernetes.html).

Nacos provides four major functions.

* **Service Discovery and Service Health Check** 
    
    Nacos makes it simple for services to register themselves and to discover other services via a DNS or HTTP interface. Nacos also provides real-time healthchecks of services to prevent sending requests to unhealthy hosts or service instance.

* **Dynamic Configuration Management**
  
  Dynamic Configuration Service allows you to manage configurations of all services in a centralized and dynamic manner across all environments. Nacos eliminates the need to redeploy applications and services when configurations are updated，which makes configuration changes more efficient and agile.

* **Dynamic DNS Service**

   Nacos supports weighted routing, making it easier for you to implement mid-tier load balancing, flexible routing policies, flow control, and simple DNS resolution services in the production environment within your data center. It helps you to implement DNS-based service discovery easily and prevent applications from coupling to vendor-specific service discovery APIs.

* **Service and MetaData Management**
	
	Nacos provides an easy-to-use service dashboard to help you manage your services metadata, configuration, kubernetes DNS, service health and metrics statistics.
 

### Quick Start
It is super easy to get started with your first project.

1. #### Download run package 
[Download](https://github.com/alibaba/nacos/releases/download/0.2.1/nacos-server-0.2.1.zip)

```
unzip nacos-server-0.2.1.zip
cd nacos/bin 
``` 

2. #### Start Server
* ##### Linux/Unix/Mac

```
Run the following command to start (standalone means non-cluster mode): 

sh startup.sh -m standalone
```

* ##### Windows
```
Run the following command to start:

cmd startup.cmd

Or double-click the startup.cmd to run NacosServer.
```

For more details, see [quick-start.](https://nacos.io/en-us/docs/quick-start.html)

3. #### Quick start for other open-source projects:

* [quick start with spring cloud](https://nacos.io/en-us/docs/use-nacos-with-springcloud.html)

* [quick start with dubbo](https://nacos.io/en-us/docs/use-nacos-with-dubbo.html)

* [quick start with kubernetes](https://nacos.io/en-us/docs/use-nacos-with-kubernetes.html)

* [more...](https://nacos.io/)

### Documentation

You can view full documentation on the Nacos website:

* [nacos.io](https://nacos.io/en-us/docs/what-is-nacos.html)

### Contact

* #### Gitter-[Nacos Gitter](https://gitter.im/alibaba/nacos)

* #### Weibo-[Nacos Weibo](https://weibo.com/u/6574374908)

* #### Segmentfault-[Nacos Segmentfault](https://segmentfault.com/t/nacos)

* #### Mailing list-[nacos\_dev@linux.alibaba.com](mailto:nacos_dev@linux.alibaba.com).

