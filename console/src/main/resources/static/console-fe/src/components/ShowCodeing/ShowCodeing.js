/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

import React from 'react';
import PropTypes from 'prop-types';
import { getParams } from '../../globalLib';
import { ConfigProvider, Dialog, Loading, Tab } from '@alifd/next';

import './index.scss';

const TabPane = Tab.Item;

@ConfigProvider.config
class ShowCodeing extends React.Component {
  static displayName = 'ShowCodeing';

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
    this.sprigboot_code = `// Refer to document: https://github.com/nacos-group/nacos-examples/tree/master/nacos-spring-boot-example/nacos-spring-boot-config-example
package com.alibaba.nacos.example.spring.boot.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@Controller
@RequestMapping("config")
public class ConfigController {

    @Value("\${useLocalCache:false}")
    private boolean useLocalCache;

    public void setUseLocalCache(boolean useLocalCache) {
        this.useLocalCache = useLocalCache;
    }

    @RequestMapping(value = "/get", method = GET)
    @ResponseBody
    public boolean get() {
        return useLocalCache;
    }
}`;
    this.sprigcloud_code = `// Refer to document:  https://github.com/nacos-group/nacos-examples/tree/master/nacos-spring-cloud-example/nacos-spring-cloud-config-example
package com.alibaba.nacos.example.spring.cloud.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/config")
@RefreshScope
public class ConfigController {

    @Value("\${useLocalCache:false}")
    private boolean useLocalCache;

    @RequestMapping("/get")
    public boolean get() {
        return useLocalCache;
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
    return `/*
* Demo for Nacos
* pom.xml
    <dependency>
        <groupId>com.alibaba.nacos</groupId>
        <artifactId>nacos-client</artifactId>
        <version>\${version}</version>
    </dependency>
*/
package com.alibaba.nacos.example;

import java.util.Properties;
import java.util.concurrent.Executor;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * Config service example
 *
 * @author Nacos
 *
 */
public class ConfigExample {

	public static void main(String[] args) throws NacosException, InterruptedException {
		String serverAddr = "localhost";
		String dataId = "${data.dataId}";
		String group = "${data.group}";
		Properties properties = new Properties();
		properties.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
		ConfigService configService = NacosFactory.createConfigService(properties);
		String content = configService.getConfig(dataId, group, 5000);
		System.out.println(content);
		configService.addListener(dataId, group, new Listener() {
			@Override
			public void receiveConfigInfo(String configInfo) {
				System.out.println("recieve:" + configInfo);
			}

			@Override
			public Executor getExecutor() {
				return null;
			}
		});

		boolean isPublishOk = configService.publishConfig(dataId, group, "content");
		System.out.println(isPublishOk);

		Thread.sleep(3000);
		content = configService.getConfig(dataId, group, 5000);
		System.out.println(content);

		boolean isRemoveOk = configService.removeConfig(dataId, group);
		System.out.println(isRemoveOk);
		Thread.sleep(3000);

		content = configService.getConfig(dataId, group, 5000);
		System.out.println(content);
		Thread.sleep(300000);

	}
}
`;
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

export default ShowCodeing;
