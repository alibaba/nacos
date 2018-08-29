import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alifd/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class CloneDialog extends React.Component {

    constructor(props) {
        super(props);
        this.allPolicy = [{ value: 'abort', label: aliwareIntl.get('newDiamond.component.CloneDialog.Terminate_the_clone0') }, { value: 'skip', label: aliwareIntl.get('newDiamond.component.CloneDialog.skip') }, { value: 'overwrite', label: aliwareIntl.get('newDiamond.component.CloneDialog.cover') }];
        this.defaultPolicy = 'abort';
        this.state = {
            visible: false,
            serverId: '',
            tenantFrom: {},
            tenantTo: '',
            dataId: '',
            group: '',
            appName: '',
            configTags: '',
            records: [],
            namespaces: [],
            policy: this.defaultPolicy,
            policyLabel: aliwareIntl.get('newDiamond.component.CloneDialog.Terminate_the_clone0'),
            total: 0
        };
        this.field = new Field(this);
        this.formItemLayout = {
            labelCol: {
                fixedSpan: 6
            },
            wrapperCol: {
                span: 18
            }
        };
    }

    componentDidMount() {}

    openDialog(payload, callback) {
        let serverId = getParams('serverId') || 'center';
        this.checkData = payload.checkData;
        this.callback = callback;
        request({
            type: 'get',
            url: `/diamond-ops/service/serverId/${serverId}/namespaceInfo`,
            success: res => {
                if (res.code === 200) {
                    let dataSource = [];
                    res.data.forEach(value => {
                        if (value.namespace !== payload.tenantFrom.id) {
                            dataSource.push({
                                value: value.namespace,
                                label: value.namespaceShowName + " | " + value.namespace
                            });
                        }
                    });
                    this.setState({
                        visible: true,
                        serverId: payload.serverId,
                        tenantFrom: payload.tenantFrom,
                        tenantTo: '',
                        dataId: payload.dataId,
                        group: payload.group,
                        appName: payload.appName,
                        configTags: payload.configTags,
                        records: payload.records,
                        namespaces: dataSource,
                        total: payload.total
                    });
                    this.field.setValue('select', '');
                } else {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        title: aliwareIntl.get('newDiamond.component.CloneDialog.get_the_namespace_failed'),
                        content: res.message
                    });
                }
            }
        });
    }

    closeDialog() {
        this.setState({
            visible: false
        });
    }

    setTenantTo(value) {
        this.field.setValue(value);
        this.setState({
            tenantTo: value
        });
    }

    setPolicy(...value) {
        this.setState({
            policyLabel: value[1].label,
            policy: value[0]
        });
    }

    getQuery() {
        if (this.state.records.length > 0) {
            return aliwareIntl.get('newDiamond.component.CloneDialog.|_the_selected_entry4');
        }
        if (this.state.dataId === '' && this.state.group === '' && this.state.appName === '' && this.state.configTags.length === 0) {
            return '';
        }
        let query = " |";
        if (this.state.dataId !== '') {
            query += ' DataId: ' + this.state.dataId + ',';
        }
        if (this.state.group !== '') {
            query += ' Group: ' + this.state.group + ',';
        }
        if (this.state.appName !== '') {
            query += aliwareIntl.get('newDiamond.component.CloneDialog.HOME_Application') + this.state.appName + ',';
        }
        if (this.state.configTags.length !== 0) {
            query += aliwareIntl.get('newDiamond.component.CloneDialog.tags') + this.state.configTags + ',';
        }
        return query.substr(0, query.length - 1);
    }

    doClone() {
        this.field.validate((errors, values) => {
            if (errors) {
                return;
            }
            this.closeDialog();
            this.checkData.tenantTo = this.state.tenantTo;
            this.checkData.policy = this.state.policy;
            this.callback(this.checkData, this.state.policyLabel);
        });
    }

    render() {
        const init = this.field.init;
        const footer = <div><Button type="primary" onClick={this.doClone.bind(this)} {...{ "disabled": this.state.total <= 0 }}>{aliwareIntl.get('newDiamond.component.CloneDialog.start_cloning')}</Button>
        </div>;

        return <div>
            <Dialog visible={this.state.visible} footer={footer} footerAlign="center" language={window.pageLanguage || 'zh-cn'} style={{ width: 555 }} onCancel={this.closeDialog.bind(this)} onClose={this.closeDialog.bind(this)} title={aliwareIntl.get('newDiamond.component.CloneDialog.configuration_cloning\uFF08') + this.state.serverId + "）"}>
                <Form field={this.field}>
                    <FormItem label={aliwareIntl.get('newDiamond.component.CloneDialog.source_space')} {...this.formItemLayout}>
                        <p><span style={{ color: '#33cde5' }}>{this.state.tenantFrom.name}</span>{" | " + this.state.tenantFrom.id}
                        </p>
                    </FormItem>
                    <FormItem label={aliwareIntl.get('newDiamond.component.CloneDialog.configuration_number')} {...this.formItemLayout}>
                        <p><span style={{ color: '#33cde5' }}>{this.state.total}</span> {this.getQuery()} </p>
                    </FormItem>
                    <FormItem label={aliwareIntl.get('newDiamond.component.CloneDialog.target_space')} {...this.formItemLayout}>
                        <Combobox style={{ width: '80%' }} size="medium" hasArrow placeholder={aliwareIntl.get('newDiamond.component.CloneDialog.select_namespace')} dataSource={this.state.namespaces} {...init('select', {
                            props: {
                                onChange: this.setTenantTo.bind(this)
                            },
                            rules: [{ required: true, message: aliwareIntl.get('newDiamond.component.CloneDialog.select_namespace') }]
                        })} language={aliwareIntl.currentLanguageCode}>
                        </Combobox>
                    </FormItem>
                    <FormItem label={aliwareIntl.get('newDiamond.component.CloneDialog.the_same_configuration')} {...this.formItemLayout}>
                        <Select size="medium" hasArrow defaultValue={this.defaultPolicy} dataSource={this.allPolicy} onChange={this.setPolicy.bind(this)} language={aliwareIntl.currentLanguageCode}>
                        </Select>
                    </FormItem>
                </Form>
            </Dialog>
        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default CloneDialog;