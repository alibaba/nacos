import React from 'react'; 
import { Button, Field, Form, Input } from '@alifd/next';
const FormItem = Form.Item; 

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class HistoryDetail extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            showmore: false
        };
        this.edasAppName = window.getParams('edasAppName');
        this.edasAppId = window.getParams('edasAppId');
        this.inApp = this.edasAppName;
        this.field = new Field(this);
        this.dataId = window.getParams('dataId') || 'yanlin';
        this.group = window.getParams('group') || 'DEFAULT_GROUP';
        this.serverId = window.getParams('serverId') || 'center';
        this.nid = window.getParams('nid') || '123509854';
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数
        //this.params = window.location.hash.split('?')[1]||'';
        this.typeMap = {
            'U': window.aliwareIntl.get('com.alibaba.nacos.page.historyDetail.update'),
            'I': window.aliwareIntl.get('com.alibaba.nacos.page.historyDetail.insert'),
            'D': window.aliwareIntl.get('com.alibaba.nacos.page.historyDetail.delete')
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

        window.request({
            url: `/nacos/v1/cs/history?dataId=${this.dataId}&group=${this.group}&nid=${this.nid}`,
            success: function (result) {
                if (result != null) {
                    let data = result;
                    self.field.setValue('dataId', data.dataId);
                    self.field.setValue('content', data.content);
                    self.field.setValue('appName', self.inApp ? self.edasAppName : data.appName);
                    self.field.setValue('envs', self.serverId);
                    self.field.setValue('opType', self.typeMap[data.opType.trim()]);
                    self.field.setValue('group', data.group);
                    self.field.setValue('md5', data.md5);
                }
            }
        });
    }
    goList() {

        //console.log(`/configurationManagement?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}`)
        window.hashHistory.push(`/historyRollback?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}&namespace=${this.tenant}`);
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
                <h1>{window.aliwareIntl.get('com.alibaba.nacos.page.historyDetail.history_details')}</h1>
                <Form field={this.field}>
                  
                    <FormItem label="Data ID:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('dataId')} />
                        <div style={{ marginTop: 10 }}>
                            <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>{this.state.showmore ? window.aliwareIntl.get('com.alibaba.nacos.page.historyDetail.recipient_from') : window.aliwareIntl.get('com.alibaba.nacos.page.historyDetail.more_advanced_options')}</a>
                        </div>
                    </FormItem>
                    <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
                    <FormItem label="Group:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('group')} />
                    </FormItem>
                    <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.historyDetail.home')} {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('appName')} />
                    </FormItem>
                    </div>
                    <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.historyDetail.action_type')} required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('opType')} />
                    </FormItem>
                    <FormItem label="MD5:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('md5')} />
                    </FormItem>
                    <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.historyDetail.configure_content')} required {...formItemLayout}>
                        <Input htmlType="text" multiple rows={15} readOnly={true} {...init('content')} />
                    </FormItem>
                    <FormItem label=" " {...formItemLayout}>
                        <Button type="primary" onClick={this.goList.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.page.historyDetail.return')}</Button>

                    </FormItem>
                </Form>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default HistoryDetail;