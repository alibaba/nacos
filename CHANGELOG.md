## 0.6.0(Dec, 2018)

[#388] Cluster name should be provided in the Instance
[#377] Clean up messy code in Naming module
[#369] Support instance list persisted on disk
[#366] findbugs-maven-plugin version
[#362] The metadata will lost when online or offline instance through web ui
[#352] Refactoring internationalization Nacos console
[#278] Nacos docker img
[#243] optimize the efficiency of integration testing, it’s taking too long now

## 0.5.0(Nov, 2018)

[#148] Naming write performace.
[#175] Support deregistering instance automatically.
[#176] Naming client query instance method should bypass local cache at client start.
[#177] Console supports registering new empty service and delete empty service.
[#181] NPE when adding a instance if no leader in the raft cluster.
[#193] Configure host domain name cause nacos server cluster is unavailable.
[#209] Disable service and cluster level customization in client registerInstance method.
[#214] Please support Java 11.
[#222] print more nacos server start status info in start.log.
[#231] Refactoring: Parsing the Nacos home directory and the cluster.conf file.
[#246] "mvn -B clean apache-rat:check findbugs:findbugs" did not work as expected.
[#251] Console Editor Optimization.
[#254] DataId and group are required in historical version and listener query.
[#256] Whether the service discovery data needs to add a newline link symbol.
[#257] Listening query switching query dimension data is not refreshed.
[#258] Remove the Balloon of DataId/Group.
[#259] Listening query paging size problem.
[#272] "#it is ip" is also parsed into an instance IP.
[#275] nacos coredns plugin to support DNS.
[#281] We should lint the console code.
[#302] Maven build project supports java 11.
[#316] In stand alone mode, Nacos still checks the cluster.conf.

## 0.4.0(Nov 7, 2018)

[#216] Fix tenant dir problem
[#197] Service update ignored some properties
[#190] Client beat lose weight info and metadata info
[#188] Console delete data cannot be updated in time
[#179] Listening query fail when namespace is not blank
[#157] Lack information in readme.md to describe the related project repositories for Nacos echosystem
[#144] There have a error and something are not clear
[#106] Snapshot file create error
[#92] Eliminate warnings, refactor code, show start.log detail


## 0.3.0(Oct 26, 2018)

[#171] UI debug errors
[#156] Web UI 404 problem
[#155] use local resource
[#145] nacos-example not found :org.apache.logging.log4j.core.Logger
[#142] UI console show Group
[#149] Fix naming client beat process failed bug.
[#150] Fix naming service registration hangs bug.

## 0.3.0-RC1(Oct 19, 2018)

[#33] Support console for config management.
[#51] Support console for naming service.
[#121] Fix get instance method hanging bug.
[#138] Add a flag to indicate if instance is offline.
[#130] Fix health check disabled if machine has one CPU core bug.
[#139] Fix still get instance with zero weight bug.
[#128] Fix console layout bug.



## 0.2.1-release(Sept 28, 2018)

* FIx deregister last instance failed error.
* Fix url pattern error.
* Fully integrate with and seamlessly support Spring framework, Spring Boot and Spring Cloud
* Separate nacos-api from nacos client implementation
* Support high available cluster mode
* Fix cluster node health check abnormality
* Fix stand-alone mode gets the change history list exception
* Fix Pulling does not exist configuration print io exception
* Optimized log framework
* Service Discovery: Client support getting server status.
* Service Discovery: Client support get all service names of server.
* Service Discovery: Client support get all subscribed services.

## 0.2.0 (Sept 17, 2018)

#### FEATURES:

* separate nacos-api from nacos client implementation
* Cluster node health check abnormality
* Stand-alone mode gets the change history list exception
* Pulling does not exist configuration print io exception
* Optimized log framework
* Service Discovery: Client support getting server status.
* Service Discovery: Client support get all service names of server.
* Service Discovery: Client support get all subscribed services.


#### IMPROVEMENTS:

#### BUG FIXES:

#### BREAKING CHANGES:



## 0.1.0 (July 18, 2018)

#### FEATURES:

* Creating, deleting, modifying, and querying configurations: the core functionalities.
* Multiple languages support: supports Java/Shell/HTTP OpenAPI.
* Service Discovery: Basic service registry and discovery.
* Service Discovery: Service load balancing using instance weights, protect threshold and instance health statuses.
* Service Discovery: Supports four ways for health check: http, tcp, mysql and client heartbeat.
* Service Discovery: CRUD operations on service instances through Java client and open API.
* Service Discovery: Service subscribtion and push through Java client.
* Nacos official website is coming. https://nacos.io/



#### IMPROVEMENTS:

#### BUG FIXES:

#### BREAKING CHANGES:
