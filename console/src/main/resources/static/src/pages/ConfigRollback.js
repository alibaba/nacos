import React from 'react'; 
import { Button, Dialog, Field, Form, Input } from '@alifd/next';
const FormItem = Form.Item; 

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ConfigRollback extends React.Component {

    constructor(props) {
        super(props);
        this.field = new Field(this);
        this.dataId = getParams('dataId') || 'yanlin';
        this.group = getParams('group') || 'DEFAULT_GROUP';
        this.serverId = getParams('serverId') || 'center';
        this.nid = getParams('nid') || '';
        this.state = {
            envName: '',
            visible: false,
            showmore: false
        };
        //this.params = window.location.hash.split('?')[1]||'';	
        this.typeMap = { //操作映射提示
            'U': 'publish',
            'I': aliwareIntl.get('com.alibaba.nacos.page.configRollback.delete'),
            'D': 'publish'
        };
        this.typeMapName = { //操作映射名
            'U': aliwareIntl.get('com.alibaba.nacos.page.configRollback.updated'),
            'I': aliwareIntl.get('com.alibaba.nacos.page.configRollback.inserted'),
            'D': aliwareIntl.get('com.alibaba.nacos.page.configRollback.delete')
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
        this.tenant = getParams('namespace') || '';
        this.serverId = getParams('serverId') || 'center';
        let url = `/nacos/v1/cs/history?dataId=${this.dataId}&group=${this.group}&nid=${this.nid}`;
//        let url = `/diamond-ops/historys/detail/serverId/${this.serverId}?dataId=${this.dataId}&group=${this.group}&nid=${this.nid}`;

        request({
            url: url,
            success: function (result) {
                if (result != null) {
                    let data = result;
                    let envs = data.envs;
                    let envName = self.serverId;
                    // for (let i = 0; i < envs.length; i++) {
                    //     let obj = envs[i];
                    //     if (obj.serverId === self.serverId) {
                    //         envName = obj.name;
                    //     }
                    // }
                    self.id = data.id; //详情的id
                    self.field.setValue('dataId', data.dataId);
                    self.field.setValue('content', data.content);
                    self.field.setValue('appName', data.appName);

                    // self.field.setValue('envs', data.envs);
                    self.field.setValue('opType', self.typeMapName[data.opType]);
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
        let tenant = getParams('namespace');
        hashHistory.push(`/historyRollback?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}&namespace=${tenant}`);
    }

    onOpenConfirm() {
        let self = this;
        let content = this.typeMap[this.opType];
        let type = 'post';
//        let type = 'put';
        if (this.opType === 'I') {
            type = 'delete';
        }
        Dialog.confirm({
            language: window.pageLanguage || 'zh-cn',
            title: aliwareIntl.get('com.alibaba.nacos.page.configRollback.please_confirm_rollback'),
            content: <div style={{ marginTop: '-20px' }}>
                <h3>{aliwareIntl.get('com.alibaba.nacos.page.configRollback.determine')} {aliwareIntl.get('com.alibaba.nacos.page.configRollback.the_following_configuration')}</h3>
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
                <p>
                    <span style={{ color: '#999', marginRight: 5 }}>{aliwareIntl.get('com.alibaba.nacos.page.configRollback.environment')}</span>
                    <span style={{ color: '#c7254e' }}>
                        {this.state.envName}
                    </span>
                </p>

            </div>,
            onOk: function () {

                self.tenant = getParams('namespace') || '';
                self.serverId = getParams('serverId') || 'center';

                let postData = {
                    appName: self.field.getValue("appName"),
                    dataId: self.field.getValue("dataId"),
                    group: self.field.getValue("group"),
                    content: self.field.getValue("content"),
//                    targetEnvs: [self.serverId],
                    tenant: self.tenant
                };
                
                let url = `/nacos/v1/cs/configs`;
//                let url = `/diamond-ops/configList/serverId/${self.serverId}/dataId/${self.dataId}/group/${postData.group}/tenant/${self.tenant}?id=${self.id}`;
//                if (self.tenant === 'global' || !self.tenant) {
//                    url = `/diamond-ops/configList/serverId/${self.serverId}/dataId/${self.dataId}/group/${postData.group}?id=${self.id}`;
//                }
                // ajax		
                request({
                    type: type,
                    contentType: 'application/x-www-form-urlencoded',
//                    contentType: 'application/json',
                    url: url,
                    data: postData,
//                    data: JSON.stringify(postData),
                    success: function (data) {
                        if (data.code === 200) {
                            Dialog.alert({ language: window.pageLanguage || 'zh-cn', content: aliwareIntl.get('com.alibaba.nacos.page.configRollback.rollback_successful') });
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
                <h1>{aliwareIntl.get('com.alibaba.nacos.page.configRollback.configuration_rollback')}</h1>
                <Form field={this.field}>

                    <FormItem label="Data ID:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('dataId')} />
                        <div style={{ marginTop: 10 }}>
                            <a style={{ fontSize: '12px' }} href="javascript:;" onClick={this.toggleMore.bind(this)}>{this.state.showmore ? aliwareIntl.get('com.alibaba.nacos.page.configRollback.retracted') : aliwareIntl.get('com.alibaba.nacos.page.configRollback.for_more_advanced')}</a>
                        </div>
                    </FormItem>
                    <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
                        <FormItem label="Group:" required {...formItemLayout}>
                            <Input htmlType="text" readOnly={true} {...init('group')} />
                        </FormItem>
                        <FormItem label={aliwareIntl.get('com.alibaba.nacos.page.configRollback.home')} {...formItemLayout}>
                            <Input htmlType="text" readOnly={true} {...init('appName')} />
                        </FormItem>
                    </div>
                    <FormItem label={aliwareIntl.get('com.alibaba.nacos.page.configRollback.belongs_to')} required {...formItemLayout}>
                        <Input htmlType="hidden" {...init('envs')} />
                        <Input htmlType="text" readOnly={true} {...init('envName')} />
                    </FormItem>
                    <FormItem label={aliwareIntl.get('com.alibaba.nacos.page.configRollback.action_type')} required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('opType')} />
                    </FormItem>
                    <FormItem label="MD5:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('md5')} />
                    </FormItem>
                    <FormItem label={aliwareIntl.get('com.alibaba.nacos.page.configRollback.configuration')} required {...formItemLayout}>
                        <Input htmlType="text" multiple rows={15} readOnly={true} {...init('content')} />
                    </FormItem>
                    <FormItem label=" " {...formItemLayout}>
                        <Button type="primary" style={{ marginRight: 10 }} onClick={this.onOpenConfirm.bind(this)}>{aliwareIntl.get('com.alibaba.nacos.page.configRollback.rollback')}</Button>
                        <Button type="light" onClick={this.goList.bind(this)}>{aliwareIntl.get('com.alibaba.nacos.page.configRollback.return')}</Button>
                    </FormItem>

                </Form>

            </div>
        );
    }

}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ConfigRollback;