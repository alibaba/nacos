import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alifd/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

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
            tag: [{ title: aliwareIntl.get('com.alibaba.cspupcloud.page.configdetail.official'), key: 'normal' }]
        };
        this.field = new Field(this);
        this.dataId = getParams('dataId') || 'yanlin';
        this.group = getParams('group') || 'DEFAULT_GROUP';
        this.ips = '';
        this.valueMap = {}; //存储不同版本的数据
        this.tenant = getParams('namespace') || '';
        this.searchDataId = getParams('searchDataId') || '';
        this.searchGroup = getParams('searchGroup') || '';
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
        this.tenant = getParams('namespace') || '';
        this.serverId = getParams('serverId') || 'center';
        let url = `/diamond-ops/configList/configTags/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}/tenant/${this.tenant}?id=`;
        if (this.tenant === 'global' || !this.tenant) {
            url = `/diamond-ops/configList/configTags/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}?id=`;
        }
        request({
            url: url,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (result) {

                if (result.code === 200) {

                    if (result.data.length > 0) {
                        //如果存在beta
                        let sufex = new Date().getTime();
                        let tag = [{ title: aliwareIntl.get('com.alibaba.cspupcloud.page.configdetail.official'), key: 'normal' }, { title: 'BETA', key: 'beta' }];
                        self.setState({
                            tag: tag,
                            hasbeta: true
                        });
                        self.getBeta();
                    }
                } else {}
            },
            complete: function () {
                self.closeLoading();
            }
        });
    }
    getBeta() {

        let self = this;
        this.tenant = getParams('namespace') || '';
        this.serverId = getParams('serverId') || 'center';
        let url = `/diamond-ops/configList/edit/beta/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}/tenant/${this.tenant}?id=`;
        if (this.tenant === 'global' || !this.tenant) {
            url = `/diamond-ops/configList/edit/beta/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}?id=`;
        }
        request({
            url: url,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (result) {

                if (result.code === 200) {
                    self.valueMap['beta'] = result.data;
                } else {}
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
        this.serverId = getParams('serverId') || 'center';
        this.tenant = getParams('namespace') || '';
        this.edasAppName = getParams('edasAppName') || '';
        this.inApp = this.edasAppName;
        let url = `/diamond-ops/configList/detail/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}/tenant/${this.tenant}?id=`;

        if (this.tenant === 'global' || !this.tenant) {
            url = `/diamond-ops/configList/detail/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}?id=`;
        }
        request({
            url: url,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (result) {
                if (result.code === 200) {
                    let data = result.data;
                    self.valueMap['normal'] = data;
                    self.field.setValue('dataId', data.dataId);
                    self.field.setValue('content', data.content);
                    self.field.setValue('appName', self.inApp ? self.edasAppName : data.appName);
                    self.field.setValue('envs', self.serverId);
                    self.field.setValue('group', data.group);
                    self.field.setValue('config_tags', data.config_tags);
                    self.field.setValue('desc', data.desc);
                    self.field.setValue('md5', data.md5);
                } else {
                    Dialog.alert({
                        title: aliwareIntl.get('com.alibaba.cspupcloud.page.configdetail.error'),
                        content: result.message,
                        language: aliwareIntl.currentLanguageCode
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
        hashHistory.push(`/configurationManagement?serverId=${this.serverId}&group=${this.searchGroup}&dataId=${this.searchDataId}&namespace=${this.tenant}`);
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
                    <h1 style={{ position: 'relative', width: '100%' }}>{aliwareIntl.get('com.alibaba.cspupcloud.page.configdetail.configuration_details')}</h1>
                    {this.state.hasbeta ? <div style={{ display: 'inline-block', height: 40, width: '80%', overflow: 'hidden' }}>

                        <Tab shape={'wrapped'} onChange={this.changeTab.bind(this)} lazyLoad={false} activeKey={this.state.activeKey}>
                            {this.state.tag.map(tab => <TabPane title={tab.title} key={tab.key}></TabPane>)}
                        </Tab>

                    </div> : ''}
                    <Form direction={"ver"} field={this.field}>

                        <FormItem label={"Data ID:"} required {...formItemLayout}>
                            <Input htmlType={"text"} readOnly={true} {...init('dataId')} />
                            <div style={{ marginTop: 10 }}>
                                <a style={{ fontSize: '12px' }} href={"javascript:;"} onClick={this.toggleMore.bind(this)}>{this.state.showmore ? aliwareIntl.get('com.alibaba.cspupcloud.page.configdetail.recipient_from') : aliwareIntl.get('com.alibaba.cspupcloud.page.configdetail.more_advanced_options')}</a>
                            </div>
                        </FormItem>

                        {this.state.showmore ? <div>
                            <FormItem label={"Group:"} required {...formItemLayout}>
                                <Input htmlType={"text"} readOnly={true} {...init('group')} />
                            </FormItem>
                            <FormItem label={aliwareIntl.get('com.alibaba.cspupcloud.page.configdetail.home')} {...formItemLayout}>
                                <Input htmlType={"text"} readOnly={true} {...init('appName')} />
                            </FormItem>

                            <FormItem label={aliwareIntl.get('newDiamond.page.configdetail.Tags')} {...formItemLayout}>
                                <Input htmlType={"text"} readOnly={true} {...init('config_tags')} />
                            </FormItem>
                        </div> : ''}

                        <FormItem label={aliwareIntl.get('newDiamond.page.configdetail.Description')} {...formItemLayout}>
                            <Input htmlType={"text"} multiple rows={3} readOnly={true} {...init('desc')} />
                        </FormItem>
                        <FormItem label={aliwareIntl.get('com.alibaba.cspupcloud.page.configdetail.belongs_to_the_environment')} required {...formItemLayout}>
                            <Input htmlType={"text"} readOnly={true} {...init('envs')} />
                        </FormItem>
                        {activeKey === 'normal' ? '' : <FormItem label={aliwareIntl.get('com.alibaba.cspupcloud.page.configdetail.beta_release')} {...formItemLayout}>

                            <div style={{ width: '100%' }} id={'betaips'}>
                                <Input multiple style={{ width: '100%' }} value={this.state.ips} readOnly={true} placeholder={"multiple"} placeholder={'127.0.0.1,127.0.0.2'} />
                            </div>
                        </FormItem>}
                        <FormItem label={"MD5:"} required {...formItemLayout}>
                            <Input htmlType={"text"} readOnly={true} {...init('md5')} />
                        </FormItem>
                        <FormItem label={aliwareIntl.get('newDiamond.page.configdetail.Data_encryption0')} {...formItemLayout}>
                                <Switch checkedChildren={aliwareIntl.get('newDiamond.page.configdetail.Open0') /*开*/} unCheckedChildren={aliwareIntl.get('newDiamond.page.configdetail.off1') /*关*/} size={"small"} checked={this.state.switchEncrypt} disabled />
                        </FormItem>
                        <FormItem label={aliwareIntl.get('com.alibaba.cspupcloud.page.configdetail.configuration')} required {...formItemLayout}>
                            <Input htmlType={"text"} multiple rows={15} readOnly={true} {...init('content')} />
                        </FormItem>
                        <FormItem label={" "} {...formItemLayout}>

                            <Button type={"primary"} onClick={this.goList.bind(this)}>{aliwareIntl.get('com.alibaba.cspupcloud.page.configdetail.return')}</Button>

                        </FormItem>
                    </Form>
                </Loading>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default Configdetail;