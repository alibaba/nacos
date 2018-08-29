import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alifd/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;
import SuccessDialog from '../components/SuccessDialog' ;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class EdasNewconfig extends React.Component {
    constructor(props) {
        super(props);
        this.field = new Field(this);
        this.edasAppName = getParams('edasAppName') || '';
        this.edasAppId = getParams('edasAppId') || '';
        this.inApp = this.edasAppName;
        this.field.setValue('appName', this.inApp ? this.edasAppName : '');
        this.inEdas = window.globalConfig.isParentEdas();
        this.dataId = getParams('dataId') || '';
        this.group = getParams('group') || 'DEFAULT_GROUP';
        this.appName = getParams('appName')|| '';
        this.state = {
            configType: 'text',
            codeValue: ``,
            envname: '',
            targetEnvName: '',
            groups: [],
            groupNames: [],
            envlist: [],
            tagLst: [],
            config_tags: [],
            envvalues: [],
            showmore: false,
            loading: false,
            encrypt: false,
            addonBefore: '',
            showGroupWarning: false
        };
        this.codeValue = '';
        this.mode = 'text';
        this.ips = '';
    }

    componentDidMount() {
        this.betaips = document.getElementById('betaips');
        //this.createCodeMirror('text', '');
        this.chontenttab = document.getElementById('chontenttab'); //diff标签
        this.tenant = getParams('namespace') || '';
        this.getGroupInfo();
        this.field.setValue('group', this.group);
        this.getGroupsList();
        this.getTags();
        this.getTagLst();
        if (!window.monaco) {
            window.importEditor(() => {
                this.initMoacoEditor();
            });
        } else {
            this.initMoacoEditor();
        }
    }
    changeModel(type) {
        if (!this.monacoEditor) {
            $('#container').empty();
            this.monacoEditor = monaco.editor.create(document.getElementById('container'), {
                model: null
            });
            return;
        }
        let oldModel = this.monacoEditor.getModel();
        let oldValue = this.monacoEditor.getValue();
        let newModel = monaco.editor.createModel(oldValue, type);
        this.monacoEditor.setModel(newModel);
        //this.monacoEditor.setValue('xx')
        if (oldModel) {
            oldModel.dispose();
        }
    }
    initMoacoEditor() {

        // require(['vs/editor/editor.main'], () => {

        //     this.monacoEditor = monaco.editor.create(document.getElementById('container'), {
        //         value: this.codeValue,
        //         language: this.state.configType,
        //         folding: false,
        //         codeLens: true,
        //         selectOnLineNumbers: true,
        //         roundedSelection: false,
        //         readOnly: false,
        //         lineNumbersMinChars: true,
        //         theme: 'vs-dark',
        //         wordWrapColumn: 120,
        //         folding: true,
        //         showFoldingControls: 'always',
        //         wordWrap: 'wordWrapColumn',
        //         cursorStyle: 'line',
        //         automaticLayout: true,
        //     });
        //     // this.monacoEditor.onDidChangeModelContent((event) => {
        //     //     const value = this.monacoEditor.getValue();

        //     //     // Always refer to the latest value
        //     //     this.__current_value = value;

        //     //     // Only invoking when user input changed
        //     //     if (!this.__prevent_trigger_change_event) {
        //     //         this.props.onChange(value, event);
        //     //     }
        //     // });


        // });
        this.monacoEditor = monaco.editor.create(document.getElementById('container'), {
            value: this.codeValue,
            language: this.state.configType,
            folding: false,
            codeLens: true,
            selectOnLineNumbers: true,
            roundedSelection: false,
            readOnly: false,
            lineNumbersMinChars: true,
            theme: 'vs-dark',
            wordWrapColumn: 120,
            folding: true,
            showFoldingControls: 'always',
            wordWrap: 'wordWrapColumn',
            cursorStyle: 'line',
            automaticLayout: true
        });
    }

    getGroupsList() {
        let self = this;
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        this.serverId = getParams('serverId') || '';
        request({
            type: 'get',
            beforeSend: function () {},
            url: self.inEdas ? '/diamond-ops/service/group' : `/diamond-ops/configList/groups_by_tenant/serverId/${this.serverId}/tenant/${this.tenant}`,
            success: res => {
                if (res.code === 200) {
                    let data = res.data;
                    let groups = [];
                    let groupNames = [];
                    for (let i = 0; i < data.length; i++) {
                        groups.push({
                            label: data[i],
                            value: data[i]
                        });
                        groupNames.push(data[i]);
                    }
                    this.setState({
                        groups: groups,
                        groupNames: groupNames
                    });
                    if (this.inEdas) {
                        if (groups.length == 0) {
                            this.setState({
                                showGroupWarning: this.state.groupNames.indexOf(this.group) < 0
                            });
                        } else {
                            this.setGroup('');
                        }
                        
                    }
                }
            }
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
                        let tag = [{ title: aliwareIntl.get('com.alibaba.newDiamond.page.configeditor.official'), key: 'normal' }, { title: 'BETA', key: 'beta' }];
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
    getTagLst() {
        let self = this;
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        this.serverId = getParams('serverId') || '';
        request({

            type: 'get',
            beforeSend: function () {},
            url: `/diamond-ops/configList/tags/serverId/${this.serverId}/tenant/${this.tenant}`,
            success: res => {
                if (res.code === 200) {
                    let data = res.data;
                    let tagLst = [];
                    for (let i = 0; i < data.length; i++) {
                        tagLst.push({
                            label: data[i],
                            value: data[i]
                        });
                    }
                    this.setState({
                        tagLst: tagLst
                    });
                }
            }
        });
    }
    setGroup(value) {
        this.group = value || '';
        this.field.setValue('group', this.group);
        if (this.inEdas) {
            this.setState({
                showGroupWarning: ((this.group !== '') && (this.state.groupNames.indexOf(value) < 0))
            });
        }
    }

    setConfigTags(value) {
        if (value.length > 5) {
            value.pop();
        }
        value.forEach((v, i) => {
            if (v.indexOf(',') !== -1 || v.indexOf('=') !== -1) {
                value.splice(i, 1);
            }
        });
        this.setState({
            config_tags: value
        });
    }

    onInputUpdate(value) {
        if (this.inputtimmer) {
            clearTimeout(this.inputtimmer);
        }
        this.inputtimmer = setTimeout(() => {
            let tagLst = this.state.tagLst,
                hastag = false;
            tagLst.forEach((v, i) => {
                if (v.value == value) {
                    hastag = true;
                }
            });
            if (!hastag) {
                tagLst.push({
                    value: value,
                    label: value,
                    time: Math.random()
                });
            }
            this.setState({ tagLst: tagLst });
        }, 500);
    }

    toggleMore() {
        this.setState({
            showmore: !this.state.showmore
        });
    }
    goList() {
        this.tenant = getParams('namespace') || '';
        this.serverId = getParams('serverId') || '';
        //console.log(`/configurationManagement?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}`)
        hashHistory.push(`/edasconfigurationManagement?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}&namespace=${this.tenant}&appId=${this.edasAppId}&edasAppId=${this.edasAppId}&appName=${this.appName}&edasAppName=${this.edasAppName}`);
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

    newChangeConfig(value) {
        this.setState({
            configType: value
        });

        this.changeModel(value);
    }
    setCodeValue(value) {

        this.setState({
            codeValue: value
        });
    }

    publishConfig() {
        this.field.validate((errors, values) => {
            if (errors) {
                return;
            }
            let content = '';
            let self = this;
            // if (this.commoneditor) {
            //     content = this.commoneditor.doc.getValue();
            //     if (content && content.length >= 10240) {
            //         // this.field.setError()
            //         Dialog.alert({
            //             language: window.pageLanguage || 'zh-cn',
            //             content: aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.configuration_contentmax')
            //         });
            //         return;
            //     }
            // } else {
            //     content = this.codeValue;
            // }
            if (this.monacoEditor) {
                content = this.monacoEditor.getValue();
            } else {
                content = this.codeValue;
            }
            if (!content) {
                return;
            }
            this.tenant = getParams('namespace') || '';
            let payload = {
                dataId: self.state.addonBefore + this.field.getValue('dataId'),
                appName: this.edasAppId ,
                group: this.field.getValue('group'),
                desc: this.field.getValue('desc'),
                config_tags: this.state.config_tags.join(),
                content: content,
                type: this.state.configType,
                betaIps: this.ip || '',
                targetEnvs: this.targetEnvs || ['center'],
                tenant: this.tenant
            };
            this.serverId = getParams('serverId') || 'center';
            let url = `/diamond-ops/configList/serverId/${this.serverId}/dataId/${payload.dataId}/group/${payload.group}/tenant/${this.tenant}`;

            if (!this.tenant) {
                url = `/diamond-ops/configList/serverId/${this.serverId}/dataId/${payload.dataId}/group/${payload.group}`;
            }

            request({
                type: 'post',
                contentType: 'application/json',
                url: url,
                data: JSON.stringify(payload),
                beforeSend: () => {
                    this.openLoading();
                },
                success: function (res) {
                    let _payload = {};
                    _payload.maintitle = aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.new_listing_main');
                    _payload.title = aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.new_listing');
                    _payload.content = '';
                    _payload.dataId = payload.dataId;
                    _payload.group = payload.group;
                    if (res.code === 200) {
                        self.group = payload.group;
                        self.dataId = payload.dataId;
                        setParams({ group: payload.group, dataId: payload.dataId }); //设置参数
                        _payload.isok = true;
                    } else {
                        _payload.isok = false;
                        _payload.message = res.message;
                    }
                    self.closeLoading();
                    self.refs['success'].openDialog(_payload);
                },
                error: function (res) {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        content: aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.publish_failed')
                    });
                    self.closeLoading();
                }

            });
        });
    }
    getGroupInfo() {
        let self = this;
        this.serverId = getParams('serverId') || 'center';
        request({
            url: `/diamond-ops/env/serverId/${this.serverId}/groupInfo`,
            success: function (res) {

                if (res.code === 0) {

                    let data = res.data;
                    let envvalues = [];
                    let envlist = [];
                    let serverId = getParams('serverId') || 'daily';
                    for (let i = 0; i < data.length; i++) {
                        envlist.push({
                            label: data[i].name,
                            value: data[i].serverId
                        });
                        if (serverId === data[i].serverId) {
                            //如果查到的serverId更路径id相关
                            envvalues.push(data[i].serverId);
                        }
                    }
                    self.targetEnvs = envvalues;
                    self.setState({
                        envvalues: envvalues,
                        envlist: envvalues //只保留当前环境
                    });
                }
            }
        });
    }

    changeEnv(values) {
        this.targetEnvs = values;
        this.setState({
            envvalues: values
        });
    }

    switchEncrypt(value) {
        if (value) {
            request({
                type: 'get',
                beforeSend: function () {},
                url: `/diamond-ops/configList/isOpenKMS`,
                success: res => {
                    if (res.code === 200) {
                        this.setState({
                            encrypt: value
                        });
                        this.setState({
                            addonBefore: "cipher-"
                        });
                    } else if (res.code === 1403) {
                        let data = res.data;
                        let titleTmp = aliwareIntl.get('newDiamond.page.newconfig.The_opening_of_the_data_encryption-related_services0');
                        let contentTmp1 = "";
                        let contentTmp2 = "";
                        if (data.kmsServiceStatus != 200) {
                            contentTmp1 = <div>{data.kmsMsg}<a href={window._getLink && window._getLink("kmsOpen")} target="_blank">{aliwareIntl.get('newDiamond.page.newconfig._to_go_to_the_opening_of1')}</a></div>;
                        }
                        if (data.sts2KmsRightStatus != 200) {
                            contentTmp2 = <div>{data.sts2KmsMsg}<a href={window._getLink && window._getLink("kmsAuthorize")} target="_blank">{aliwareIntl.get('newDiamond.page.newconfig.to_the_authorization_of2')}</a></div>;
                        }
                        let contentTmp = <div style={{ "font-size": "14px" }}>{contentTmp1}{contentTmp2}</div>;
                        Dialog.alert({
                            language: window.pageLanguage || 'zh-cn',
                            title: titleTmp,
                            content: contentTmp
                        });
                        this.setState({
                            encrypt: false
                        });
                    } else {
                        Dialog.alert({
                            language: window.pageLanguage || 'zh-cn',
                            title: aliwareIntl.get('newDiamond.page.newconfig.The_opening_of_the_data_encryption-related_services0'),
                            content: res.message
                        });
                        this.setState({
                            encrypt: false
                        });
                    }
                }
            });
        } else {
            this.setState({
                encrypt: value
            });
            this.setState({
                addonBefore: ""
            });
        }
    }

    changeBeta(selected) {
        if (selected) {
            this.betaips.style.display = 'block';
        } else {
            this.betaips.style.display = 'none';
        }
    }
    getIps(value) {
        this.ips = value;
    }

    validateChart(rule, value, callback) {
        const { getValue } = this.field;
        const chartReg = /[@#\$%\^&\*]+/g;

        if (chartReg.test(value)) {
            callback(aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.do_not_ente'));
        } else {
            callback();
        }
    }
    render() {
        const { init, getError, getState } = this.field;
        const formItemLayout = {
            labelCol: {
                span: 2
            },
            wrapperCol: {
                span: 22
            }
        };

        // const list = [{
        //     value: 0,
        //     label: 'TEXT'
        // }, {
        //     value: 1,
        //     label: 'JSON'
        // }, {
        //     value: 2,
        //     label: 'XML'
        // }];
        const list = [{
            value: 'text',
            label: 'TEXT'
        }, {
            value: 'json',
            label: 'JSON'
        }, {
            value: 'xml',
            label: 'XML'
        }, {
            value: 'yaml',
            label: 'YAML'
        }, {
            value: 'text/html',
            label: 'HTML'
        }, {
            value: 'properties',
            label: 'properties'
        }];

        const groupInput = <FormItem label="Group:" required {...formItemLayout}>
                        <Combobox style={{ width: '100%' }} size="large" hasArrow dataSource={this.state.groups} placeholder={aliwareIntl.get("com.alibaba.newDiamond.page.newconfig.group_placeholder")} defaultValue={this.group} {...init('group', {
                rules: [{
                    required: true,
                    message: aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.the_more_advanced')
                }, {
                    max: 127,
                    message: aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.group_is_not_empty')
                }, { validator: this.validateChart.bind(this) }]
            })} onChange={this.setGroup.bind(this)} onChange={this.setGroup.bind(this)} hasClear language={aliwareIntl.currentLanguageCode}>
                        </Combobox>
                    </FormItem>;

        return (
            <div style={{ padding: 10 }}>
                <Loading shape="flower" tip="Loading..." style={{ width: '100%', position: 'relative' }} visible={this.state.loading} color="#333">
                    <h1>{aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.new_listing')}</h1>
                    <Form field={this.field}>
                        <FormItem label="Data ID:" required {...formItemLayout}>
                            <Input {...init('dataId', {
                                rules: [{
                                    required: true,
                                    message: aliwareIntl.get('com.alibaba.newDiamond.page.newconfig')
                                }, {
                                    max: 255,
                                    message: aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.dataId_is_not_empty')
                                }, { validator: this.validateChart.bind(this) }]
                            })} addonTextBefore={this.state.addonBefore ? <div style={{ minWidth: 100, color: "#373D41" }}>{this.state.addonBefore}</div> : null} />

                        </FormItem>
                        {this.inEdas ? groupInput : ""}
                        <FormItem label=" " {...formItemLayout} style={{ display: this.state.showGroupWarning ? "block" : "none" }}>
                            <Notice type={'warning'} size={'medium'} animation={false}>{aliwareIntl.get('newDiamond.page.newconfig.Note_You_are_to_be_a_custom_packet_the_new_configuration,_make_sure_that_the_client_use_the_Pandora_version_higher_than_3._4._0,_otherwise_it_may_read_less_than_the_configuration.0')}</Notice>
                        </FormItem>
                    <FormItem label="" {...formItemLayout}>
                        <div>
                            <a style={{ fontSize: '12px' }} href="javascript:;" onClick={this.toggleMore.bind(this)}>{this.state.showmore ? aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.Data_ID_length') : aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.collapse')}</a>
                        </div>
                    </FormItem>

                    <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
                        {this.inEdas ? "" : groupInput}

                        <FormItem label={aliwareIntl.get('newDiamond.page.newconfig.Tags')} {...formItemLayout}>
                            <Combobox size="medium" hasArrow style={{ width: '100%' }} autoWidth={true} multiple={true} tags={true} filterLocal={true} placeholder={aliwareIntl.get('newDiamond.page.configurationManagement.Please_enter_tag')} dataSource={this.state.tagLst} value={this.state.config_tags} onChange={this.setConfigTags.bind(this)} onChange={this.onInputUpdate.bind(this)} hasClear language={aliwareIntl.currentLanguageCode}>
                            </Combobox>
                        </FormItem>

                    </div>

                    <FormItem label={aliwareIntl.get('newDiamond.page.newconfig.Description')} {...formItemLayout}>
                        <Input htmlType="text" multiple rows={3} {...init('desc')} />
                    </FormItem>

                    <FormItem label={aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.the_home_application')} {...formItemLayout} required>
                        <div style={{ height: 30, lineHeight: '33px' }}>
                            <CheckboxGroup disabled={true} value={this.state.envvalues} onChange={this.changeEnv.bind(this)} dataSource={this.state.envlist} />
                        </div>
                    </FormItem>
                    <FormItem label={aliwareIntl.get('newDiamond.page.newconfig.data_encryption3')} {...formItemLayout}>
                            <Switch checkedChildren={<Icon type="select" style={{ marginLeft: -3 }} />} unCheckedChildren={<Icon type="close" size="small" />} size={"small"} onChange={this.switchEncrypt.bind(this)} checked={this.state.encrypt} />
                            <Balloon trigger={<Icon type="help" size={'small'} style={{ color: '#1DC11D', marginLeft: 5, verticalAlign: 'top', height: 26, lineHeight: "26px" }} />} align="t" triggerType="hover">
                            <a href={window._getLink && window._getLink("kmsUse")} target="_blank">{aliwareIntl.get('newDiamond.page.newconfig.data_encryption3')}</a>
                        </Balloon>
                    </FormItem>
                    <FormItem label={aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.the_target_environment')} {...formItemLayout}>
                        <RadioGroup dataSource={list} value={this.state.configType} onChange={this.newChangeConfig.bind(this)} />
                    </FormItem>
                    {}
                    <FormItem label={<span style={{ marginRight: 5 }}>{aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.configuration_format')}<Balloon trigger={<Icon type="help" size={'small'} style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle', marginTop: 2 }} />} align="t" style={{ marginRight: 5 }} triggerType="hover">
                        <p>{aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.configure_contents_of')}</p>
                        <p>{aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.full_screen')}</p>
                    </Balloon>:</span>} required {...formItemLayout}>
                        <div id="container" style={{ width: '100%', height: 300 }}></div>
                    </FormItem>

                    <FormItem {...formItemLayout} label="">

                        <div style={{ textAlign: 'right' }}>
                            <Button type="primary" style={{ marginRight: 10 }} onClick={this.publishConfig.bind(this)}>{aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.esc_exit')}</Button>

                            <Button type="light" onClick={this.goList.bind(this)}>{aliwareIntl.get('com.alibaba.newDiamond.page.newconfig.release')}</Button>
                        </div>
                    </FormItem>
                    </Form>
                <SuccessDialog ref="success" />
                </Loading>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default EdasNewconfig;