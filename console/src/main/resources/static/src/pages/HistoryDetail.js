import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alife/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class HistoryDetail extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            showmore: false
        };
        this.edasAppName = getParams('edasAppName');
        this.edasAppId = getParams('edasAppId');
        this.inApp = this.edasAppName;
        this.field = new Field(this);
        this.dataId = getParams('dataId') || 'yanlin';
        this.group = getParams('group') || 'DEFAULT_GROUP';
        this.serverId = getParams('serverId') || 'center';
        this.nid = getParams('nid') || '123509854';
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        //this.params = window.location.hash.split('?')[1]||'';
        this.typeMap = {
            'U': aliwareIntl.get('com.alibaba.newDiamond.page.historyDetail.update'),
            'I': aliwareIntl.get('com.alibaba.newDiamond.page.historyDetail.insert'),
            'D': aliwareIntl.get('com.alibaba.newDiamond.page.historyDetail.delete')
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

        request({
            url: `/diamond-ops/historys/detail/serverId/${this.serverId}?dataId=${this.dataId}&group=${this.group}&nid=${this.nid}`,
            success: function (result) {

                let data = result.data;
                self.field.setValue('dataId', data.dataId);
                self.field.setValue('content', data.content);
                self.field.setValue('appName', self.inApp ? self.edasAppName : data.appName);
                self.field.setValue('envs', self.serverId);
                self.field.setValue('opType', self.typeMap[data.opType]);
                self.field.setValue('group', data.group);
                self.field.setValue('md5', data.md5);
            }
        });
    }
    goList() {

        //console.log(`/configurationManagement?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}`)
        hashHistory.push(`/historyRollback?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}&namespace=${this.tenant}`);
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
                <h1>{aliwareIntl.get('com.alibaba.newDiamond.page.historyDetail.history_details')}</h1>
                <Form field={this.field}>
                  
                    <FormItem label="Data ID:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('dataId')} />
                        <div style={{ marginTop: 10 }}>
                            <a style={{ fontSize: '12px' }} href="javascript:;" onClick={this.toggleMore.bind(this)}>{this.state.showmore ? aliwareIntl.get('com.alibaba.newDiamond.page.historyDetail.recipient_from') : aliwareIntl.get('com.alibaba.newDiamond.page.historyDetail.more_advanced_options')}</a>
                        </div>
                    </FormItem>
                    <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
                    <FormItem label="Group:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('group')} />
                    </FormItem>
                    <FormItem label={aliwareIntl.get('com.alibaba.newDiamond.page.historyDetail.home')} {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('appName')} />
                    </FormItem>
                    </div>
                    <FormItem label={aliwareIntl.get('com.alibaba.newDiamond.page.historyDetail.belongs_to_the_environment')} required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('envs')} />
                    </FormItem>
                    <FormItem label={aliwareIntl.get('com.alibaba.newDiamond.page.historyDetail.action_type')} required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('opType')} />
                    </FormItem>
                    <FormItem label="MD5:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('md5')} />
                    </FormItem>
                    <FormItem label={aliwareIntl.get('com.alibaba.newDiamond.page.historyDetail.configure_content')} required {...formItemLayout}>
                        <Input htmlType="text" multiple rows={15} readOnly={true} {...init('content')} />
                    </FormItem>
                    <FormItem label=" " {...formItemLayout}>
                        <Button type="primary" onClick={this.goList.bind(this)}>{aliwareIntl.get('com.alibaba.newDiamond.page.historyDetail.return')}</Button>

                    </FormItem>
                </Form>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default HistoryDetail;