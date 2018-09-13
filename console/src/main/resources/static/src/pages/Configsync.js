import React from 'react';
import { Button, Checkbox, Dialog, Field, Form, Input, Loading } from '@alifd/next';
import SuccessDialog from '../components/SuccessDialog';

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class Configsync extends React.Component {
    constructor(props) {
        super(props);
        this.field = new Field(this);
        this.dataId = window.getParams('dataId') || 'yanlin';
        this.group = window.getParams('group') || '';
        this.serverId = window.getParams('serverId') || '';

        this.state = {
            configType: 0,

            envvalues: [],
            commonvalue: [],
            envComponent: '',
            envGroups: [],
            envlist: [],
            loading: false,
            showmore: false
        };
        this.codeValue = '';
        this.mode = 'text';
        this.ips = '';
    }
    componentDidMount() {

        this.getDataDetail();
        //  this.getDomain();
    }
    toggleMore() {
        this.setState({
            showmore: !this.state.showmore
        });
    }
    getEnvList(value) {
        this.setState({
            envvalues: value
        });
        this.envs = value;
    }
    getDomain() {
        let self = this;
        window.request({
            url: `/diamond-ops/env/domain`,
            success: function (data) {

                if (data.code === 200) {
                    let envGroups = data.data.envGroups;

                    self.setState({
                        envGroups: envGroups
                    });
                }
            }
        });
    }
    getDataDetail() {
        let self = this;
        this.tenant = window.getParams('namespace') || '';
        this.serverId = window.getParams('serverId') || 'center';
        let url = `/diamond-ops/configList/detail/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}/tenant/${this.tenant}?id=`;
        if (this.tenant === 'global' || !this.tenant) {
            url = `/diamond-ops/configList/detail/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}?id=`;
        }
        window.request({
            url: url,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (result) {
                if (result.code === 200) {
                    let data = result.data;

                    self.field.setValue('dataId', data.dataId);
                    //self.field.setValue('content', data.content);
                    self.field.setValue('appName', data.appName);
                    //self.field.setValue('envs', self.serverId);
                    self.field.setValue('group', data.group);
                    //self.field.setValue('md5', data.md5);
                    self.field.setValue('content', data.content || ''
                        //let envlist = [];
                        // let envvalues = [];
                        // for (let i = 0; i < data.envs.length; i++) {
                        //     let obj = data.envs[i]
                        //     envlist.push({
                        //         label: obj.name,
                        //         value: obj.serverId
                        //     })
                        //     envvalues.push(obj.serverId);
                        // }

                    ); let env = data.envs || [];
                    let envvalues = [];
                    let envlist = [];
                    for (let i = 0; i < env.length; i++) {
                        envlist.push({
                            value: env[i].serverId,
                            label: env[i].name
                        });
                        if (env[i].serverId === self.serverId) {
                            envvalues.push(self.serverId);
                        }
                    }
                    self.setState({
                        envlist: envlist,
                        envvalues: envvalues
                        // self.setState({
                        //     envname: env.name,
                        // })
                        //self.serverId = env.serverId;

                    });
                } else {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        title: window.aliwareIntl.get('com.alibaba.nacos.page.configsync.error'),
                        content: result.message
                    });
                }
            },
            complete: function () {
                self.closeLoading();
            }
        });
    }

    goList() {

        //console.log(`/configurationManagement?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}`)
        window.hashHistory.push(`/configurationManagement?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}`);
    }

    sync() {

        let self = this;
        let payload = {
            dataId: this.field.getValue('dataId'),
            appName: this.field.getValue('appName'),
            group: this.field.getValue('group'),
            content: this.field.getValue('content'),
            betaIps: this.ips,
            targetEnvs: this.envs

        };
        window.request({
            type: 'put',
            contentType: 'application/json',
            url: `/diamond-ops/configList/serverId/${this.serverId}/dataId/${payload.dataId}/group/${payload.group}?id=`,
            data: JSON.stringify(payload),
            success: function (res) {
                let _payload = {};
                _payload.maintitle = window.aliwareIntl.get('com.alibaba.nacos.page.configsync.sync_configuration_main');
                _payload.title = window.aliwareIntl.get('com.alibaba.nacos.page.configsync.sync_configuration');
                _payload.content = '';
                _payload.dataId = payload.dataId;
                _payload.group = payload.group;
                if (res.code === 200) {
                    _payload.isok = true;
                } else {
                    _payload.isok = false;
                    _payload.message = res.message;
                }
                self.refs['success'].openDialog(_payload);
            },
            error: function () { }
        });
    }
    syncResult() {
        let dataId = this.field.getValue('dataId');
        let gruop = this.field.getValue('group');
        window.hashHistory.push(`/diamond-ops/static/pages/config-sync/index.html?serverId=center&dataId=${dataId}&group=${gruop}`);
    }
    changeEnv(values) {
        this.targetEnvs = values;
        this.setState({
            envvalues: values
        });
    }
    getIps(value) {
        this.ips = value;
    }
    goResult() {
        window.hashHistory.push(`/consistencyEfficacy?serverId=${this.serverId}&dataId=${this.dataId}&group=${this.group}`);
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
    render() {
        const { init } = this.field;
        const formItemLayout = {
            labelCol: {
                span: 2
            },
            wrapperCol: {
                span: 22
            }
        };

        return (
            <div style={{ padding: 10 }}>
                <Loading shape="flower" style={{ position: 'relative', width: '100%' }} visible={this.state.loading} tip="Loading..." color="#333">
                    <h1>{window.aliwareIntl.get('com.alibaba.nacos.page.configsync.sync_configuration')}</h1>
                    <Form field={this.field}>

                        <Form.Item label="Data ID:" required {...formItemLayout}>
                            <Input htmlType="text" disabled={'disabled'} {...init('dataId')} />
                            <div style={{ marginTop: 10 }}>
                                <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>{this.state.showmore ? window.aliwareIntl.get('com.alibaba.nacos.page.configsync.retracted') : window.aliwareIntl.get('com.alibaba.nacos.page.configsync.for_more_advanced_options')}</a>
                            </div>
                        </Form.Item>
                        <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
                            <Form.Item label="Group ID:" required {...formItemLayout}>
                                <Input htmlType="text" disabled={'disabled'} {...init('group')} />
                            </Form.Item>
                            <Form.Item label={window.aliwareIntl.get('com.alibaba.nacos.page.configsync.home')} required {...formItemLayout}>
                                <Input htmlType="text" disabled={'disabled'} {...init('appName')} />
                            </Form.Item>
                        </div>
                        <Form.Item label={window.aliwareIntl.get('com.alibaba.nacos.page.configsync.belongs_to_the_environment')} required {...formItemLayout}>
                            <Input htmlType="text" disabled={'disabled'} {...init('envs')} />
                        </Form.Item>

                        <Form.Item label={window.aliwareIntl.get('com.alibaba.nacos.page.configsync.configuration')} required {...formItemLayout}>
                            <Input htmlType="text" multiple rows={15} disabled={'disabled'} {...init('content')} />
                        </Form.Item>
                        <Form.Item label={window.aliwareIntl.get('com.alibaba.nacos.page.configsync.target')} required {...formItemLayout}>
                            <div>
                                <Checkbox.Group value={this.state.envvalues} onChange={this.changeEnv.bind(this)} dataSource={this.state.envlist} />
                            </div>
                        </Form.Item>
                        <Form.Item label=" " {...formItemLayout}>

                            <div style={{ textAlign: 'right' }}>
                                <Button type="primary" onClick={this.sync.bind(this)} style={{ marginRight: 10 }}>{window.aliwareIntl.get('com.alibaba.nacos.page.configsync.sync')}</Button>
                                {}
                                <Button type="light" onClick={this.goList.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.page.configsync.return')}</Button>
                            </div>
                        </Form.Item>
                    </Form>
                    <SuccessDialog ref="success" />
                </Loading>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default Configsync;