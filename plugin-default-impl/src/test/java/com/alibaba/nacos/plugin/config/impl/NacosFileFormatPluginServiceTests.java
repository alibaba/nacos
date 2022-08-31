/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.plugin.config.impl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * tests for file format type validate.
 *
 * @author liyunfei
 */
@RunWith(MockitoJUnitRunner.class)
public class NacosFileFormatPluginServiceTests {
    
    private String textContent = "text123";
    
    private String jsonContent = "{\"hello\":\"hello\"}";
    
    private String propertiesContent = "#\n" + "# Copyright 1999-2018 Alibaba Group Holding Ltd.\n" + "#\n"
            + "# Licensed under the Apache License, Version 2.0 (the \"License\");\n"
            + "# you may not use this file except in compliance with the License.\n"
            + "# You may obtain a copy of the License at\n" + "#\n"
            + "#      http://www.apache.org/licenses/LICENSE-2.0\n" + "#\n"
            + "# Unless required by applicable law or agreed to in writing, software\n"
            + "# distributed under the License is distributed on an \"AS IS\" BASIS,\n"
            + "# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
            + "# See the License for the specific language governing permissions and\n"
            + "# limitations under the License.\n" + "#\n" + "\n"
            + "#*************** Spring Boot Related Configurations ***************#\n"
            + "### Default web context path:\n" + "server.servlet.contextPath=/nacos\n"
            + "### Default web server port:\n" + "server.port=8851\n"
            + "#*************** Network Related Configurations ***************#\n"
            + "### If prefer hostname over ip for Nacos server addresses in cluster.conf:\n"
            + "# nacos.inetutils.prefer-hostname-over-ip=false\n" + "\n" + "### Specify local server's IP:\n"
            + "# nacos.inetutils.ip-address=\n" + "\n" + "\n"
            + "#*************** Config Module Related Configurations ***************#\n"
            + "### If use MySQL as datasource:\n" + " spring.datasource.platform=mysql\n" + "\n" + "### Count of DB:\n"
            + " db.num=1\n" + "\n" + "## Connect URL of DB:\n"
            + " db.url.0=jdbc:mysql://47.98.99.88:3306/nacos_config?characterEncoding=utf8&connectTimeout=1000&"
            + "socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC\n"
            + " db.user.0=practice\n" + " db.password.0=123456\n" + "\n"
            + "#*************** Naming Module Related Configurations ***************#\n"
            + "### Data dispatch task execution period in milliseconds:\n"
            + "# nacos.naming.distro.taskDispatchPeriod=200\n" + "\n" + "### Data count of batch sync task:\n"
            + "# nacos.naming.distro.batchSyncKeyCount=1000\n" + "\n"
            + "### Retry delay in milliseconds if sync task failed:\n" + "# nacos.naming.distro.syncRetryDelay=5000\n"
            + "### If enable data warmup. If set to false, the server would accept request without local data "
            + "preparation:\n" + "# nacos.naming.data.warmup=true\n" + "\n"
            + "### If enable the instance auto expiration, kind like of health check of instance:\n"
            + "# nacos.naming.expireInstance=true\n" + "\n" + "nacos.naming.empty-service.auto-clean=true\n"
            + "nacos.naming.empty-service.clean.initial-delay-ms=50000\n"
            + "nacos.naming.empty-service.clean.period-time-ms=30000\n" + "\n" + "\n"
            + "#*************** CMDB Module Related Configurations ***************#\n"
            + "### The interval to dump external CMDB in seconds:\n" + "# nacos.cmdb.dumpTaskInterval=3600\n" + "\n"
            + "### The interval of polling data change event in seconds:\n" + "# nacos.cmdb.eventTaskInterval=10\n"
            + "\n" + "### The interval of loading labels in seconds:\n" + "# nacos.cmdb.labelTaskInterval=300\n" + "\n"
            + "### If turn on data loading task:\n" + "# nacos.cmdb.loadDataAtStart=false\n" + "\n" + "\n"
            + "#*************** Metrics Related Configurations ***************#\n" + "### Metrics for prometheus\n"
            + "#management.endpoints.web.exposure.include=*\n" + "\n" + "### Metrics for elastic search\n"
            + "management.metrics.export.elastic.enabled=false\n"
            + "#management.metrics.export.elastic.host=http://localhost:9200\n" + "\n" + "### Metrics for influx\n"
            + "management.metrics.export.influx.enabled=false\n" + "#management.metrics.export.influx.db=springboot\n"
            + "#management.metrics.export.influx.uri=http://localhost:8086\n"
            + "#management.metrics.export.influx.auto-create-db=true\n"
            + "#management.metrics.export.influx.consistency=one\n"
            + "#management.metrics.export.influx.compressed=true\n" + "\n"
            + "#*************** Access Log Related Configurations ***************#\n"
            + "### If turn on the access log:\n" + "server.tomcat.accesslog.enabled=true\n" + "\n"
            + "### accesslog automatic cleaning time\n" + "server.tomcat.accesslog.max-days=30\n" + "\n"
            + "### The access log pattern:\n"
            + "server.tomcat.accesslog.pattern=%h %l %u %t \"%r\" %s %b %D %{User-Agent}i %{Request-Source}i\n" + "\n"
            + "### The directory of access log:\n" + "server.tomcat.basedir=file:.\n"
            + "#spring.datasource.platform=mysql\n" + "#db.num=1\n"
            + "#db.url.0=jdbc:mysql://10.101.167.27:3306/acm?characterEncoding=utf8&connectTimeout=1000"
            + "&socketTimeout=10000&" + "autoReconnect=true\n" + "#db.user=root\n" + "#db.password=root\n"
            + "#*************** Access Control Related Configurations ***************#\n"
            + "### If enable spring security, this option is deprecated in 1.2.0:\n"
            + "#spring.security.enabled=false\n" + "\n" + "### The ignore urls of auth, is deprecated in 1.2.0:\n"
            + "nacos.security.ignore.urls=/,/error,/**/*.css,/**/*.js,/**/*.html,/**/*.map,/**/*.svg,/**/*.png,"
            + "/**/*.ico,/console-ui/public/**,/v1/auth/**,/v1/console/health/**,/actuator/**,/v1/console/server/**\n"
            + "\n" + "### The auth system to use, currently only 'nacos' and 'ldap' is supported:\n"
            + "nacos.core.auth.system.type=nacos\n" + "\n" + "### If turn on auth system:\n"
            + "nacos.core.auth.enabled=false\n" + "\n"
            + "### Turn on/off caching of auth information. By turning on this switch, the update of auth information"
            + " would have a 15 seconds delay.\n" + "nacos.core.auth.caching.enabled=true\n" + "\n"
            + "### Since 1.4.1, Turn on/off white auth for user-agent: nacos-server, only for upgrade from old version.\n"
            + "nacos.core.auth.enable.userAgentAuthWhite=false\n" + "\n"
            + "### Since 1.4.1, worked when nacos.core.auth.enabled=true and nacos.core.auth.enable.userAgentAuthWhite=false.\n"
            + "### The two properties is the white list for auth and used by identity the request from other server.\n"
            + "nacos.core.auth.server.identity.key=serverIdentity\n"
            + "nacos.core.auth.server.identity.value=security\n" + "\n"
            + "### worked when nacos.core.auth.system.type=nacos\n" + "### The token expiration in seconds:\n"
            + "nacos.core.auth.plugin.nacos.token.expire.seconds=18000\n" + "### The default token:\n"
            + "nacos.core.auth.plugin.nacos.token.secret.key=SecretKey01234567890123456789012345678901234567890"
            + "1234567890123456789\n" + "\n" + "# todo 配置推送地址的位置--web进行配置及数量的考虑 rocketmq+netty通信推送--URL需要校验？\n"
            + "### configuration changes plugin\n" + "# webhook\n" + "nacos.core.config.plugin.webhook.enabled=true\n"
            + "# default or self\n" + "nacos.core.config.plugin.webhook.way=nacos\n" + "# ding talk,wechat ,lark\n"
            + "nacos.core.config.plugin.webhook.type=eventBridge\n"
            + "nacos.core.config.plugin.webhook.eventbridge.accessKeyId=\n"
            + "nacos.core.config.plugin.webhook.eventbridge.accessKeySecret=\n"
            + "nacos.core.config.plugin.webhook.eventbridge.endpoint=1017438417648207.eventbridge.cn-hangzhou.aliyuncs.com\n"
            + "nacos.core.config.plugin.webhook.eventbridge.eventbus=demo-bus\n"
            + "nacos.core.config.plugin.webhook.eventbridge.source=webhook.event\n" + "\n" + "# dingTalk\n"
            + "# dingTalk\n"
            + "nacos.core.config.plugin.webhook.url=https://oapi.dingtalk.com/robot/send?access_token=a5ddc8240703732ed22224864b"
            + "80c30ab6270bdf42da1c9299efc1567d789\n" + "\n"
            + "# https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=19d192c9-07fc-4b1f-ad2e-5549ef4f939e\n"
            + "# https://oapi.dingtalk.com/robot/send?access_token=a5ddc8240703732ed2fd02224864b80c30ab6270bdf42da1c9299efc1\n"
            + "\n" + "spring.message.enable=true\n"
            + "spring.message.wechat-webhooks=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=19d192c9-07fc-4b1f-ad2e-5549e\n"
            + "\n" + "# whitelist\n" + "nacos.core.config.plugin.whiteList.enabled=true\n"
            + "nacos.core.config.plugin.whiteList.way=nacos\n"
            + "nacos.core.config.plugin.whiteList.urls=xml,text,properties,html\n" + "\n" + "# fileCheck\n"
            + "nacos.core.config.plugin.fileformatcheck.enabled=true\n"
            + "nacos.core.config.plugin.fileformatcheck.way=nacos\n" + "\n"
            + "### worked when nacos.core.auth.system.type=ldap，{0} is Placeholder,replace login username\n"
            + "#nacos.core.auth.ldap.url=ldap://localhost:389\n" + "#nacos.core.auth.ldap.basedc=dc=example,dc=org\n"
            + "#nacos.core.auth.ldap.userDn=cn=admin,${nacos.core.auth.ldap.basedc}\n"
            + "#nacos.core.auth.ldap.password=admin\n" + "#nacos.core.auth.ldap.userdn=cn={0},dc=example,dc=org\n"
            + "\n" + "\n" + "\n" + "#*************** Istio Related Configurations ***************#\n"
            + "### If turn on the MCP server:\n" + "nacos.istio.mcp.server.enabled=false\n" + "\n" + "\n" + "\n"
            + "###*************** Add from 1.3.0 ***************###\n" + "\n" + "\n"
            + "#*************** Core Related Configurations ***************#\n" + "\n"
            + "### set the WorkerID manually\n" + "# nacos.core.snowflake.worker-id=\n" + "\n" + "### Member-MetaData\n"
            + "# nacos.core.member.meta.site=\n" + "# nacos.core.member.meta.adweight=\n"
            + "# nacos.core.member.meta.weight=\n" + "\n" + "### MemberLookup\n"
            + "### Addressing pattern category, If set, the priority is highest\n"
            + "# nacos.core.member.lookup.type=[file,address-server]\n"
            + "## Set the cluster list with a configuration file or command-line argument\n"
            + "# nacos.member.list=192.168.16.101:8847?raft_port=8807,192.168.16.101?raft_port=8808,192.168.16.101:8849?raft_port=8809\n"
            + "# nacos.member.list=127.0.0.1:8847?raft_port=8807,127.0.0.1:8848?raft_port=8808,127.0.0.1:8849?raft_port=8809\n"
            + "# nacos.member.list=192.168.16.101:8847?raft_port=8807,192.168.16.101?raft_port=8808,192.168.16.101:8849?raft_port=8809\n"
            + "  nacos.member.list=192.168.16.101:8847,192.168.16.101:8849,192.168.16.101:8851\n" + "\n"
            + "# 邻近端口占用？？\n" + "## for AddressServerMemberLookup\n"
            + "# Maximum number of retries to query the address server upon initialization\n"
            + "# nacos.core.address-server.retry=5\n" + "## Server domain name address of [address-server] mode\n"
            + "# address.server.domain=jmenv.tbsite.net\n" + "## Server port of [address-server] mode\n"
            + "# address.server.port=8080\n" + "## Request address of [address-server] mode\n"
            + "# address.server.url=/nacos/serverlist\n" + "\n"
            + "#*************** JRaft Related Configurations ***************#\n" + "\n"
            + "### Sets the Raft cluster election timeout, default value is 5 second\n"
            + "# nacos.core.protocol.raft.data.election_timeout_ms=5000\n"
            + "### Sets the amount of time the Raft snapshot will execute periodically, default is 30 minute\n"
            + "# nacos.core.protocol.raft.data.snapshot_interval_secs=30\n" + "### raft internal worker threads\n"
            + "# nacos.core.protocol.raft.data.core_thread_num=8\n"
            + "### Number of threads required for raft business request processing\n"
            + "# nacos.core.protocol.raft.data.cli_service_thread_num=4\n"
            + "### raft linear read strategy. Safe linear reads are used by default, the Leader tenure is confirmed by heartbeat\n"
            + "# nacos.core.protocol.raft.data.read_index_type=ReadOnlySafe\n"
            + "### rpc request timeout, default 5 seconds\n"
            + "# nacos.core.protocol.raft.data.rpc_request_timeout_ms=5000\n" + "\n";
    
