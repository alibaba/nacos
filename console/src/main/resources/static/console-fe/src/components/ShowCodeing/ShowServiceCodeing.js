/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import { getParams } from '../../globalLib';
import { ConfigProvider, Dialog, Loading, Tab } from '@alifd/next';

import './index.scss';

const TabPane = Tab.Item;
/**
 *
 * 服务注册示例代码展示窗口
 * @author yongchao9  #2019年05月18日 下午4:26:19
 *
 */
@ConfigProvider.config
class ShowServiceCodeing extends React.Component {
  static displayName = 'ShowServiceCodeing';

  static propTypes = {
    locale: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      dialogvisible: false,
      loading: false,
    };
    this.defaultCode = '';
    this.nodejsCode = 'TODO';
    this.cppCode = 'TODO';
    this.shellCode = 'TODO';
    this.pythonCode = 'TODO';
    this.record = {};
    this.sprigboot_code = '/* Refer to document: https://github.com/nacos-group/nacos-examples/blob/master/nacos-spring-boot-example/nacos-spring-boot-discovery-example\n' +
      '*  pom.xml\n' +
      '\t<dependency>\n' +
      '\t    <groupId>com.alibaba.boot</groupId>\n' +
      '\t    <artifactId>nacos-discovery-spring-boot-starter</artifactId>\n' +
      '\t    <version>0.2.1</version>\n' +
      '\t</dependency>\n' +
      '*/\n' +
      '/* Refer to document:  https://github.com/nacos-group/nacos-examples/blob/master/nacos-spring-boot-example/nacos-spring-boot-discovery-example/src/main/resources\n' +
      '* application.properties\n' +
      '   nacos.discovery.server-addr=127.0.0.1:8848\n' +
      '*/    \n' +
      '// Refer to document: https://github.com/nacos-group/nacos-examples/blob/master/nacos-spring-boot-example/nacos-spring-boot-discovery-example/src/main/java/com/alibaba/nacos/example/spring/boot/controller\n' +
      '\n' +
      'package com.alibaba.nacos.example.spring.boot.controller;\n' +
      '\n' +
      'import com.alibaba.nacos.api.annotation.NacosInjected;\n' +
      'import com.alibaba.nacos.api.exception.NacosException;\n' +
      'import com.alibaba.nacos.api.naming.NamingService;\n' +
      'import com.alibaba.nacos.api.naming.pojo.Instance;\n' +
      'import org.springframework.stereotype.Controller;\n' +
      'import org.springframework.web.bind.annotation.RequestMapping;\n' +
      'import org.springframework.web.bind.annotation.RequestParam;\n' +
      'import org.springframework.web.bind.annotation.ResponseBody;\n' +
      '\n' +
      'import java.util.List;\n' +
      '\n' +
      'import static org.springframework.web.bind.annotation.RequestMethod.GET;\n' +
      '\n' +
      '@Controller\n' +
      '@RequestMapping("discovery")\n' +
      'public class DiscoveryController {\n' +
      '\n' +
      '    @NacosInjected\n' +
      '    private NamingService namingService;\n' +
      '\n' +
      '    @RequestMapping(value = "/get", method = GET)\n' +
      '    @ResponseBody\n' +
      '    public List<Instance> get(@RequestParam String serviceName) throws NacosException {\n' +
      '        return namingService.getAllInstances(serviceName);\n' +
      '    }\n' +
      '}';
    this.sprigcloud_code = `/* Refer to document: https://github.com/nacos-group/nacos-examples/blob/master/nacos-spring-cloud-example/nacos-spring-cloud-discovery-example/
*  pom.xml
\t<dependency>
\t        <groupId>org.springframework.cloud</groupId>
\t        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
\t        <version>0.2.1.RELEASE</version>
\t</dependency>
*/

// nacos-spring-cloud-provider-example

/* Refer to document:  https://github.com/nacos-group/nacos-examples/tree/master/nacos-spring-cloud-example/nacos-spring-cloud-discovery-example/nacos-spring-cloud-provider-example/src/main/resources
* application.properties
server.port=18080
spring.application.name=service-provider
spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848
*/    

// Refer to document: https://github.com/nacos-group/nacos-examples/tree/master/nacos-spring-cloud-example/nacos-spring-cloud-discovery-example/nacos-spring-cloud-provider-example/src/main/java/com/alibaba/nacos/example/spring/cloud
package com.alibaba.nacos.example.spring.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xiaojing
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NacosProviderApplication {

\tpublic static void main(String[] args) {
\t\tSpringApplication.run(NacosProviderApplication.class, args);
\t}

\t@RestController
\tclass EchoController {
\t\t@RequestMapping(value = "/echo/{string}", method = RequestMethod.GET)
\t\tpublic String echo(@PathVariable String string) {
\t\t\treturn "Hello Nacos Discovery " + string;
\t\t}
\t}
}

// nacos-spring-cloud-consumer-example

/* Refer to document: https://github.com/nacos-group/nacos-examples/tree/master/nacos-spring-cloud-example/nacos-spring-cloud-discovery-example/nacos-spring-cloud-consumer-example/src/main/resources
* application.properties
spring.application.name=service-consumer
spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848
*/    

// Refer to document: https://github.com/nacos-group/nacos-examples/tree/master/nacos-spring-cloud-example/nacos-spring-cloud-discovery-example/nacos-spring-cloud-consumer-example/src/main/java/com/alibaba/nacos/example/spring/cloud
package com.alibaba.nacos.example.spring.cloud;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * @author xiaojing
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NacosConsumerApplication {

    @LoadBalanced
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static void main(String[] args) {
        SpringApplication.run(NacosConsumerApplication.class, args);
    }

    @RestController
    public class TestController {

        private final RestTemplate restTemplate;

        @Autowired
        public TestController(RestTemplate restTemplate) {this.restTemplate = restTemplate;}

        @RequestMapping(value = "/echo/{str}", method = RequestMethod.GET)
        public String echo(@PathVariable String str) {
            return restTemplate.getForObject("http://service-provider/echo/" + str, String.class);
        }
    }
}`;
  }

  componentDidMount() {}

  openLoading() {
    this.setState({
      loading: true,
    });
  }

  closeLoading() {
    this.setState({
      loading: false,
    });
  }

  getData() {
    const namespace = getParams('namespace'); // 获取ak,sk
    const obj = {
      group: this.record.group || '',
      dataId: this.record.dataId || '',
      namespace,
      inEdas: window.globalConfig.isParentEdas(),
    };
    this.defaultCode = this.getJavaCode(obj);
    this.createCodeMirror('text/x-java', this.defaultCode);
    this.nodejsCode = this.getNodejsCode(obj);
    this.cppCode = this.getCppCode(obj);
    this.shellCode = this.getShellCode(obj);
    this.pythonCode = this.getPythonCode(obj);
    this.forceUpdate();
  }

  getJavaCode(data) {
    return `/* Refer to document: https://github.com/nacos-group/nacos-examples/tree/master/nacos-spring-example/nacos-spring-discovery-example
*  pom.xml
\t<dependency>
                <groupId>com.alibaba.nacos</groupId>
                <artifactId>nacos-spring-context</artifactId>
                <version>0.2.2-RC1</version>
            </dependency>
*/

/* Refer to document:  https://github.com/nacos-group/nacos-examples/blob/master/nacos-spring-example/nacos-spring-discovery-example/src/main/java/com/alibaba/nacos/example/spring
package com.alibaba.nacos.example.spring;

import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.spring.context.annotation.discovery.EnableNacosDiscovery;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableNacosDiscovery(globalProperties = @NacosProperties(serverAddr = "127.0.0.1:8848"))
public class NacosConfiguration {

}

// Refer to document: https://github.com/nacos-group/nacos-examples/tree/master/nacos-spring-example/nacos-spring-discovery-example/src/main/java/com/alibaba/nacos/example/spring/controller
package com.alibaba.nacos.example.spring.controller;

import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@Controller
@RequestMapping("discovery")
public class DiscoveryController {

    @NacosInjected
    private NamingService namingService;

    @RequestMapping(value = "/get", method = GET)
    @ResponseBody
    public List<Instance> get(@RequestParam String serviceName) throws NacosException {
        return namingService.getAllInstances(serviceName);
    }
}`;
  }

  getNodejsCode(data) {
    return 'TODO';
  }

  getCppCode(data) {
    return 'TODO';
  }

  getShellCode(data) {
    return 'TODO';
  }

  getPythonCode(data) {
    return 'TODO';
  }

  openDialog(record) {
    this.setState({
      dialogvisible: true,
    });
    this.record = record;
    setTimeout(() => {
      this.getData(); // 获取数据
    });
  }

  closeDialog() {
    this.setState({
      dialogvisible: false,
    });
  }

  createCodeMirror(mode, value) {
    const commontarget = this.refs.codepreview;
    if (commontarget) {
      commontarget.innerHTML = '';
      this.cm = window.CodeMirror(commontarget, {
        value,
        mode,
        height: 400,
        width: 500,
        lineNumbers: true,
        theme: 'xq-light',
        lint: true,
        tabMode: 'indent',
        autoMatchParens: true,
        textWrapping: true,
        gutters: ['CodeMirror-lint-markers'],
        extraKeys: {
          F1(cm) {
            cm.setOption('fullScreen', !cm.getOption('fullScreen'));
          },
          Esc(cm) {
            if (cm.getOption('fullScreen')) cm.setOption('fullScreen', false);
          },
        },
      });
      // this.cm.setSize('300px',height:'222px');
      this.cm.setSize('500px', '490px');
    }

    // this.cm.setSize(window.innerWidth*0.8-10,400);//设置宽高
  }

  changeTab(key, code) {
    setTimeout(() => {
      this[key] = true;

      this.createCodeMirror('text/javascript', code);
    });
  }

  render() {
    const { locale = {} } = this.props;
    const footer = <div />;
    return (
      <div>
        <Dialog
          title={locale.sampleCode}
          style={{ width: '80%' }}
          visible={this.state.dialogvisible}
          footer={footer}
          onClose={this.closeDialog.bind(this)}
        >
          <div style={{ height: 500 }}>
            <Loading tip={locale.loading} style={{ width: '100%' }} visible={this.state.loading}>
              <Tab shape={'text'} style={{ height: 40, paddingBottom: 10 }}>
                <TabPane
                  title={'Java'}
                  key={1}
                  onClick={this.changeTab.bind(this, 'commoneditor1', this.defaultCode)}
                />
                <TabPane
                  title={'Spring Boot'}
                  key={2}
                  onClick={this.changeTab.bind(this, 'commoneditor2', this.sprigboot_code)}
                />

                <TabPane
                  title={'Spring Cloud'}
                  key={21}
                  onClick={this.changeTab.bind(this, 'commoneditor21', this.sprigcloud_code)}
                />

                <TabPane
                  title={'Node.js'}
                  key={3}
                  onClick={this.changeTab.bind(this, 'commoneditor3', this.nodejsCode)}
                />

                <TabPane
                  title={'C++'}
                  key={4}
                  onClick={this.changeTab.bind(this, 'commoneditor4', this.cppCode)}
                />

                <TabPane
                  title={'Shell'}
                  key={5}
                  onClick={this.changeTab.bind(this, 'commoneditor5', this.shellCode)}
                />

                <TabPane
                  title={'Python'}
                  key={6}
                  onClick={this.changeTab.bind(this, 'commoneditor6', this.pythonCode)}
                />
                {}
              </Tab>
              <div ref={'codepreview'} />
            </Loading>
          </div>
        </Dialog>
      </div>
    );
  }
}

export default ShowServiceCodeing;
