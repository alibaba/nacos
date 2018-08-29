import React from 'react'; 
import { Dialog, Loading, Tab } from '@alifd/next';
const TabPane = Tab.Item; 

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ShowCodeing extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            dialogvisible: false,
            loading: false
        };
        this.defaultCode = ``;
        this.nodejsCode = ``;
        this.cppCode = ``;
        this.shellCode = ``;
        this.pythonCode = ``;
        this.record = {};
        this.sprigboot_code = `// Refer to document:  https://help.aliyun.com/document_detail/60369.html
package com.alibaba.cloud.acm.sample;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created on 01/10/2017.
 * Assume the configuration content is as follows
 * user.id = 0
 * user.name = juven.xxxb
 * user.age = 18
 * @author juven.xuxb
 */
@SpringBootApplication
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}

@Component
class SampleRunner implements ApplicationRunner {

    @Value("\${user.id}")
    String userId;

    @Value("\${user.name}")
    String userName;

    @Value("\${user.age}")
    int userAge;

    public void run(ApplicationArguments args) throws Exception {
        System.out.println(userId);
        System.out.println(userName);
        System.out.println(userAge);
    }
}

@RestController
@RequestMapping("/sample")
@RefreshScope
class SampleController {

    @Value("\${user.name}")
    String userName;

    @RequestMapping("/acm")
    public String simple() {
        return "Hello Spring Cloud ACM!" + "Hello " + userName + "!";
    }
}`;
    }

    componentDidMount() {}
    openLoading() {
        this.setState({
            loading: true
        });
    }
    closeLoading() {
        this.setState({
            loading: false
        });
    }

    getData() {
        let namespace = getParams('namespace'); //获取ak,sk
        request({
            url: `/diamond-ops/service/namespaceOwnerInfo/${namespace}`,
            beforeSend: () => {
                this.openLoading();
            },
            success: res => {
                if (res.code === 200) {
                    let obj = {
                        regionId: res.data.regionId,
                        accessKey: res.data.accessKey,
                        secretKey: res.data.secretKey,
                        group: this.record.group || '',
                        dataId: this.record.dataId || '',
                        namespace: namespace,
                        endpoint: res.data.endpoint,
                        inEdas: window.globalConfig.isParentEdas()
                    };

                    this.defaultCode = this.getJavaCode(obj);
                    this.createCodeMirror('text/x-java', this.defaultCode);
                    this.nodejsCode = this.getNodejsCode(obj);
                    this.cppCode = this.getCppCode(obj);
                    this.shellCode = this.getShellCode(obj);
                    this.pythonCode = this.getPythonCode(obj);
                }
            },
            complete: () => {
                this.closeLoading();
            }
        });
    }

    getJavaCode(data) {
        let initCode = "";
        let initKmsCode = "";
        if (data.dataId.startsWith("cipher-")) {
            initKmsCode = `properties.put("openKMSFilter", true);
			properties.put("regionId", "${data.regionId}");`;
        }
        if (data.inEdas) {
            initCode = `/**
                *  In production environment, parameters are passed through JVM so that you can use multiple environment， -Daddress.server.domain=${data.endpoint}  -Dtenant.id=${data.namespace} -Dproject.name=acmtest -Dspas.identity=\${home}\\.spas_key\\acmtest
                *  Input accessKey/secretKey in your local acmtest file in the following format.
                *  accessKey=$accessKey
                *  secretKey=$secretKey
                */
                Properties properties = new Properties();
                ${initKmsCode}
                ConfigService.init(properties);`;
        } else {
            initCode = `// Initialize configuration service and the console will retrieve the following parameters through the sample code. Input parameters include endpoint, namespace, accessKey, and secretKey(The secrectKey of ACM. Do not use the secrectKey of your Alibaba acount.).
			Properties properties = new Properties();
			properties.put("endpoint", "${data.endpoint}");
			properties.put("namespace", "${data.namespace}");
            // Access ACM with instance RAM role: https://help.aliyun.com/document_detail/72013.html
            // properties.put("ramRoleName", "$ramRoleName");
			properties.put("accessKey", "$accessKey");
			properties.put("secretKey", "$secretKey");
			${initKmsCode}
			ConfigService.init(properties);`;
        }

        return `// Refer to document:  https://help.aliyun.com/document_detail/60138.html
package com.alibaba.middleware.acm;

import java.util.Properties;
import com.alibaba.edas.acm.ConfigService;
import com.alibaba.edas.acm.exception.ConfigException;
import com.alibaba.edas.acm.listener.ConfigChangeListener;
// Sample code, for illustration only
public class ACMTest {

    // Properties/Switch
    private static String config = "DefaultValue";
	
    private static Properties acmProperties = new Properties();
    
	public static void main(String[] args) {
		try {
			
			${initCode}
			// Get configuration proactively
			String content = ConfigService.getConfig("${data.dataId}", "${data.group}", 6000);
			System.out.println(content);
			// Add listener for the configuration during initialization, so that configuration changes will trigger callback notifications.
			ConfigService.addListener("${data.dataId}", "${data.group}", new ConfigChangeListener() {
				public void receiveConfigInfo(String configInfo) {
					// When the configuration is updated, the callback function will send the new value to the user.
					// Note that you should not perform any block operations in the callback function. Otherwise the thread will be blocked.
					config = configInfo;
					System.out.println(configInfo);
				}
			});
			
			/**
			 * If the configuration value is in the format of properties (key=value), you can use the following listener to configure multiple configuration items in one configuration.
			 */
			
			/**
			ConfigService.addListener("${data.dataId}", "${data.group}", new PropertiesListener() {
				
				@Override
				public void innerReceive(Properties properties) {
					// TODO Auto-generated method stub
					acmProperties = properties;
					System.out.println(properties);
				}
			});
			
			**/
			
		} catch (ConfigException e) {
			e.printStackTrace();
		}

		// In this sample, the main thread does not exit, because the configuration subscription is the daemon thread, and it will exit if the main threads exits. The following code is not needed in real scenarios. 
		while (true) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
    // Expose the configuration value with the GET interface
    public static String getConfig() {
        return config;
    }
    
	// Expose the configuration value with the GET interface
	public static Object getPorpertiesValue(String key) {
		if (acmProperties != null) {
			return acmProperties.get(key);
		}
		return null;
	}

}`;
    }

    getNodejsCode(data) {
        return `// Refer to document:  https://help.aliyun.com/document_detail/62670.html
        
const ACMClient = require('acm-client');
const co = require('co');
const acm = new ACMClient({
  endpoint: '${data.endpoint}', // Available in the ACM console
  namespace: '${data.namespace}', // Available in the ACM console
  accessKey: '$accessKey', // Available in the ACM console
  secretKey: '$secretKey', // Available in the ACM console
  requestTimeout: 6000, // Request timeout, 6s by default
});
// get config
co(function*() {
  const content= yield acm.getConfig('${data.dataId}', '${data.group}');
  console.log('getConfig = ',content);
});
// Update listening data
acm.subscribe({
  dataId: 'test',
  group: 'DEFAULT_GROUP',
}, content => {
  console.log(content);
});`;
    }

    getCppCode(data) {
        return `// Refer to document: https://help.aliyun.com/document_detail/63523.html
#include "ACM.h"

using namespace std;
using namespace acm;
// Define listener
class MyListener : public ManagerListener
{
public:
    MyListener(const std::string& data_id, const std::string& group):data_id_(data_id),group_(group){}
    virtual ~MyListener()
    {}
    virtual void getExecutor()
    {
        printf("data_id:%s group:%s getExecutor\\n", \
        data_id_.c_str(), group_.c_str());
    }
    // Callback function
    virtual void receiveConfigInfo( std::string &configInfo)
    {
        printf("data_id:%s group:%s configInfo:\\n%s\\n", \
        data_id_.c_str(), group_.c_str(), configInfo.c_str());
        config_ = configInfo;
    }

private:
    std::string data_id_;
    std::string group_;
    std::string config_;
};


int main() {
    // Initialize configuration service and the console will retrieve the following parameters through the sample code.
    ACM::init("${data.endpoint}", "${data.namespace}", "$accessKey", "$secretKey");

    // fill in dataId and group
    std::string dataId = "${data.dataId}";
    std::string group = "${data.group}";
    std::string content;
    // get config
    ACM::getConfig(dataId, group, 5000, content);
    printf("get ok config %s\\n", content.c_str());
    // Listen for configuration changes
    MyListener* listener = new MyListener(dataId, group);
    ACM::addListener(dataId, group, listener);
    printf("add listener ok %s %s\\n", dataId.c_str(), group.c_str());
    do {
        printf("input q to quit\\n");
    } while (getchar() != 'q');

    return 0;
}
`;
    }

    getShellCode(data) {
        return `#!/bin/bash

## config param
endpoint=${data.endpoint}
namespace=${data.namespace}
accessKey=$accessKey
secretKey=$secretKey
dataId=${data.dataId}
group=${data.group}
## config param end

## get serverIp from address server
serverIp=\`curl $endpoint:8080/diamond-server/diamond -s | awk '{a[NR]=$0}END{srand();i=int(rand()*NR+1);print a[i]}'\`

## config sign
timestamp=\`echo $[$(date +%s%N)/1000000]\`
signStr=$namespace+$group+$timestamp
signContent=\`echo -n $signStr | openssl dgst -hmac $secretKey -sha1 -binary | base64\`

## request
curl -H "Spas-AccessKey:"$accessKey -H "timeStamp:"$timestamp -H "Spas-Signature:"$signContent "http://"$serverIp":8080/diamond-server/config.co?dataId="$dataId"&group="$group"&tenant="$namespace -v`;
    }

    getPythonCode(data) {
        let initKmsCode = "";
        if (data.dataId.startsWith("cipher-")) {
            initKmsCode = `c.set_options(kms_enabled=True, region_id="${data.regionId}")`;
        }
        return `# https://help.aliyun.com/document_detail/66727.html
import acm

ENDPOINT = "${data.endpoint}"
NAMESPACE = "${data.namespace}"
AK = "$accessKey"
SK = "$secretKey"
DATA_ID= "${data.dataId}"
GROUP= "${data.group}"

# Initialize ACM client.
c = acm.ACMClient(ENDPOINT, NAMESPACE, AK, SK)
${initKmsCode}

# Get plain content from ACM.
print(c.get(DATA_ID, GROUP))`;
    }

    openDialog(record) {
        this.setState({
            dialogvisible: true
        });
        this.record = record;
        setTimeout(() => {

            this.getData(); //获取数据
        });
    }

    closeDialog() {
        this.setState({
            dialogvisible: false
        });
    }

    createCodeMirror(mode, value) {
        let commontarget = this.refs['codepreview'];
        if (commontarget) {
            commontarget.innerHTML = '';
            this.cm = CodeMirror(commontarget, {
                value: value,
                mode: mode,
                height: 400,
                width: 500,
                lineNumbers: true,
                theme: 'xq-light',
                lint: true,
                tabMode: "indent",
                autoMatchParens: true,
                textWrapping: true,
                gutters: ["CodeMirror-lint-markers"],
                extraKeys: {
                    "F1": function (cm) {
                        cm.setOption("fullScreen", !cm.getOption("fullScreen"));
                    },
                    "Esc": function (cm) {
                        if (cm.getOption("fullScreen")) cm.setOption("fullScreen", false);
                    }
                }
            });
        }

        //this.cm.setSize(window.innerWidth*0.8-10,400);//设置宽高
    }
    changeTab(key, code) {
        setTimeout(() => {
            this[key] = true;

            this.createCodeMirror('text/javascript', code);
        });
    }
    render() {
        const footer = <div></div>;
        return (
            <div>
                <Dialog title={aliwareIntl.get('com.alibaba.newDiamond.component.ShowCodeing.Sample_code')} style={{ width: '80%' }} visible={this.state.dialogvisible} footer={footer} onClose={this.closeDialog.bind(this)} language={aliwareIntl.currentLanguageCode}>
                    <div style={{ height: 500 }}>
                        <Loading tip={aliwareIntl.get('com.alibaba.newDiamond.component.ShowCodeing.loading')} style={{ width: '100%' }} visible={this.state.loading}>
                            <Tab shape={'text'} style={{ height: 40, paddingBottom: 10 }}>
                                <TabPane title={'Java'} key={1} onClick={this.changeTab.bind(this, 'commoneditor1', this.defaultCode)}>

                                </TabPane>
                                <TabPane title={'Spring Boot'} key={2} onClick={this.changeTab.bind(this, 'commoneditor2', this.sprigboot_code)}>

                                </TabPane>
                                <TabPane title={'Node.js'} key={3} onClick={this.changeTab.bind(this, 'commoneditor3', this.nodejsCode)}>

                                </TabPane>
                                <TabPane title={'C++'} key={4} onClick={this.changeTab.bind(this, 'commoneditor4', this.cppCode)}>

                                </TabPane>

                                <TabPane title={'Shell'} key={5} onClick={this.changeTab.bind(this, 'commoneditor5', this.shellCode)}>

                                </TabPane>
                                <TabPane title={'Python'} key={6} onClick={this.changeTab.bind(this, 'commoneditor6', this.pythonCode)}>

                                </TabPane>
                                {}
                            </Tab>
                            <div ref={'codepreview'}></div>
                        </Loading>
                    </div>
                </Dialog>

            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ShowCodeing;