    private String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<!--\n"
            + "  ~ Copyright 1999-2021 Alibaba Group Holding Ltd.\n" + "  ~\n"
            + "  ~ Licensed under the Apache License, Version 2.0 (the \"License\");\n"
            + "  ~ you may not use this file except in compliance with the License.\n"
            + "  ~ You may obtain a copy of the License at\n" + "  ~\n"
            + "  ~      http://www.apache.org/licenses/LICENSE-2.0\n" + "  ~\n"
            + "  ~ Unless required by applicable law or agreed to in writing, software\n"
            + "  ~ distributed under the License is distributed on an \"AS IS\" BASIS,\n"
            + "  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
            + "  ~ See the License for the specific language governing permissions and\n"
            + "  ~ limitations under the License.\n" + "  -->\n" + "\n"
            + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
            + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "    xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
            + "    <parent>\n" + "        <artifactId>nacos-all</artifactId>\n"
            + "        <groupId>com.alibaba.nacos</groupId>\n" + "        <version>${revision}</version>\n"
            + "    </parent>\n" + "    <modelVersion>4.0.0</modelVersion>\n" + "    \n"
            + "    <artifactId>nacos-plugin-default-impl</artifactId>\n" + "    <packaging>jar</packaging>\n"
            + "    <name>nacos-plugin-default-impl ${project.version}</name>\n" + "    <url>http://nacos.io</url>\n"
            + "\n" + "    <dependencies>\n" + "        <dependency>\n"
            + "            <groupId>com.alibaba.nacos</groupId>\n"
            + "            <artifactId>nacos-auth-plugin</artifactId>\n" + "        </dependency>\n"
            + "        <dependency>\n" + "            <groupId>com.alibaba.nacos</groupId>\n"
            + "            <artifactId>nacos-config-plugin</artifactId>\n" + "        </dependency>\n"
            + "        <dependency>\n" + "            <groupId>com.alibaba.nacos</groupId>\n"
            + "            <artifactId>nacos-common</artifactId>\n" + "        </dependency>\n" + "        \n"
            + "        <dependency>\n" + "            <groupId>com.alibaba.nacos</groupId>\n"
            + "            <artifactId>nacos-sys</artifactId>\n" + "            <scope>provided</scope>\n"
            + "        </dependency>\n" + "    \n" + "        <dependency>\n"
            + "            <groupId>com.alibaba.nacos</groupId>\n"
            + "            <artifactId>nacos-config</artifactId>\n" + "            <scope>provided</scope>\n"
            + "        </dependency>\n" + "    \n" + "        <dependency>\n"
            + "            <groupId>io.jsonwebtoken</groupId>\n" + "            <artifactId>jjwt-api</artifactId>\n"
            + "        </dependency>\n" + "        <dependency>\n" + "            <groupId>io.jsonwebtoken</groupId>\n"
            + "            <artifactId>jjwt-impl</artifactId>\n" + "            <scope>runtime</scope>\n"
            + "        </dependency>\n" + "        <dependency>\n" + "            <groupId>io.jsonwebtoken</groupId>\n"
            + "            <artifactId>jjwt-jackson</artifactId>\n" + "            <scope>runtime</scope>\n"
            + "        </dependency>\n" + "        <dependency>\n"
            + "            <groupId>org.springframework.ldap</groupId>\n"
            + "            <artifactId>spring-ldap-core</artifactId>\n" + "        </dependency>\n" + "\n"
            + "        <dependency>\n" + "            <groupId>io.github.swalikh</groupId>\n"
            + "            <artifactId>wework-wehook-starter</artifactId>\n" + "            <version>1.0.0</version>\n"
            + "        </dependency>\n" + "\n" + "        <dependency>\n"
            + "            <groupId>com.aliyun</groupId>\n"
            + "            <artifactId>alibaba-dingtalk-service-sdk</artifactId>\n"
            + "            <version>1.0.1</version>\n" + "        </dependency>\n" + "    \n" + "        <dependency>\n"
            + "            <groupId>com.aliyun</groupId>\n"
            + "            <artifactId>eventbridge-client</artifactId>\n" + "            <version>1.2.6</version>\n"
            + "        </dependency>\n" + "    </dependencies>\n" + "</project>\n";
    
