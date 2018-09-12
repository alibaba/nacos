import React from 'react';
import $ from 'jquery';
import SuccessDialog from '../components/SuccessDialog';
import { Balloon, Button, Dialog, Field, Form, Icon, Input, Loading, Message, Select, Radio } from '@alifd/next';
const FormItem = Form.Item;
const { Group: RadioGroup } = Radio;
const { AutoComplete: Combobox } = Select;


/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class Newconfig extends React.Component {
    constructor(props) {
        super(props);
        this.field = new Field(this);
        this.edasAppName = window.getParams('edasAppName') || '';
        this.edasAppId = window.getParams('edasAppId') || '';
        this.inApp = this.edasAppName;
        this.field.setValue('appName', this.inApp ? this.edasAppName : '');
        this.inEdas = window.globalConfig.isParentEdas();
        this.dataId = window.getParams('dataId') || '';
        this.group = window.getParams('group') || 'DEFAULT_GROUP';
        this.searchDataId = window.getParams('searchDataId') || '';
        this.searchGroup = window.getParams('searchGroup') || '';
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
        this.tenant = window.getParams('namespace') || '';
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
            this.monacoEditor = window.monaco.editor.create(document.getElementById('container'), {
                model: null
            });
            return;
        }
        let oldModel = this.monacoEditor.getModel();
        let oldValue = this.monacoEditor.getValue();
        let newModel = window.monaco.editor.createModel(oldValue, type);
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
        this.monacoEditor = window.monaco.editor.create(document.getElementById('container'), {
            value: this.codeValue,
            language: this.state.configType,
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
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数
        this.serverId = window.getParams('serverId') || '';
        window.request({
            type: 'get',
            beforeSend: function () { },
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
                        if (groups.length === 0) {
                            this.setState({
                                showGroupWarning: this.state.groupNames.indexOf(this.group) < 0
                            });
                        } else {
                            this.setGroup('');
                        }
                    } else {
                        this.setGroup(data[0] || 'DEFAULT_GROUP');
                    }
                }
            }
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
                        let tag = [{ title: window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.official'), key: 'normal' }, { title: 'BETA', key: 'beta' }];
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
    getTagLst() {
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数
        this.serverId = window.getParams('serverId') || '';
        window.request({

            type: 'get',
            beforeSend: function () { },
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
                showGroupWarning: this.group !== '' && this.state.groupNames.indexOf(value) < 0
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
                if (v.value === value) {
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
        this.tenant = window.getParams('namespace') || '';
        this.serverId = window.getParams('serverId') || '';
        //console.log(`/configurationManagement?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}`)
        window.hashHistory.push(`/configurationManagement?serverId=${this.serverId}&group=${this.searchGroup}&dataId=${this.searchDataId}&namespace=${this.tenant}`);
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
            if (this.monacoEditor) {
                content = this.monacoEditor.getValue();
            } else {
                content = this.codeValue;
            }
            if (!content) {
                return;
            }
            this.tenant = window.getParams('namespace') || '';
            let payload = {
                dataId: self.state.addonBefore + this.field.getValue('dataId'),
                group: this.field.getValue('group'),
                content: content,
                desc: this.field.getValue('desc'),
                config_tags: this.state.config_tags.join(),
                type: this.state.configType,
                appName: this.inApp ? this.edasAppId : this.field.getValue('appName'),
                tenant: this.tenant
            };
            this.serverId = window.getParams('serverId') || 'center';
            let url = `/nacos/v1/cs/configs`;
            window.request({
                type: 'post',
                contentType: 'application/x-www-form-urlencoded',
                url: url,
                data: payload,
                beforeSend: () => {
                    this.openLoading();
                },
                success: function (res) {
                    let _payload = {};
                    _payload.maintitle = window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.new_listing_main');
                    _payload.title = window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.new_listing');
                    _payload.content = '';
                    _payload.dataId = payload.dataId;
                    _payload.group = payload.group;
                    if (res === true) {
                        self.group = payload.group;
                        self.dataId = payload.dataId;
                        window.setParams({ group: payload.group, dataId: payload.dataId }); //设置参数
                        _payload.isok = true;
                    } else {
                        _payload.isok = false;
                        _payload.message = res.message;
                    }
                    self.refs['success'].openDialog(_payload);
                },
                complete: function () {
                    self.closeLoading();
                },
                error: function (res) {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        content: window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.publish_failed')
                    });
                    self.closeLoading();
                }

            });
        });
    }
    getGroupInfo() {
        let self = this;
        this.serverId = window.getParams('serverId') || 'center';
        window.request({
            url: `/diamond-ops/env/serverId/${this.serverId}/groupInfo`,
            success: function (res) {

                if (res.code === 0) {

                    let data = res.data;
                    let envvalues = [];
                    let envlist = [];
                    let serverId = window.getParams('serverId') || 'daily';
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
            window.request({
                type: 'get',
                beforeSend: function () { },
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
                        let titleTmp = window.aliwareIntl.get('nacos.page.newconfig.The_opening_of_the_data_encryption-related_services0');
                        let contentTmp1 = "";
                        let contentTmp2 = "";
                        if (data.kmsServiceStatus !== 200) {
                            contentTmp1 = <div>{data.kmsMsg}<a href={window._getLink && window._getLink("kmsOpen")} target={"_blank"}>{window.aliwareIntl.get('nacos.page.newconfig._to_go_to_the_opening_of1')}</a></div>;
                        }
                        if (data.sts2KmsRightStatus !== 200) {
                            contentTmp2 = <div>{data.sts2KmsMsg}<a style={{ marginLeft: 10 }} href={window._getLink && window._getLink("kmsAuthorize")} target={"_blank"}>{window.aliwareIntl.get('nacos.page.newconfig.to_the_authorization_of2')}</a></div>;
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
                            title: window.aliwareIntl.get('nacos.page.newconfig.The_opening_of_the_data_encryption-related_services0'),
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
        const chartReg = /[@#\$%\^&\*]+/g;

        if (chartReg.test(value)) {
            callback(window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.do_not_ente'));
        } else {
            callback();
        }
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
            label: 'Properties'
        }];

        const groupInput = <FormItem label={"Group:"} required {...formItemLayout}>
            <Combobox style={{ width: '100%' }} size={"large"} hasArrow dataSource={this.state.groups} placeholder={window.aliwareIntl.get("com.alibaba.nacos.page.newconfig.group_placeholder")} defaultValue={this.group} {...init('group', {
                rules: [{
                    required: true,
                    message: window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.the_more_advanced')
                }, {
                    max: 127,
                    message: window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.group_is_not_empty')
                }, { validator: this.validateChart.bind(this) }]
            })} onChange={this.setGroup.bind(this)} hasClear language={window.aliwareIntl.currentLanguageCode}>
            </Combobox>
        </FormItem>;

        return (
            <div style={{ padding: 10 }}>
                <Loading shape={"flower"} tip={"Loading..."} style={{ width: '100%', position: 'relative' }} visible={this.state.loading} color={"#333"}>
                    <h1>{window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.new_listing')}</h1>
                    <Form field={this.field}>
                        <FormItem label={"Data ID:"} required {...formItemLayout}>
                            <Input {...init('dataId', {
                                rules: [{
                                    required: true,
                                    message: window.aliwareIntl.get('com.alibaba.nacos.page.newconfig')
                                }, {
                                    max: 255,
                                    message: window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.dataId_is_not_empty')
                                }, { validator: this.validateChart.bind(this) }]
                            })} addonTextBefore={this.state.addonBefore ? <div style={{ minWidth: 100, color: "#373D41" }}>{this.state.addonBefore}</div> : null} />

                        </FormItem>
                        {this.inEdas ? groupInput : ""}
                        <FormItem label={" "} {...formItemLayout} style={{ display: this.state.showGroupWarning ? "block" : "none" }}>
                            <Message type={'warning'} size={'medium'} animation={false}>{window.aliwareIntl.get('nacos.page.newconfig.Note_You_are_to_be_a_custom_packet_the_new_configuration,_make_sure_that_the_client_use_the_Pandora_version_higher_than_3._4._0,_otherwise_it_may_read_less_than_the_configuration.0')}</Message>
                        </FormItem>
                        <FormItem label={""} {...formItemLayout}>
                            <div>
                                <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>{this.state.showmore ? window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.Data_ID_length') : window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.collapse')}</a>
                            </div>
                        </FormItem>

                        <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
                            {this.inEdas ? "" : groupInput}

                            <FormItem label={window.aliwareIntl.get('nacos.page.newconfig.Tags')} {...formItemLayout}>
                                <Select size={"medium"} hasArrow style={{ width: '100%', height: '100%!important' }} autoWidth={true} multiple={true} mode="tag" filterLocal={true} placeholder={window.aliwareIntl.get('nacos.page.configurationManagement.Please_enter_tag')} dataSource={this.state.tagLst} value={this.state.config_tags} onChange={this.setConfigTags.bind(this)} hasClear language={window.aliwareIntl.currentLanguageCode}>
                                </Select>
                            </FormItem>

                            <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.Group_ID_cannot_be_longer')} {...formItemLayout}>
                                <Input {...init('appName')} readOnly={this.inApp} />

                            </FormItem>
                        </div>

                        <FormItem label={window.aliwareIntl.get('nacos.page.newconfig.Description')} {...formItemLayout}>
                            <Input htmlType={"text"} multiple rows={3} {...init('desc')} />
                        </FormItem>

                        <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.the_target_environment')} {...formItemLayout}>
                            <RadioGroup dataSource={list} value={this.state.configType} onChange={this.newChangeConfig.bind(this)} />
                        </FormItem>
                        <FormItem label={<span>{window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.configuration_format')}<Balloon trigger={<Icon type={"help"} size={'small'} style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle', marginTop: 2 }} />} align={"t"} style={{ marginRight: 5 }} triggerType={"hover"}>
                            <p>{window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.configure_contents_of')}</p>
                            <p>{window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.full_screen')}</p>
                        </Balloon>:</span>} required {...formItemLayout}>
                            <div id={"container"} style={{ width: '100%', height: 300 }}></div>
                        </FormItem>

                        <FormItem {...formItemLayout} label={""}>

                            <div style={{ textAlign: 'right' }}>
                                <Button type={"primary"} style={{ marginRight: 10 }} onClick={this.publishConfig.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.esc_exit')}</Button>

                                <Button type={"light"} onClick={this.goList.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.page.newconfig.release')}</Button>
                            </div>
                        </FormItem>
                    </Form>
                    <SuccessDialog ref={"success"} />
                </Loading>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default Newconfig;