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
 * Service Registration Sample Code Show Window
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
    this.springCode = 'TODO';
    this.sprigbootCode = 'TODO';
    this.sprigcloudCode = 'TODO';
  }

  componentDidMount() {
  }

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
    this.springCode = this.getSpringCode(obj);
    this.sprigbootCode = this.getSpringBootCode(obj);
    this.sprigcloudCode = this.getSpringCloudCode(obj);
    this.nodejsCode = this.getNodejsCode(obj);
    this.cppCode = this.getCppCode(obj);
    this.shellCode = this.getShellCode(obj);
    this.pythonCode = this.getPythonCode(obj);
    this.forceUpdate();
  }

  getJavaCode(data) {
    return `/* Refer to document: https://github.com/alibaba/nacos/blob/master/example/src/main/java/com/alibaba/nacos/example
*  pom.xml
    <dependency>
        <groupId>com.alibaba.nacos</groupId>
        <artifactId>nacos-client</artifactId>
        <version>$\{latest.version}</version>
    </dependency>
*/
package com.alibaba.nacos.example;

import java.util.Properties;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;

/**
 * @author nkorange
 */
public class NamingExample {

    public static void main(String[] args) throws NacosException {

        Properties properties = new Properties();
        properties.setProperty("serverAddr", System.getProperty("serverAddr"));
        properties.setProperty("namespace", System.getProperty("namespace"));

        NamingService naming = NamingFactory.createNamingService(properties);

        naming.registerInstance("${this.record.name}", "11.11.11.11", 8888, "TEST1");

        naming.registerInstance("${this.record.name}", "2.2.2.2", 9999, "DEFAULT");

        System.out.println(naming.getAllInstances("${this.record.name}"));

        naming.deregisterInstance("${this.record.name}", "2.2.2.2", 9999, "DEFAULT");

        System.out.println(naming.getAllInstances("${this.record.name}"));

        naming.subscribe("${this.record.name}", new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent)event).getServiceName());
                System.out.println(((NamingEvent)event).getInstances());
            }
        });
    }
}`;
  }

  getSpringCode(data) {
    return `/* Refer to document: https://github.com/nacos-group/nacos-examples/tree/master/nacos-spring-example/nacos-spring-discovery-example
*  pom.xml
    <dependency>
        <groupId>com.alibaba.nacos</groupId>
        <artifactId>nacos-spring-context</artifactId>
        <version>\${latest.version}</version>
    </dependency>
*/

// Refer to document:  https://github.com/nacos-group/nacos-examples/blob/master/nacos-spring-example/nacos-spring-discovery-example/src/main/java/com/alibaba/nacos/example/spring
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

  getSpringBootCode(data) {
    return `/* Refer to document: https://github.com/nacos-group/nacos-examples/blob/master/nacos-spring-boot-example/nacos-spring-boot-discovery-example
*  pom.xml
    <dependency>
       <groupId>com.alibaba.boot</groupId>
       <artifactId>nacos-discovery-spring-boot-starter</artifactId>
       <version>\${latest.version}</version>
    </dependency>
*/
/* Refer to document:  https://github.com/nacos-group/nacos-examples/blob/master/nacos-spring-boot-example/nacos-spring-boot-discovery-example/src/main/resources
* application.properties
   nacos.discovery.server-addr=127.0.0.1:8848
*/    
// Refer to document: https://github.com/nacos-group/nacos-examples/blob/master/nacos-spring-boot-example/nacos-spring-boot-discovery-example/src/main/java/com/alibaba/nacos/example/spring/boot/controller

package com.alibaba.nacos.example.spring.boot.controller;

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

  getSpringCloudCode(data) {
    return `/* Refer to document: https://github.com/nacos-group/nacos-examples/blob/master/nacos-spring-cloud-example/nacos-spring-cloud-discovery-example/
*  pom.xml 
    <dependency>
       <groupId>org.springframework.cloud</groupId>
       <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
       <version>\${latest.version}</version>
    </dependency>
*/

// nacos-spring-cloud-provider-example

/* Refer to document:  https://github.com/nacos-group/nacos-examples/tree/master/nacos-spring-cloud-example/nacos-spring-cloud-discovery-example/nacos-spring-cloud-provider-example/src/main/resources
* application.properties
server.port=18080
spring.application.name=${this.record.name}
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

  public static void main(String[] args) {
    SpringApplication.run(NacosProviderApplication.class, args);
}

  @RestController
  class EchoController {
    @RequestMapping(value = "/echo/{string}", method = RequestMethod.GET)
    public String echo(@PathVariable String string) {
      return "Hello Nacos Discovery " + string;
    }
  }
}

// nacos-spring-cloud-consumer-example

/* Refer to document: https://github.com/nacos-group/nacos-examples/tree/master/nacos-spring-cloud-example/nacos-spring-cloud-discovery-example/nacos-spring-cloud-consumer-example/src/main/resources
* application.properties
spring.application.name=micro-service-oauth2
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
      this.cm.setSize('auto', '490px');
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
                  key={0}
                  onClick={this.changeTab.bind(this, 'commoneditor1', this.defaultCode)}
                />
                <TabPane
                  title={'Spring'}
                  key={1}
                  onClick={this.changeTab.bind(this, 'commoneditor1', this.springCode)}
                />
                <TabPane
                  title={'Spring Boot'}
                  key={2}
                  onClick={this.changeTab.bind(this, 'commoneditor2', this.sprigbootCode)}
                />

                <TabPane
                  title={'Spring Cloud'}
                  key={21}
                  onClick={this.changeTab.bind(this, 'commoneditor21', this.sprigcloudCode)}
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
