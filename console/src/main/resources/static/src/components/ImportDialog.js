import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alifd/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ImportDialog extends React.Component {
    constructor(props) {
        super(props);
        this.formItemLayout = {
            labelCol: {
                fixedSpan: 4
            },
            wrapperCol: {
                span: 20
            }
        };
        this.allPolicy = [{ value: 'abort', label: aliwareIntl.get('newDiamond.component.ImportDialog.To_terminate_the_import0') }, { value: 'skip', label: aliwareIntl.get('newDiamond.component.ImportDialog.skip1') }, {
            value: 'overwrite',
            label: aliwareIntl.get('newDiamond.component.ImportDialog.cover2')
        }];
        this.defaultPolicy = 'abort';
        this.state = {
            visible: false,
            serverId: '',
            tenant: '',
            policy: this.defaultPolicy,
            policyLabel: aliwareIntl.get('newDiamond.component.ImportDialog.To_terminate_the_import0')
        };
    }

    componentDidMount() {}

    openDialog(payload, callback) {
        this.callback = callback;
        this.setState({
            visible: true,
            serverId: payload.serverId,
            tenant: payload.tenant
        });
    }

    closeDialog() {
        this.setState({
            visible: false
        });
    }

    setPolicy(...value) {
        this.setState({
            policyLabel: value[1].label,
            policy: value[0]
        });
    }

    formatter(res) {
        if (res.code === 200) {
            return {
                code: '0',
                retData: res
            };
        } else {
            return {
                code: '1',
                error: {
                    message: res.message
                },
                retData: res
            };
        }
    }

    render() {
        let uploadLink = `/diamond-ops/batch/import/serverId/${this.state.serverId}/tenant/${this.state.tenant.id}?policy=${this.state.policy}`;

        if (window.globalConfig.isParentEdas()) {
            uploadLink = '/authgw/' + window.edasprefix + uploadLink;
        }
        const helpTip = <Balloon trigger={<span>Data ID <Icon type={"help"} size={'small'} style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle' }} /></span>} align={"t"} style={{ marginRight: 5 }} triggerType={"hover"}>	
        <a href={window._getLink && window._getLink("knowDataid")} target={"_blank"}>{aliwareIntl.get('newDiamond.component.ImportDialog.You_can_only_upload._zip_file_format0') /*只能上传.zip格式的文件*/}</a>	
    </Balloon>;
        const footer = <div><Upload language={window.pageLanguage || 'zh-cn'} listType={"text"} action={uploadLink} limit={1} accept={".zip"} onSuccess={(...args) => {
                this.callback(args[0].retData, this.state.policyLabel);
                this.closeDialog();
            }} onError={(...args) => {
                this.callback(args[0].response.retData, this.state.policyLabel);
                this.closeDialog();
            }} formatter={this.formatter.bind(this)} headers={{ poweredBy: "simpleMVC", projectName: "newDiamond" }}>
                <Button type={"primary"}>{aliwareIntl.get('newDiamond.component.ImportDialog.Upload_File3')}</Button>
            </Upload></div>;

        return <div>
            <Dialog visible={this.state.visible} footer={footer} footerAlign={"center"} language={window.pageLanguage || 'zh-cn'} style={{ width: 480 }} onCancel={this.closeDialog.bind(this)} onClose={this.closeDialog.bind(this)} title={aliwareIntl.get('newDiamond.component.ImportDialog.Import_configuration4') + this.state.serverId + "）"}>
                <Form>
                    <FormItem label={aliwareIntl.get('newDiamond.component.ImportDialog.target_space5')} {...this.formItemLayout}>
                        <p><span style={{ color: '#33cde5' }}>{this.state.tenant.name}</span>{" | " + this.state.tenant.id}
                        </p>
                    </FormItem>
                    <FormItem label={aliwareIntl.get('newDiamond.component.ImportDialog.the_same_configuration6')} {...this.formItemLayout}>
                        <Select size={"medium"} hasArrow defaultValue={this.defaultPolicy} dataSource={this.allPolicy} onChange={this.setPolicy.bind(this)} language={aliwareIntl.currentLanguageCode}>
                        </Select>
                    </FormItem>
                </Form>
                
                <div style={{ textAlign: "center" }}><Icon type={"warning"} style={{ color: '#ff8a00', marginRight: 5, verticalAlign: 'middle' }} />{aliwareIntl.get('newDiamond.component.ImportDialog.file_upload_directly_after_importing_the_configuration,_please_be_sure_to_exercise_caution7')}{helpTip}</div>
            </Dialog>
        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ImportDialog;