    private String htmlContent = "<!--\n" + "  ~ Copyright 1999-2018 Alibaba Group Holding Ltd.\n" + "  ~\n"
            + "  ~ Licensed under the Apache License, Version 2.0 (the \"License\");\n"
            + "  ~ you may not use this file except in compliance with the License.\n"
            + "  ~ You may obtain a copy of the License at\n" + "  ~\n"
            + "  ~      http://www.apache.org/licenses/LICENSE-2.0\n" + "  ~\n"
            + "  ~ Unless required by applicable law or agreed to in writing, software\n"
            + "  ~ distributed under the License is distributed on an \"AS IS\" BASIS,\n"
            + "  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
            + "  ~ See the License for the specific language governing permissions and\n"
            + "  ~ limitations under the License.\n" + "  -->\n" + "\n" + "<!DOCTYPE html>\n" + "\n"
            + "<html lang=\"en\">\n" + "\n" + "<head>\n" + "\t<meta charset=\"UTF-8\">\n"
            + "\t<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
            + "\t<meta http-equiv=\"X-UA-Compatible\" content=\"ie=edge\">\n"
            + "\t<meta http-equiv=\"Cache-Control\" content=\"no-cache, no-store, must-revalidate\">\n"
            + "  <meta http-equiv=\"Pragma\" content=\"no-cache\">\n"
            + "  <meta http-equiv=\"Expires\" content=\"0\">\n" + "\t<title>Nacos</title>\n"
            + "\t<link rel=\"shortcut icon\" href=\"console-ui/public/img/nacos-logo.png\" type=\"image/x-icon\">\n"
            + "  <link rel=\"stylesheet\" type=\"text/css\" href=\"console-ui/public/css/bootstrap.css\">\n"
            + "\t<link rel=\"stylesheet\" type=\"text/css\" href=\"console-ui/public/css/console1412.css\">\n"
            + "\t<!-- 第三方css开始 -->\n"
            + "\t<link rel=\"stylesheet\" type=\"text/css\" href=\"console-ui/public/css/codemirror.css\">\n"
            + "\t<link rel=\"stylesheet\" type=\"text/css\" href=\"console-ui/public/css/merge.css\">\n"
            + "\t<link rel=\"stylesheet\" type=\"text/css\" href=\"console-ui/public/css/icon.css\">\n"
            + "\t<link rel=\"stylesheet\" type=\"text/css\" href=\"console-ui/public/css/font-awesome.css\">\n"
            + "\t<!-- 第三方css结束 -->\n"
            + "<link href=\"./css/main.css?7449a5cb967e58fecbaa\" rel=\"stylesheet\"></head>\n" + "\n" + "<body>\n"
            + "\t<div id=\"root\" style=\"overflow:hidden\"></div>\n" + "\t<div id=\"app\"></div>\n"
            + "\t<div id=\"other\"></div>\n" + "\n" + "\t<!-- 第三方js开始 -->\n"
            + "\t<script src=\"console-ui/public/js/jquery.js\"></script>\n"
            + "\t<script src=\"console-ui/public/js/codemirror.js\"></script>\n"
            + "\t<script src=\"console-ui/public/js/javascript.js\"></script>\n"
            + "\t<script src=\"console-ui/public/js/xml.js\"></script>\n"
            + "\t<script src=\"console-ui/public/js/codemirror.addone.fullscreen.js\"></script>\n"
            + "\t<script src=\"console-ui/public/js/codemirror.addone.lint.js\"></script>\n"
            + "\t<script src=\"console-ui/public/js/codemirror.lib.json-lint.js\"></script>\n"
            + "\t<script src=\"console-ui/public/js/codemirror.addone.json-lint.js\"></script>\n"
            + "\t<script src=\"console-ui/public/js/codemirror.lib.clike-lint.js\"></script>\n"
            + "\t<script src=\"console-ui/public/js/diff_match_patch.js\"></script>\n"
            + "\t<script src=\"console-ui/public/js/merge.js\"></script>\n"
            + "\t<script src=\"console-ui/public/js/loader.js\"></script>\n" + "\t<!-- 第三方js结束 -->\n"
            + "<script type=\"text/javascript\" src=\"./js/main.js?7449a5cb967e58fecbaa\"></script></body>\n" + "\n"
            + "</html>\n";
    
