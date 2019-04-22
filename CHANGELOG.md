## 1.0.0-RC4(Mar 22, 2019)
* [#923] Nacos 1.0.0 compatible with nacos-client 0.6.2
* [#938] Client beat processor task lost
* [#946] Change default server mode to AP



## 1.0.0-RC1(Mar 15, 2019)

* [#870] About Nacos's namespace and tenant design
* [#869] Client exception message is confusing
* [#866] BeatInfo scheduled property may have the memory visibility issue
* [#865] checksum value is not correct
* [#839] Refactor API URLs 
* [#811] ApiCommands.updateIpPublish countDownLatch timeout issue
* [#809] Instance field 'valid' should be deprecated and replaced by 'healthy'
* [#803] Nacos front-end function regression plan and landing
* [#801] Nacos uses nignx as a best practice article for current limiting.
* [#757] The word 'domain' should be replaced by 'service'
* [#745] Support server running mode in CP, AP or mixed
* [#744] The exact status of server should be stored and controlled
* [#725] Will the nacos registry be how to do multi-environment deployment?
* [#677] Support ephemeral instances and persistent instances
* [#651] Remove old API entry 'APICommands'
* [#650] Refactor server list management to make it irrelevant to consistency protocol
* [#634] Add global push enable switch and data query enable switch
* [#629] Server data needs warm up before open traffic
* [#502] Registering ephemeral instance as well as persistent instances
* [#501] Health check mode confict when building muilt clusters whit nacos sync + nacos
* [#479] Metadata should be displayed and edited using standard property syntax
* [#327] Inform the ACM SDK of the RAM role name and access the configuration ? ?
* [#269] need to support service group in naming module

## 0.9.0(Feb 28, 2019)

* [#840] Nacos server adds startup mode to distinguish between config and naming.
* [#762] Register instance returns failed when the health check mode is 'server' in standalone mode.
* [#473] Nacos Cluster Mode kubernate Startup nacos.log error Log.
* [#240] Log strong dependence problem.
* [#824] getServicesOfServer throws exception if service list is empty.
* [#802] Nacos server multi-boot mode support.
* [#800] Nacos's client-to-server addressing mode document introduction.
* [#768] The interval at which the heartbeat is sent in BeatReactor is not controlled by the server return value.
* [#759] why instance can't auto-delete.
* [#756] Format of instance and service should be validated.
* [#720] Memory leak in PushService.
* [#653] IoUtils under nacos-common-0.2.1-RC1.jar lacks "" judgment on encoding.
* [#588] Client compatible to jdk1.6.

## 0.8.0(Jan 22, 2019) PRE-GA

* [#162] Support open metrics and prometheus
* [#268] Health check is performed in the Nacos startup script
* [#320] Nacos supports multiple configuration files, configuration template abstraction and inheritance
* [#333] Use nacos in k8s to get hostname exception
* [#335] update nacos.io docker img priority/low
* [#339] Project language problem identified in github
* [#381] Discuss：How to support Login
* [#397] Some questions for Nacos
* [#402] When the configuration is added or edited, the edit box will not come out 
* [#462] Nacos monitor discuss (0.8 version)
* [#496] Warning log printing when quering a nonexistent service
* [#497] Make subscription of service triggered by getInstance method optional
* [#498] Support namespace for service discovery
* [#499] When the configuration is newly created (if data-id and group already exist), the original configuration will be overwritten
* [#512] nacos-logs start.out always print 8848 (but port can be changed)
* [#514] Nacos 0.7 not support namespace
* [#523] Add a switch to control server detection and client reporting heartbeat switching
* [#526] Possible data loss in server side health check mode
* [#527] Many repeat client beat tasks can be generated
* [#558] Enable access log recording by default 
* [#560] Nacos server startup issues
* [#579] New API support - “update health in none health check mode through api”
* [#587] Client sends request concurrently
* [#592] Service restful interface put/post is reversed
* [#599] getSubscribeServices method gets services that were deregistered
* [#603] Format log of naming module 
* [#609] Always print a NPE log at start
* [#663] Nacos update instance info NPE
* [#668] 0.8.0-SNAPSHOT naming heartbeat not compatible with lower version client
* [#672] Startup.cmd bug

## 0.7.0(Dec, 2018)

* [ #461 ] Registration failed when instance port is set to 0
* [ #455 ] The console can't change the change code
* [ #447 ] 集群模式server挂掉一台后，提供方注册失败
* [ #445 ] 0.6.1控制台创建配置发布提交时，提示信息有问题
* [ #442 ] Typos in class names and variables.
* [ #413 ] The console has some uncaught exceptions
* [ #395 ] nacos surport mysql in the case of stand-alone mode
* [ #393 ] Support operation of selector on console
* [ #365 ] NodeJs SDK support
* [ #362 ] The metadata will lost when online or offline instance through web ui
* [ #187 ] Provide Label ability for Naming Service into NACOS for complex multi-DC scenario.

## 0.6.1(Dec, 2018)

* [#421] NamingService's serivce name can't use colon(:) in Windows
* [#432] When packing nacos-core, ${user.home} is replaced in the logback configuration file (nacos.xml)

## 0.6.0(Dec, 2018)

* [#388] Cluster name should be provided in the Instance
* [#377] Clean up messy code in Naming module
* [#369] Support instance list persisted on disk
* [#366] findbugs-maven-plugin version
* [#362] The metadata will lost when online or offline instance through web ui
* [#352] Refactoring internationalization Nacos console
* [#278] Nacos docker img
* [#243] optimize the efficiency of integration testing, it’s taking too long now

## 0.5.0(Nov, 2018)

* [#148] Naming write performace.
* [#175] Support deregistering instance automatically.
* [#176] Naming client query instance method should bypass local cache at client start.
* [#177] Console supports registering new empty service and delete empty service.
* [#181] NPE when adding a instance if no leader in the raft cluster.
* [#193] Configure host domain name cause nacos server cluster is unavailable.
* [#209] Disable service and cluster level customization in client registerInstance method.
* [#214] Please support Java 11.
* [#222] print more nacos server start status info in start.log.
* [#231] Refactoring: Parsing the Nacos home directory and the cluster.conf file.
* [#246] "mvn -B clean apache-rat:check findbugs:findbugs" did not work as expected.
* [#251] Console Editor Optimization.
* [#254] DataId and group are required in historical version and listener query.
* [#256] Whether the service discovery data needs to add a newline link symbol.
* [#257] Listening query switching query dimension data is not refreshed.
* [#258] Remove the Balloon of DataId/Group.
* [#259] Listening query paging size problem.
* [#272] "#it is ip" is also parsed into an instance IP.
* [#275] nacos coredns plugin to support DNS.
* [#281] We should lint the console code.
* [#302] Maven build project supports java 11.
* [#316] In stand alone mode, Nacos still checks the cluster.conf.

## 0.4.0(Nov 7, 2018)

* [#216] Fix tenant dir problem
* [#197] Service update ignored some properties
* [#190] Client beat lose weight info and metadata info
* [#188] Console delete data cannot be updated in time
* [#179] Listening query fail when namespace is not blank
* [#157] Lack information in readme.md to describe the related project repositories for Nacos echosystem
* [#144] There have a error and something are not clear
* [#106] Snapshot file create error
* [#92] Eliminate warnings, refactor code, show start.log detail


## 0.3.0(Oct 26, 2018)

* [#171] UI debug errors
* [#156] Web UI 404 problem
* [#155] use local resource
* [#145] nacos-example not found :org.apache.logging.log4j.core.Logger
* [#142] UI console show Group
* [#149] Fix naming client beat process failed bug.
* [#150] Fix naming service registration hangs bug.

## 0.3.0-RC1(Oct 19, 2018)

* [#33] Support console for config management.
* [#51] Support console for naming service.
* [#121] Fix get instance method hanging bug.
* [#138] Add a flag to indicate if instance is offline.
* [#130] Fix health check disabled if machine has one CPU core bug.
* [#139] Fix still get instance with zero weight bug.
* [#128] Fix console layout bug.



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
