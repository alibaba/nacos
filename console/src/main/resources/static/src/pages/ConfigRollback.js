import React from 'react';
import { Button, Dialog, Field, Form, Input } from '@alifd/next';
const FormItem = Form.Item;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ConfigRollback extends React.Component {

    constructor(props) {
        super(props);
        this.field = new Field(this);
        this.dataId = window.getParams('dataId') || 'yanlin';
        this.group = window.getParams('group') || 'DEFAULT_GROUP';
        this.serverId = window.getParams('serverId') || 'center';
        this.nid = window.getParams('nid') || '';
        this.state = {
            envName: '',
            visible: false,
            showmore: false
        };
        //this.params = window.location.hash.split('?')[1]||'';	
        this.typeMap = { //操作映射提示
            'U': 'publish',
            'I': window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.delete'),
            'D': 'publish'
        };
        this.typeMapName = { //操作映射名
            'U': window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.updated'),
            'I': window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.inserted'),
            'D': window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.delete')
        };
    }

    componentDidMount() {
        this.getDataDetail();
    }
    toggleMore() {
        this.setState({
            showmore: !this.state.showmore
        });
    }
    getDataDetail() {
        let self = this;
        this.tenant = window.getParams('namespace') || '';
        this.serverId = window.getParams('serverId') || 'center';
        let url = `/nacos/v1/cs/history?dataId=${this.dataId}&group=${this.group}&nid=${this.nid}`;
        window.request({
            url: url,
            success: function (result) {
                if (result != null) {
                    let data = result;
                    let envName = self.serverId;
                    self.id = data.id; //详情的id
                    self.field.setValue('dataId', data.dataId);
                    self.field.setValue('content', data.content);
                    self.field.setValue('appName', data.appName);
                    self.field.setValue('opType', self.typeMapName[data.opType.trim()]);
                    self.opType = data.opType; //当前回滚类型I:插入,D:删除,U:'更新'
                    self.field.setValue('group', data.group);
                    self.field.setValue('md5', data.md5);
                    self.field.setValue('envName', envName);
                    self.setState({
                        envName: envName
                    });
                }
            }
        });
    }
    goList() {
        let tenant = window.getParams('namespace');
        window.hashHistory.push(`/historyRollback?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}&namespace=${tenant}`);
    }

    onOpenConfirm() {
        let self = this;
        let type = 'post';
        if (this.opType.trim() === 'I') {
            type = 'delete';
        }
        Dialog.confirm({
            language: window.pageLanguage || 'zh-cn',
            title: window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.please_confirm_rollback'),
            content: <div style={{ marginTop: '-20px' }}>
                <h3>{window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.determine')} {window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.the_following_configuration')}</h3>
                <p>
                    <span style={{ color: '#999', marginRight: 5 }}>Data ID:</span>
                    <span style={{ color: '#c7254e' }}>
                        {self.field.getValue("dataId")}
                    </span>
                </p>
                <p>
                    <span style={{ color: '#999', marginRight: 5 }}>Group:</span>
                    <span style={{ color: '#c7254e' }}>
                        {self.field.getValue("group")}
                    </span>
                </p>

            </div>,
            onOk: function () {
                self.tenant = window.getParams('namespace') || '';
                self.serverId = window.getParams('serverId') || 'center';
                self.dataId = self.field.getValue("dataId");
                self.group = self.field.getValue("group");
                let postData = {
                    appName: self.field.getValue("appName"),
                    dataId: self.dataId,
                    group: self.group,
                    content: self.field.getValue("content"),
                    tenant: self.tenant
                };

                let url = `/nacos/v1/cs/configs`;
                if (self.opType.trim() === 'I') {
                	url = `/nacos/v1/cs/configs?dataId=${self.dataId}&group=${self.group}`;
                	postData = {};
                }
                
                // ajax
                window.request({
                    type: type,
                    contentType: 'application/x-www-form-urlencoded',
                    url: url,
                    data: postData,
                    success: function (data) {
                        if (data === true) {
                            Dialog.alert({ language: window.pageLanguage || 'zh-cn', content: window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.rollback_successful') });
                        }
                    }
                });
            }
        });
    }

    render() {
        const init = this.field.init;
        const formItemLayout = {
            labelCol: {
                fixedSpan: 6
            },
            wrapperCol: {
                span: 18
            }
        };
        return (
            <div style={{ padding: 10 }}>
                <h1>{window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.configuration_rollback')}</h1>
                <Form field={this.field}>

                    <FormItem label="Data ID:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('dataId')} />
                        <div style={{ marginTop: 10 }}>
                            <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>{this.state.showmore ? window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.retracted') : window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.for_more_advanced')}</a>
                        </div>
                    </FormItem>
                    <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
                        <FormItem label="Group:" required {...formItemLayout}>
                            <Input htmlType="text" readOnly={true} {...init('group')} />
                        </FormItem>
                        <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.home')} {...formItemLayout}>
                            <Input htmlType="text" readOnly={true} {...init('appName')} />
                        </FormItem>
                    </div>
                    <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.action_type')} required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('opType')} />
                    </FormItem>
                    <FormItem label="MD5:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('md5')} />
                    </FormItem>
                    <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.configuration')} required {...formItemLayout}>
                        <Input htmlType="text" multiple rows={15} readOnly={true} {...init('content')} />
                    </FormItem>
                    <FormItem label=" " {...formItemLayout}>
                        <Button type="primary" style={{ marginRight: 10 }} onClick={this.onOpenConfirm.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.rollback')}</Button>
                        <Button type="light" onClick={this.goList.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.page.configRollback.return')}</Button>
                    </FormItem>

                </Form>

            </div>
        );
    }

}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ConfigRollback;