    private String yamlContent = "hello: hello";
    
    @Test
    public void testValidateText() {
        String type = "text";
        boolean rs = NacosFileFormatPluginService.validate(textContent, type);
        Assert.assertTrue(rs);
        rs = NacosFileFormatPluginService.validate(jsonContent, type);
        Assert.assertTrue(rs);
        rs = NacosFileFormatPluginService.validate(xmlContent, type);
        Assert.assertTrue(rs);
        rs = NacosFileFormatPluginService.validate(propertiesContent, type);
        Assert.assertTrue(rs);
        rs = NacosFileFormatPluginService.validate(yamlContent, type);
        Assert.assertTrue(rs);
        rs = NacosFileFormatPluginService.validate(htmlContent, type);
        Assert.assertTrue(rs);
    }
    
    @Test
    public void testValidateJson() {
        String type = "json";
        boolean rs = NacosFileFormatPluginService.validate(jsonContent, type);
        Assert.assertTrue(rs);
        rs = NacosFileFormatPluginService.validate(xmlContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(propertiesContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(textContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(htmlContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(yamlContent, type);
        Assert.assertFalse(rs);
    }
    
    @Test
    public void testValidateXml() {
        String type = "xml";
        boolean rs = NacosFileFormatPluginService.validate(xmlContent, type);
        Assert.assertTrue(rs);
        rs = NacosFileFormatPluginService.validate(jsonContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(propertiesContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(textContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(htmlContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(yamlContent, type);
        Assert.assertFalse(rs);
    }
    
    @Test
    public void testValidateHtml() {
        String type = "html";
        boolean rs = NacosFileFormatPluginService.validate(htmlContent, type);
        Assert.assertTrue(rs);
        rs = NacosFileFormatPluginService.validate(jsonContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(propertiesContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(textContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(yamlContent, type);
        Assert.assertFalse(rs);
        rs = NacosFileFormatPluginService.validate(xmlContent, type);
        Assert.assertTrue(rs);
    }
    
    @Test
    public void testValidateProperties() {
        String type = "properties";
        boolean rs = NacosFileFormatPluginService.validate(propertiesContent, type);
        Assert.assertTrue(rs);
    }
    
}