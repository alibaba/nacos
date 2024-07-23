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
    this.csharpCode = 'TODO';
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
    this.csharpCode = this.getCSharpCode(obj);
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
				System.out.println("receive:" + configInfo);
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
    return `/*
* Demo for Nacos
*/
import json
import socket

import nacos


def get_host_ip():
    res = socket.gethostbyname(socket.gethostname())
    return res


def load_config(content):
    _config = json.loads(content)
    return _config


def nacos_config_callback(args):
    content = args['raw_content']
    load_config(content)


class NacosClient:
    service_name = None
    service_port = None
    service_group = None

    def __init__(self, server_endpoint, namespace_id, username=None, password=None):
        self.client = nacos.NacosClient(server_endpoint,
                                        namespace=namespace_id,
                                        username=username,
                                        password=password)
        self.endpoint = server_endpoint
        self.service_ip = get_host_ip()

    def register(self):
        self.client.add_naming_instance(self.service_name,
                                        self.service_ip,
                                        self.service_port,
                                        group_name=self.service_group)

    def modify(self, service_name, service_ip=None, service_port=None):
        self.client.modify_naming_instance(service_name,
                                           service_ip if service_ip else self.service_ip,
                                           service_port if service_port else self.service_port)

    def unregister(self):
        self.client.remove_naming_instance(self.service_name,
                                           self.service_ip,
                                           self.service_port)

    def set_service(self, service_name, service_ip, service_port, service_group):
        self.service_name = service_name
        self.service_ip = service_ip
        self.service_port = service_port
        self.service_group = service_group

    async def beat_callback(self):
        self.client.send_heartbeat(self.service_name,
                                   self.service_ip,
                                   self.service_port)

    def load_conf(self, data_id, group):
        return self.client.get_config(data_id=data_id, group=group, no_snapshot=True)

    def add_conf_watcher(self, data_id, group, callback):
        self.client.add_config_watcher(data_id=data_id, group=group, cb=callback)


if __name__ == '__main__':
    nacos_config = {
        "nacos_data_id":"test",
        "nacos_server_ip":"127.0.0.1",
        "nacos_namespace":"public",
        "nacos_groupName":"DEFAULT_GROUP",
        "nacos_user":"nacos",
        "nacos_password":"1234567"
    }
    nacos_data_id = nacos_config["nacos_data_id"]
    SERVER_ADDRESSES = nacos_config["nacos_server_ip"]
    NAMESPACE = nacos_config["nacos_namespace"]
    groupName = nacos_config["nacos_groupName"]
    user = nacos_config["nacos_user"]
    password = nacos_config["nacos_password"]
    # todo 将另一个路由对象（通常定义在其他模块或文件中）合并到主应用（app）中。
    # app.include_router(custom_api.router, tags=['test'])
    service_ip = get_host_ip()
    client = NacosClient(SERVER_ADDRESSES, NAMESPACE, user, password)
    client.add_conf_watcher(nacos_data_id, groupName, nacos_config_callback)

    # 启动时，强制同步一次配置
    data_stream = client.load_conf(nacos_data_id, groupName)
    json_config = load_config(data_stream)
`;
  }

  getCSharpCode(data) {
    return `/*
Demo for Basic Nacos Opreation
App.csproj

<ItemGroup>
  <PackageReference Include="nacos-sdk-csharp" Version="\${latest.version}" />
</ItemGroup>
*/

using Microsoft.Extensions.DependencyInjection;
using Nacos.V2;
using Nacos.V2.DependencyInjection;
using System;
using System.Collections.Generic;
using System.Threading.Tasks;

class Program
{
    static async Task Main(string[] args)
    {
        string serverAddr = "http://localhost:8848";
        string dataId = "${data.dataId}";
        string group = "${data.group}";

        IServiceCollection services = new ServiceCollection();

        services.AddNacosV2Config(x =>
        {
            x.ServerAddresses = new List<string> { serverAddr };
            x.Namespace = "cs-test";

            // swich to use http or rpc
            x.ConfigUseRpc = true;
        });

        IServiceProvider serviceProvider = services.BuildServiceProvider();
        var configSvc = serviceProvider.GetService<INacosConfigService>();

        var content = await configSvc.GetConfig(dataId, group, 3000);
        Console.WriteLine(content);

        var listener = new ConfigListener();

        await configSvc.AddListener(dataId, group, listener);

        var isPublishOk = await configSvc.PublishConfig(dataId, group, "content");
        Console.WriteLine(isPublishOk);

        await Task.Delay(3000);
        content = await configSvc.GetConfig(dataId, group, 5000);
        Console.WriteLine(content);

        var isRemoveOk = await configSvc.RemoveConfig(dataId, group);
        Console.WriteLine(isRemoveOk);
        await Task.Delay(3000);

        content = await configSvc.GetConfig(dataId, group, 5000);
        Console.WriteLine(content);
        await Task.Delay(300000);
    }

    internal class ConfigListener : IListener
    {
        public void ReceiveConfigInfo(string configInfo)
        {
            Console.WriteLine("receive:" + configInfo);
        }
    }
}

/*
Refer to document:  https://github.com/nacos-group/nacos-sdk-csharp/tree/dev/samples/MsConfigApp
Demo for ASP.NET Core Integration
MsConfigApp.csproj

<ItemGroup>
  <PackageReference Include="nacos-sdk-csharp.Extensions.Configuration" Version="\${latest.version}" />
</ItemGroup>
*/

using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Hosting;
using Serilog;
using Serilog.Events;

public class Program
{
    public static void Main(string[] args)
    {
        Log.Logger = new LoggerConfiguration()
            .Enrich.FromLogContext()
            .MinimumLevel.Override("Microsoft", LogEventLevel.Warning)
            .MinimumLevel.Override("System", LogEventLevel.Warning)
            .MinimumLevel.Debug()
            .WriteTo.Console()
            .CreateLogger();

        try
        {
            Log.ForContext<Program>().Information("Application starting...");
            CreateHostBuilder(args, Log.Logger).Build().Run();
        }
        catch (System.Exception ex)
        {
            Log.ForContext<Program>().Fatal(ex, "Application start-up failed!!");
        }
        finally
        {
            Log.CloseAndFlush();
        }
    }

    public static IHostBuilder CreateHostBuilder(string[] args, Serilog.ILogger logger) =>
        Host.CreateDefaultBuilder(args)
            .ConfigureAppConfiguration((context, builder) =>
            {
                var c = builder.Build();
                builder.AddNacosV2Configuration(c.GetSection("NacosConfig"), logAction: x => x.AddSerilog(logger));
            })
            .ConfigureWebHostDefaults(webBuilder =>
            {
                webBuilder.UseStartup<Startup>().UseUrls("http://*:8787");
            })
            .UseSerilog();
}
  `;
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

                <TabPane
                  title={'C#'}
                  key={7}
                  onClick={this.changeTab.bind(this, 'commoneditor7', this.csharpCode)}
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
