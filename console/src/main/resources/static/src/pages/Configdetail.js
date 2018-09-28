import React from 'react';
import { Button, Dialog, Field, Form, Input, Loading, Tab } from '@alifd/next';
const TabPane = Tab.Item;
const FormItem = Form.Item;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class Configdetail extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            showmore: false,
            activeKey: 'normal',
            hasbeta: false,
            ips: '',
            checkedBeta: false,
            switchEncrypt: false,
            tag: [{ title: window.aliwareIntl.get('com.alibaba.nacos.page.configdetail.official'), key: 'normal' }]
        };
        this.field = new Field(this);
        this.dataId = window.getParams('dataId') || 'yanlin';
        this.group = window.getParams('group') || 'DEFAULT_GROUP';
        this.ips = '';
        this.valueMap = {}; //存储不同版本的数据
        this.tenant = window.getParams('namespace') || '';
        this.searchDataId = window.getParams('searchDataId') || '';
        this.searchGroup = window.getParams('searchGroup') || '';
        //this.params = window.location.hash.split('?')[1]||'';	
    }

    componentDidMount() {
        if (this.dataId.startsWith("cipher-")) {
            this.setState({
                switchEncrypt: true
            });
        }
        this.getDataDetail();
        this.getTags();
    }
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
    getTags() {
        let self = this;
        this.tenant = window.getParams('namespace') || '';
        this.serverId = window.getParams('serverId') || 'center';
        let url = `/diamond-ops/configList/configTags/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}/tenant/${this.tenant}?id=`;
        if (this.tenant === 'global' || !this.tenant) {
            url = `/diamond-ops/configList/configTags/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}?id=`;
        }
        window.request({
            url: url,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (result) {

                if (result.code === 200) {

                    if (result.data.length > 0) {
                        //如果存在beta
                        let tag = [{ title: window.aliwareIntl.get('com.alibaba.nacos.page.configdetail.official'), key: 'normal' }, { title: 'BETA', key: 'beta' }];
                        self.setState({
                            tag: tag,
                            hasbeta: true
                        });
                        self.getBeta();
                    }
                } else { }
            },
            complete: function () {
                self.closeLoading();
            }
        });
    }
    getBeta() {

        let self = this;
        this.tenant = window.getParams('namespace') || '';
        this.serverId = window.getParams('serverId') || 'center';
        let url = `/diamond-ops/configList/edit/beta/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}/tenant/${this.tenant}?id=`;
        if (this.tenant === 'global' || !this.tenant) {
            url = `/diamond-ops/configList/edit/beta/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}?id=`;
        }
        window.request({
            url: url,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (result) {

                if (result.code === 200) {
                    self.valueMap['beta'] = result.data;
                } else { }
            },
            complete: function () {
                self.closeLoading();
            }
        });
    }
    changeTab(value) {

        let self = this;
        let key = value.split('-')[0];
        let data = this.valueMap[key];
        console.log(data);
        this.setState({
            activeKey: value
        });

        self.field.setValue('content', data.content);

        if (data.betaIps) {
            self.setState({
                ips: data.betaIps
            });
        }
    }
    toggleMore() {
        this.setState({
            showmore: !this.state.showmore
        });
    }

    getDataDetail() {
        let self = this;
        this.serverId = window.getParams('serverId') || 'center';
        this.tenant = window.getParams('namespace') || '';
        this.edasAppName = window.getParams('edasAppName') || '';
        this.inApp = this.edasAppName;
        let url = `/nacos/v1/cs/configs?show=all&dataId=${this.dataId}&group=${this.group}`;
        window.request({
            url: url,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (result) {
                if (result != null) {
                    let data = result;
                    self.valueMap['normal'] = data;
                    self.field.setValue('dataId', data.dataId);
                    self.field.setValue('content', data.content);
                    self.field.setValue('appName', self.inApp ? self.edasAppName : data.appName);
                    self.field.setValue('envs', self.serverId);
                    self.field.setValue('group', data.group);
                    self.field.setValue('config_tags', data.configTags);
                    self.field.setValue('desc', data.desc);
                    self.field.setValue('md5', data.md5);
                } else {
                    Dialog.alert({
                        title: window.aliwareIntl.get('com.alibaba.nacos.page.configdetail.error'),
                        content: result.message,
                        language: window.aliwareIntl.currentLanguageCode
                    });
                }
            },
            complete: function () {
                self.closeLoading();
            }
        });
    }
    goList() {
        window.hashHistory.push(`/configurationManagement?serverId=${this.serverId}&group=${this.searchGroup}&dataId=${this.searchDataId}&namespace=${this.tenant}`);
    }
    render() {
        const init = this.field.init;
        const formItemLayout = {
            labelCol: {
                span: 2
            },
            wrapperCol: {
                span: 22
            }
        };
        let activeKey = this.state.activeKey.split('-')[0];
        return (
            <div style={{ padding: 10 }}>
                <Loading shape={"flower"} tip={"Loading..."} style={{ width: '100%', position: 'relative' }} visible={this.state.loading} color={"#333"}>
                    <h1 style={{ position: 'relative', width: '100%' }}>{window.aliwareIntl.get('com.alibaba.nacos.page.configdetail.configuration_details')}</h1>
                    {this.state.hasbeta ? <div style={{ display: 'inline-block', height: 40, width: '80%', overflow: 'hidden' }}>

                        <Tab shape={'wrapped'} onChange={this.changeTab.bind(this)} lazyLoad={false} activeKey={this.state.activeKey}>
                            {this.state.tag.map(tab => <TabPane title={tab.title} key={tab.key}></TabPane>)}
                        </Tab>

                    </div> : ''}
                    <Form direction={"ver"} field={this.field}>

                        <FormItem label={"Data ID:"} required {...formItemLayout}>
                            <Input htmlType={"text"} readOnly={true} {...init('dataId')} />
                            <div style={{ marginTop: 10 }}>
                                <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>{this.state.showmore ? window.aliwareIntl.get('com.alibaba.nacos.page.configdetail.recipient_from') : window.aliwareIntl.get('com.alibaba.nacos.page.configdetail.more_advanced_options')}</a>
                            </div>
                        </FormItem>

                        {this.state.showmore ? <div>
                            <FormItem label={"Group:"} required {...formItemLayout}>
                                <Input htmlType={"text"} readOnly={true} {...init('group')} />
                            </FormItem>
                            <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.configdetail.home')} {...formItemLayout}>
                                <Input htmlType={"text"} readOnly={true} {...init('appName')} />
                            </FormItem>

                            <FormItem label={window.aliwareIntl.get('nacos.page.configdetail.Tags')} {...formItemLayout}>
                                <Input htmlType={"text"} readOnly={true} {...init('config_tags')} />
                            </FormItem>
                        </div> : ''}

                        <FormItem label={window.aliwareIntl.get('nacos.page.configdetail.Description')} {...formItemLayout}>
                            <Input htmlType={"text"} multiple rows={3} readOnly={true} {...init('desc')} />
                        </FormItem>
                        {activeKey === 'normal' ? '' : <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.configdetail.beta_release')} {...formItemLayout}>

                            <div style={{ width: '100%' }} id={'betaips'}>
                                <Input multiple style={{ width: '100%' }} value={this.state.ips} readOnly={true} placeholder={'127.0.0.1,127.0.0.2'} />
                            </div>
                        </FormItem>}
                        <FormItem label={"MD5:"} required {...formItemLayout}>
                            <Input htmlType={"text"} readOnly={true} {...init('md5')} />
                        </FormItem>
                        <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.configdetail.configuration')} required {...formItemLayout}>
                            <Input htmlType={"text"} multiple rows={15} readOnly={true} {...init('content')} />
                        </FormItem>
                        <FormItem label={" "} {...formItemLayout}>

                            <Button type={"primary"} onClick={this.goList.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.page.configdetail.return')}</Button>

                        </FormItem>
                    </Form>
                </Loading>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default Configdetail;