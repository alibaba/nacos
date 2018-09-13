import React from 'react';
import $ from 'jquery';
import DiffEditorDialog from '../components/DiffEditorDialog';
import SuccessDialog from '../components/SuccessDialog';
import { Balloon, Button, Dialog, Field, Form, Icon, Input, Loading, Radio, Select, Tab } from '@alifd/next';
const TabPane = Tab.Item;
const FormItem = Form.Item;
const { Group: RadioGroup } = Radio;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class Configeditor extends React.Component {
    constructor(props) {
        super(props);
        this.edasAppName = window.getParams('edasAppName') || '';
        this.edasAppId = window.getParams('edasAppId') || '';
        this.inApp = this.edasAppName;
        this.field = new Field(this);
        this.dataId = window.getParams('dataId') || 'yanlin';
        this.group = window.getParams('group') || 'DEFAULT_GROUP';
        this.tenant = window.getParams('namespace') | '';
        this.state = {
            configType: 'text',
            codeValue: ``,
            envname: 'center',
            targetEnvName: '',
            envlist: [],
            envvalues: [],
            loading: false,
            showmore: false,
            activeKey: 'normal',
            hasbeta: false,
            ips: '',
            checkedBeta: false,
            tagLst: [],
            config_tags: [],
            switchEncrypt: false,
            tag: [{ title: window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.official'), key: 'normal' }]
        };
        this.codeValue = '';
        this.mode = 'text';
        this.ips = '';
        this.valueMap = {}; //存储不同版本的数据
        this.searchDataId = window.getParams('searchDataId') || '';
        this.searchGroup = window.getParams('searchGroup') || '';
    }
    componentDidMount() {
        if (this.dataId.startsWith("cipher-")) {
            this.setState({
                switchEncrypt: true
            });
        }
        this.betaips = document.getElementById('betaips');
        this.getDataDetail();
        this.chontenttab = document.getElementById('chontenttab'); //diff标签
        this.getTags();
        this.getTagLst();
    }

    initMoacoEditor(language, value) {
        if (!window.monaco) {
            window.importEditor(() => {
                this.monacoEditor = window.monaco.editor.create(document.getElementById('container'), {
                    value: value,
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
            });
        } else {
            this.monacoEditor = window.monaco.editor.create(document.getElementById('container'), {
                value: value,
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
    }

    toggleMore() {
        this.setState({
            showmore: !this.state.showmore
        });
    }
    navTo(url) {

        this.serverId = window.getParams('serverId') || '';
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数
        window.hashHistory.push(`${url}?serverId=${this.serverId || ''}&dataId=${this.dataId}&group=${this.group}&namespace=${this.tenant}`);
    }
    getTagLst() {
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数
        this.serverId = window.getParams('serverId') || '';
        window.request({

            type: 'get',
            beforeSend: function () { },
            url: `/diamond-ops/configList/tags/serverId/${this.serverId}/tenant/${this.tenant}`,
            success: res => {
                console.log("res:", res);
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
    getBeta() {
        let self = this;
        this.tenant = window.getParams('namespace') || '';
        this.serverId = window.getParams('serverId') || 'center';
        let url = `/diamond-ops/configList/edit/beta/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}/tenant/${this.tenant}?id=`;
        if (this.tenant === 'global' || !this.tenant) {
            url = `/diamond-ops/configList/edit/beta/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}?id=`;
        }
        window.request({
            url: url,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (result) {

                if (result.code === 200) {

                    self.valueMap['beta'] = result.data;
                } else { }
            },
            complete: function () {
                self.closeLoading();
            }
        });
    }
    getConfigList() {
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

                if (result.code === 200) { } else { }
            },
            complete: function () {
                self.closeLoading();
            }
        });
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
    getDataDetail() {
        let self = this;
        this.tenant = window.getParams('namespace') || '';
        this.serverId = window.getParams('serverId') || 'center';
        let url = `/nacos/v1/cs/configs?show=all&dataId=${this.dataId}&group=${this.group}`;
        //        let url = `/diamond-ops/configList/detail/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}/tenant/${this.tenant}?id=`;
        //        if (this.tenant === 'global' || !this.tenant) {
        //            url = `/diamond-ops/configList/detail/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}?id=`;
        //        }
        window.request({
            url: url,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (result) {

                if (result != null) {
                    let data = result;
                    self.valueMap['normal'] = data;
                    self.field.setValue('dataId', data.dataId);
                    //self.field.setValue('content', data.content);
                    self.field.setValue('appName', self.inApp ? self.edasAppName : data.appName);
                    //self.field.setValue('envs', self.serverId);
                    self.field.setValue('group', data.group);

                    //self.field.setValue('type', data.type);
                    self.field.setValue('desc', data.desc);
                    //self.field.setValue('md5', data.md5);
                    self.codeValue = data.content || '';
                    let type = data.type || 'text';
                    self.setState({ //设置radio 高亮
                        configType: type
                    });
                    self.initMoacoEditor(type, self.codeValue);

                    //self.createCodeMirror('text', self.codeValue);
                    //self.codeValue = self.commoneditor.doc.getValue();
                    debugger;
                    if (data.configTags != null) {
                        let tagArr = data.configTags.split(",");
                        self.setConfigTags(tagArr);
                    }

                    let envvalues = [];
                    //                    for (let i = 0; i < data.envs.length; i++) {
                    //                        let obj = data.envs[i];
                    //                        envlist.push({
                    //                            label: obj.name,
                    //                            value: obj.serverId
                    //                        });
                    //                        envvalues.push(obj.serverId);
                    //                    }

                    let env = {};
                    //                    let env = data.envs[0] || {};
                    //                    self.setState({
                    //                        envlist: envlist,
                    //                        envname: env.name,
                    //                        envvalues: envvalues
                    //                    });
                    self.serverId = env.serverId;
                    self.targetEnvs = envvalues;
                } else {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        title: window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.wrong'),
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

        let tenant = window.getParams('namespace');
        window.hashHistory.push(`/configurationManagement?serverId=${this.serverId}&group=${this.searchGroup}&dataId=${this.searchDataId}&namespace=${tenant}`);
    }

    createCodeMirror(mode, value) {
        let commontarget = this.refs["commoneditor"];
        commontarget.innerHTML = '';
        this.commoneditor = window.CodeMirror(commontarget, {
            value: value,
            mode: mode,
            lineNumbers: true,
            theme: 'xq-light',
            lint: true,
            gutters: ["CodeMirror-lint-markers"],
            extraKeys: {
                "F1": function (cm) {
                    cm.setOption("fullScreen", !cm.getOption("fullScreen"));
                },
                "Esc": function (cm) {
                    if (cm.getOption("fullScreen")) cm.setOption("fullScreen", false);
                }
            }
        });
        this.commoneditor.on('change', this.codemirrorValueChanged.bind(this));
    }
    codemirrorValueChanged(doc) {
        if (this.diffeditor) {
            this.diffeditor.edit.doc.setValue(doc.getValue());
        }
    }
    createDiffCodeMirror(leftCode, rightCode) {
        let target = this.refs["diffeditor"];
        target.innerHTML = '';

        this.diffeditor = window.CodeMirror.MergeView(target, {
            value: leftCode || '',
            origLeft: null,
            orig: rightCode || '',
            lineNumbers: true,
            mode: this.mode,
            theme: 'xq-light',
            highlightDifferences: true,
            connect: 'align',
            collapseIdentical: false
        });
    }
    changeConfig(value) {
        if (value === 0) {
            this.createCodeMirror('text', this.codeValue);
            this.mode = 'text';
        }
        if (value === 1) {
            this.createCodeMirror('application/json', this.codeValue);
            this.mode = 'application/json';
        }
        if (value === 2) {
            this.createCodeMirror('xml', this.codeValue);
            this.mode = 'xml';
        }
        this.setState({
            configType: value
        });
    }
    setCodeValue(value) {

        this.setState({
            codeValue: value
        });
    }
    toggleDiff(checked) {
        if (checked) {
            this.chontenttab.style.display = 'block';

            let nowvalue = this.commoneditor.doc.getValue();
            if (!this.diffeditor) {
                this.createDiffCodeMirror(nowvalue, this.codeValue);
            }
        } else {
            this.chontenttab.style.display = 'none';
            //this.diffeditor = null;
            //let target = this.refs["diffeditor"];
            //target.innerHTML = '';
        }
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
            //     //content = content.replace("↵", "\n\r");
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
            this.tenant = window.getParams('namespace') || '';
            this.serverId = window.getParams('serverId') || 'center';

            let payload = {
                dataId: this.field.getValue('dataId'),
                appName: this.inApp ? this.edasAppId : this.field.getValue('appName'),
                group: this.field.getValue('group'),
                desc: this.field.getValue('desc'),
                config_tags: this.state.config_tags.join(),
                type: this.state.configType,
                content: content,
                tenant: this.tenant

            };
            let url = `/nacos/v1/cs/configs`;
            window.request({
                type: 'post',
                contentType: 'application/x-www-form-urlencoded',
                url: url,
                data: payload,
                success: function (res) {
                    let _payload = {};
                    _payload.maintitle = window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.toedittitle');
                    _payload.title = <div>{window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.toedit')}</div>;
                    _payload.content = '';
                    _payload.dataId = payload.dataId;
                    _payload.group = payload.group;

                    if (res != null) {
                        _payload.isok = true;
                        let activeKey = self.state.activeKey.split('-')[0];
                        if (activeKey === 'normal' && self.hasips === true) {
                            //如果是在normal面板选择了beta发布
                            let sufex = new Date().getTime();
                            self.setState({
                                tag: [{ title: window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.official'), key: `normal-${sufex}` }, { title: 'BETA', key: `beta-${sufex}` }], hasbeta: true,
                                activeKey: `beta-${sufex}`
                            });
                            payload.betaIps = payload.betaIps || payload.ips;
                            self.valueMap['beta'] = payload; //赋值beta
                            self.changeTab(`beta-${sufex}`);
                        }
                        if (activeKey === 'normal' && self.hasips === false) {
                            //如果是在normal面板选择了发布
                            self.valueMap['normal'] = payload; //赋值正式
                        }
                        if (activeKey === 'beta' && self.hasips === false) {
                            //如果是在beta面板选择了正式发布需要停止beta                      
                            self.valueMap['normal'] = payload; //赋值正式
                            self.stopPublishConfig();
                        }
                        if (activeKey === 'beta' && self.hasips === true) {
                            //如果是在beta面板继续beta发布                              
                            self.valueMap['beta'] = payload; //赋值beta
                        }
                    } else {
                        _payload.isok = false;
                        _payload.message = res.message;
                    }
                    self.refs['success'].openDialog(_payload
                    );
                },
                error: function () { }
            });
        });
    }
    validateChart(rule, value, callback) {
        const chartReg = /[@#\$%\^&\*]+/g;

        if (chartReg.test(value)) {
            callback(window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.vdchart'));
        } else {
            callback();
        }
    }
    stopPublishConfig() {
        this.field.validate((errors, values) => {
            if (errors) {
                return;
            }
            let self = this;
            this.tenant = window.getParams('namespace') || '';
            let payload = {
                dataId: this.field.getValue('dataId'),
                appName: this.inApp ? this.edasAppId : this.field.getValue('appName'),
                group: this.field.getValue('group'),
                content: this.field.getValue('content'),
                targetEnvs: this.targetEnvs,
                tenant: this.tenant
            };

            let url = `/diamond-ops/configList/stopBeta/serverId/${this.serverId}/dataId/${payload.dataId}/group/${payload.group}?id=`;
            if (this.tenant) {
                //添加tenant
                url = `/diamond-ops/configList/stopBeta/serverId/${this.serverId}/dataId/${payload.dataId}/group/${payload.group}/tenant/${this.tenant}?id=`;
            }
            window.request({
                type: 'put',
                contentType: 'application/json',
                url: url,
                data: JSON.stringify(payload),
                success: function (res) {
                    let _payload = {};

                    _payload.maintitle = window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.stop_beta');
                    _payload.title = <div>{window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.stop_beta')}<a onClick={self.navTo.bind(self, '/pushTrajectory')}>{window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.look')}</a></div>;
                    _payload.content = '';
                    _payload.dataId = payload.dataId;
                    _payload.group = payload.group;
                    if (res.code === 200) {
                        _payload.isok = true;
                    } else {
                        _payload.isok = false;
                        _payload.title = window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.stop_beta');
                        _payload.message = res.message;
                    }
                    setTimeout(() => {
                        let sufex = new Date().getTime();
                        let tag = [{ title: window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.official'), key: `normal-${sufex}` }];
                        self.setState({
                            tag: tag,
                            checkedBeta: false,
                            activeKey: 'normal' + sufex,
                            hasbeta: false

                        });
                        self.changeTab(`normal-${sufex}`);
                    });

                    self.refs['success'].openDialog(_payload);
                },
                error: function () { }
            });
        });
    }
    changeEnv(values) {
        this.targetEnvs = values;
        this.setState({
            envvalues: values
        });
    }
    changeBeta(selected) {
        if (selected) {
            this.betaips.style.display = 'block';
        } else {
            this.betaips.style.display = 'none';
        }
        this.setState({
            checkedBeta: selected
        });
    }
    getIps(value) {
        this.ips = value;
        this.setState({
            ips: value
        });
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
    openDiff(hasips) {
        this.hasips = hasips; //是否包含ips
        let leftvalue = this.monacoEditor.getValue(); //this.commoneditor.doc.getValue();
        let rightvalue = this.codeValue;
        leftvalue = leftvalue.replace(/\r\n/g, "\n").replace(/\n/g, "\r\n");
        rightvalue = rightvalue.replace(/\r\n/g, "\n").replace(/\n/g, "\r\n");
        //let rightvalue = this.diffeditor.doc.getValue();
        //console.log(this.commoneditor, leftvalue==rightvalue)
        this.refs['diffeditor'].openDialog(leftvalue, rightvalue);
    }
    changeTab(value) {

        let self = this;
        let key = value.split('-')[0];

        let data = this.valueMap[key];
        this.setState({
            activeKey: value
        });
        self.field.setValue('dataId', data.dataId);

        self.field.setValue('appName', self.inApp ? self.edasAppName : data.appName);
        //self.field.setValue('envs', self.serverId);
        self.field.setValue('group', data.group);
        //self.field.setValue('md5', data.md5);
        self.codeValue = data.content || '';
        self.createCodeMirror('text', self.codeValue);
        if (data.betaIps) {
            self.getIps(data.betaIps);
            self.changeBeta(true);
        } else {
            self.getIps('');
            self.changeBeta(false);
        }
    }
    newChangeConfig(value) {
        this.setState({
            configType: value
        });
        this.changeModel(value);
    }
    changeModel(type, value) {
        if (!this.monacoEditor) {
            $('#container').empty();
            this.initMoacoEditor(type, value);
            return;
        }
        let oldModel = this.monacoEditor.getModel();
        let oldValue = this.monacoEditor.getValue();
        let newModel = window.monaco.editor.createModel(oldValue, type);
        this.monacoEditor.setModel(newModel);
        if (oldModel) {
            oldModel.dispose();
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
            value: 'html',
            label: 'HTML'
        }, {
            value: 'properties',
            label: 'Properties'
        }];
        let activeKey = this.state.activeKey.split('-')[0];

        return (
            <div style={{ padding: 10 }}>
                <Loading shape="flower" style={{ position: 'relative', width: '100%' }} visible={this.state.loading} tip="Loading..." color="#333">
                    <h1 style={{ overflow: 'hidden', height: 50, width: '100%' }}>
                        <div>{window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.toedit')}</div>

                    </h1>
                    {this.state.hasbeta ? <div style={{ display: 'inline-block', height: 40, width: '80%', overflow: 'hidden' }}>

                        <Tab shape={'wrapped'} onChange={this.changeTab.bind(this)} lazyLoad={false} activeKey={this.state.activeKey}>
                            {this.state.tag.map(tab => <TabPane title={tab.title} key={tab.key}></TabPane>)}
                        </Tab>

                    </div> : ''}

                    <Form field={this.field}>
                        <FormItem label="Data ID:" {...formItemLayout}>
                            <Input disabled={true} {...init('dataId', {
                                rules: [{
                                    required: true,
                                    message: window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.recipient_from')
                                }, { validator: this.validateChart.bind(this) }]
                            })} />

                        </FormItem>
                        <FormItem label="" {...formItemLayout}>
                            <div>
                                <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>{this.state.showmore ? window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.more_advanced_options') : window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.group_is_not_empty')}</a>
                            </div>
                        </FormItem>
                        <div style={{ height: this.state.showmore ? 'auto' : '0', overflow: 'hidden' }}>
                            <FormItem label="Group:" {...formItemLayout}>
                                <Input disabled={true} {...init('group', {
                                    rules: [{
                                        required: true,
                                        message: window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.Home_application:')
                                    }, { validator: this.validateChart.bind(this) }]
                                })} />
                            </FormItem>
                            <FormItem label={window.aliwareIntl.get('nacos.page.configeditor.Tags')} {...formItemLayout}>
                                <Select size="medium" hasArrow style={{ width: '100%' }} autoWidth={true} multiple={true} mode="tag" filterLocal={true} placeholder={window.aliwareIntl.get('nacos.page.configurationManagement.Please_enter_tag')} dataSource={this.state.tagLst} value={this.state.config_tags} onChange={this.setConfigTags.bind(this)} hasClear language={window.aliwareIntl.currentLanguageCode}>
                                </Select>
                            </FormItem>

                            <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.the_target_environment:')} {...formItemLayout}>
                                <Input {...init('appName')} readOnly={this.inApp} />
                            </FormItem>
                        </div>

                        <FormItem label={window.aliwareIntl.get('nacos.page.configeditor.Description')} {...formItemLayout}>
                            <Input htmlType="text" multiple rows={3} {...init('desc')} />
                        </FormItem>
                        <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.configure_contents_of')} {...formItemLayout}>
                            <RadioGroup dataSource={list} value={this.state.configType} onChange={this.newChangeConfig.bind(this)} />
                        </FormItem>
                        <FormItem label={<span style={{ marginRight: 5 }}>{window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.configcontent')}<Balloon trigger={<Icon type="help" size={'small'} style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle', marginTop: 2 }} />} align="t" style={{ marginRight: 5 }} triggerType="hover">
                            <p>{window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.Esc_exit')}</p>
                            <p>{window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.release_beta')}</p>
                        </Balloon>:</span>} {...formItemLayout}>

                            <div style={{ clear: 'both', height: 300 }} id="container"></div>
                        </FormItem>
                        {}
                        <FormItem {...formItemLayout} label="">
                            <div style={{ textAlign: 'right' }}>
                                {activeKey === 'normal' ? '' : <Button type="primary" style={{ marginRight: 10 }} onClick={this.stopPublishConfig.bind(this, false)}>{window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.stop_beta')}</Button>}
                                {activeKey === 'beta' ? <Button style={{ marginRight: 10 }} type="primary" onClick={this.openDiff.bind(this, true)}>{window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.release')}</Button> : ''}
                                {activeKey === 'normal' ? <Button type="primary" disabled={this.state.hasbeta} style={{ marginRight: 10 }} onClick={this.openDiff.bind(this, this.state.checkedBeta)}>{this.state.checkedBeta ? window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.release') : window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.return')}</Button> : <Button type="primary" style={{ marginRight: 10 }} onClick={this.openDiff.bind(this, false)}>{window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.return')}</Button>}

                                <Button type="light" onClick={this.goList.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.page.configeditor.')}</Button>
                            </div>
                        </FormItem>
                    </Form>
                    <DiffEditorDialog ref="diffeditor" publishConfig={this.publishConfig.bind(this)} />
                    <SuccessDialog ref="success" />
                </Loading>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default Configeditor;