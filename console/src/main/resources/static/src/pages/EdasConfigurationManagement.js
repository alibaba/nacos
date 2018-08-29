import React from 'react'; 
import { Balloon, Button, Checkbox, Collapse, Dialog, Field, Form, Icon, Input, Loading, Pagination, Select, Table } from '@alifd/next';
const Accordion = Collapse; 
const FormItem = Form.Item; 
const { Panel } = Collapse; 
const { AutoComplete: Combobox } = Select;
import BatchHandle from '../components/BatchHandle' ;
import RegionGroup from '../components/RegionGroup' ;
import ShowCodeing from '../components/ShowCodeing' ;
import DeleteDialog from '../components/DeleteDialog' ;
import CloneDialog from '../components/CloneDialog' ;
import ImportDialog from '../components/ImportDialog' ;
import ExportDialog from '../components/ExportDialog' ;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
/**
 * 配置管理
 */
class EdasConfigurationManagement extends React.Component {
    constructor(props) {
        super(props);
        this.field = new Field(this);
        this.appName = getParams('edasAppId') || '';
        this.preAppName = this.appName;
        this.group = getParams('group') || '';
        this.preGroup = this.group;
        this.dataId = getParams('dataId') || '';
        this.preDataId = this.dataId;
        this.serverId = getParams('serverId') || 'center';
        this.edasAppId = getParams('edasAppId') || '';
        this.edasAppName = getParams('edasAppName') || '';
        this.inApp = this.edasAppId;
        this.state = {
            value: "",
            visible: false,
            total: 0,
            pageSize: 10,
            currentPage: 1,
            dataSource: [],
            fieldValue: [],
            showAppName: false,
            showgroup: false,
            dataId: this.dataId,
            group: this.group,
            appName: this.appName,
            config_tags: [],
            tagLst: [],
            selectValue: [],
            loading: false,
            groupList: [],
            groups: [],
            tenant: true,
            nownamespace_id: window.nownamespace || '',
            nownamespace_name: window.namespaceShowName || '',
            selectedRecord: [],
            selectedKeys: []
        };
        let obj = {
            dataId: this.dataId || '',
            group: this.preGroup || '',
            appName: this.appName || ''
        };
        setParams(obj);
        this.batchHandle = null;
        this.toggleShowQuestionnaire = this.toggleShowQuestionnaire.bind(this);
    }

    componentDidMount() {
        // this.getData()
        
        this.getGroup();
        if (window._getLink && window._getLink("isCn") === "true" && !this.checkQuestionnaire()) {
            if(window.location.host == 'acm.console.aliyun.com') {
                Dialog.alert({
                    title: '问卷调查',
                    style: {
                        width: '60%'
                    },
                    content: <div>
                        <div style={{ fontSize: '15px', lineHeight: '22px' }}> 答 ACM 前端监控调查问卷，限时领取阿里云代金券
        详情猛戳：<a href="https://survey.aliyun.com/survey/k0BjJ2ARC" target="_blank">问卷调查</a>
                    </div>
                    <div style={{ fontSize: '15px' }}>
                        不再显示：<Checkbox onChange={this.toggleShowQuestionnaire} />
                        </div>
                    </div>,
                    language: aliwareIntl.currentLanguageCode
                });
            }
        }
    }
    toggleShowQuestionnaire(value) {
        if (value) {
            localStorage.setItem('acm_questionnaire', 1);
        } else {
            localStorage.removeItem('acm_questionnaire');
        }
    }
    checkQuestionnaire() {

        let acm_questionnaire = localStorage.getItem('acm_questionnaire');
        if (acm_questionnaire) {
            return true;
        } else {
            return false;
        }
    }
    getGroupsList() {
        let self = this;
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        this.serverId = getParams('serverId') || '';
        request({

            type: 'get',
            beforeSend: function () {},
            url: `/diamond-ops/configList/groups_by_tenant/serverId/${this.serverId}/tenant/${this.tenant}`,
            success: res => {
                console.log("res:", res);
                if (res.code === 200) {
                    let data = res.data;
                    let groups = [];
                    for (let i = 0; i < data.length; i++) {
                        groups.push({
                            label: data[i],
                            value: data[i]
                        });
                    }
                    this.setState({
                        groups: groups
                    });
                }
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

    getGroup() {
        let self = this;
        request({
            type: 'get',
            beforeSend: function () {},
            url: '/diamond-ops/service/group',
            success: res => {
                if (res.code === 200) {
                    let data = res.data;
                    let groupList = [];
                    for (let i = 0; i < data.length; i++) {
                        groupList.push({
                            label: data[i],
                            value: data[i]
                        });
                    }
                    this.setState({
                        groupList: groupList
                    });
                }
            }
        });
    }
    /**
     * 回车事件
     */
    keyDownSearch(e) {
        var theEvent = e || window.event;
        var code = theEvent.keyCode || theEvent.which || theEvent.charCode;
        if (code == 13) {
            this.getData();
            return false;
        }
        return true;
    }
    navTo(url, record) {

        this.serverId = getParams('serverId') || '';
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        hashHistory.push(`${url}?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${record.group}&namespace=${this.tenant}`);
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
    componentWillMount() {
        window.addEventListener('keydown', this.keyDownSearch.bind(this), false);
    }
    componentWillUnMount() {
        window.removeEventListener('keydown', this.keyDownSearch.bind(this));
    }
    onSearch() {}

    onChange() {}
    cleanAndGetData(needclean = false) {
        if (needclean) {
            this.dataId = '';
            this.group = '';
            this.setState({
                group: '',
                dataId: ''
            });
            setParams({
                group: '',
                dataId: ''
            });
        }
        this.getData();
        this.getGroupsList();
        this.getTagLst();
    }
    getData(pageNo = 1, clearSelect = true) {
        let self = this;
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        this.serverId = getParams('serverId') || '';
        request({
            url: `/diamond-ops/configList/serverId/${this.serverId}?dataId=${this.dataId}&group=${this.group}&appName=${this.appName}&config_tags=${this.state.config_tags || ''}&pageNo=${pageNo}&pageSize=${this.state.pageSize}`,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (data) {
                if (data.code === 200) {
                    self.setState({
                        dataSource: data.data,
                        total: data.total,
                        currentPage: pageNo
                    });
                    if (clearSelect) {
                        self.setState({
                            selectedRecord: [],
                            selectedKeys: []
                        });
                    }
                }
                self.setState({
                    tenant: self.tenant
                });
            },
            complete: function () {
                self.closeLoading();
            }
        });
    }
    showMore() {}
    chooseNav(record, key) {
        let self = this;
        switch (key) {
            case 'nav1':
                self.navTo('/historyRollback', record);
                break;
            case 'nav2':
                self.navTo('/pushTrajectory', record);
                break;
            case 'nav3':
                self.navTo('/listeningToQuery', record);
                break;
        }
    }
    removeConfig(record) {
        let self = this;
        Dialog.confirm({
            language: window.pageLanguage || 'zh-cn',
            title: aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.Remove_configuration'),
            content: <div style={{ marginTop: '-20px' }}>
                <h3>{aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.suredelete')}</h3>
                <p>
                    <span style={{ color: '#999', marginRight: 5 }}>Data ID:</span>
                    <span style={{ color: '#c7254e' }}>
                        {record.dataId}
                    </span>
                </p>
                <p>
                    <span style={{ color: '#999', marginRight: 5 }}>Group:</span>
                    <span style={{ color: '#c7254e' }}>
                        {record.group}
                    </span>
                </p>
                <p>
                    <span style={{ color: '#999', marginRight: 5 }}>{aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.environment')}</span>
                    <span style={{ color: '#c7254e' }}>
                        {self.serverId || ''}
                                </span>
                </p>

            </div>,
            onOk: () => {
                let url = `/diamond-ops/configList/serverId/${self.serverId}/dataId/${record.dataId}/group/${record.group}?id=${record.id}`;
                if (this.tenant) {
                    //如果存在tenant 在path加上
                    url = `/diamond-ops/configList/serverId/${self.serverId}/dataId/${record.dataId}/group/${record.group}/tenant/${this.tenant}?id=${record.id}`;
                }
                request({
                    url: url,
                    type: 'delete',
                    success: function (res) {
                        let _payload = {};

                        _payload.title = aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.configuration_management');
                        _payload.content = '';
                        _payload.dataId = record.dataId;
                        _payload.group = record.group;
                        if (res.code === 200) {
                            _payload.isok = true;
                        } else {
                            _payload.isok = false;
                            _payload.message = res.message;
                        }
                        self.refs['delete'].openDialog(_payload);
                        self.getData();
                    }
                });
            }
        });
    }
    renderLastTime(value, index, record) {
        return <div>{aliwareIntl.intlNumberFormat(record.lastModifiedTime)}</div>;
    }
    showCode(record) {
        this.refs['showcode'].openDialog(record);
    }
    renderCol(value, index, record) {

        return <div>
            <a href="javascript:;" onClick={this.goDetail.bind(this, record)} style={{ marginRight: 5 }}>{aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.details')}</a>
            <span style={{ marginRight: 5 }}>|</span>
            <a href="javascript:;" style={{ marginRight: 5 }} onClick={this.goEditor.bind(this, record)}>{aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.edit')}</a>
            <span style={{ marginRight: 5 }}>|</span>
            <a href="javascript:;" style={{ marginRight: 5 }} onClick={this.removeConfig.bind(this, record)}>{aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.delete')}</a>
           


        </div>;
    }
    changePage(value) {
        this.setState({
            currentPage: value
        });
        this.getData(value, false);
    }
    onInputUpdate() {}
    chooseFieldChange(fieldValue) {

        this.setState({
            fieldValue
        });
    }
    showSelect(value) {
        this.setState({
            selectValue: value
        });
        if (value.indexOf('appName') !== -1) {
            this.setState({
                showAppName: true
            });
        } else {
            this.setState({
                showAppName: false
            });
        }
        if (value.indexOf('group') !== -1) {
            this.setState({
                showgroup: true
            });
        } else {
            this.setState({
                showgroup: false
            });
        }
        this.chooseFieldChange(value);
    }
    getAppName(value) {
        this.appName = value;
        this.setState({
            appName: value
        });
    }

    setAppName(value) {
        this.appName = value;
        this.setState({
            appName: value
        });
    }
    getDataId(value) {
        this.dataId = value;
        this.setState({
            dataId: value
        });
    }
    setConfigTags(value) {
        this.setState({
            config_tags: value
        });
    }
    /**
     * groupId赋值
     */
    setGroup(value) {
        this.group = value || '';
        this.setState({
            group: value || ''
        });
    }
    selectAll() {
        setParam('dataId', this.dataId);
        setParam('group', this.group);
        setParam('appName', this.appName);
        // if (this.dataId !== this.preDataId) {
        //     setParam('dataId', this.dataId);
        //     this.preDataId = this.dataId;
        // }
        // if (this.group !== this.preGroup) {
        //     setParam('group', this.preGroup);
        //     this.preGroup = this.group;
        // }
        // if (this.appName !== this.preAppName) {
        //     setParam('appName', this.appName);
        //     this.preAppName = this.appName;
        // }
        this.getData();
    }
    resetAll() {
        this.dataId = '';
        this.appName = '';
        this.group = '';
        this.setState({
            selectValue: [],
            dataId: '',
            appName: '',
            group: '',
            showAppName: false,
            showgroup: false
        });
        this.selectAll();
    }
    chooseEnv(value) {
        this.serverId = getParams('serverId') || 'center';
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        hashHistory.push(`/edasnewconfig?serverId=${this.serverId || ''}&namespace=${this.tenant}&edasAppName=${this.edasAppName}&appName=${this.edasAppName}&edasAppId=${this.edasAppId}`);
    }
    setNowNameSpace(name, id) {
        this.setState({
            nownamespace_name: name,
            nownamespace_id: id
        });
    }
    goDetail(record) {
        this.serverId = getParams('serverId') || 'center';
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        hashHistory.push(`/edasconfigdetail?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${record.group}&namespace=${this.tenant}&edasAppName=${this.edasAppName}&appName=${this.edasAppName}&edasAppId=${this.edasAppId}`);
    }
    goEditor(record) {
        this.serverId = getParams('serverId') || 'center';
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        hashHistory.push(`/edasconfigeditor?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${record.group}&namespace=${this.tenant}&edasAppName=${this.edasAppName}&appName=${this.edasAppName}&edasAppId=${this.edasAppId}`);
    }
    goConfigSync(record) {
        this.serverId = getParams('serverId') || 'center';
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        hashHistory.push(`/edasconfigsync?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${record.group}&namespace=${this.tenant}`);
    }
    doBatch(key) {
        switch (key) {
            case 'clone':
                this.checkForClone();
                break;
            case 'delete':
                this.doBatchDelete();
                break;
            case 'import':
                this.doImport();
                break;
            case 'export':
                this.checkForExport();
                break;
        }
    }
    checkForClone() {
        let self = this;
        if (this.state.selectedRecord.length < 1) {
            Dialog.alert({
                title: aliwareIntl.get('newDiamond.page.configurationManagement.Configuration_cloning0'),
                content: <div style={{ fontSize: 18, color: "#373D41" }}>{aliwareIntl.get('newDiamond.page.configurationManagement.select_need_to_clone_the_configuration_items1')}</div>,
                // content: <div ><span style={{fontSize:18,color:"#373D41"}}>您未选择配置项，确定按以下条件克隆所有数据？</span><p>DataId:2321, Group:testgroup, 标签:tag, 归属应用:testapp</p></div>,
                language: aliwareIntl.currentLanguageCode
                // onOk: () => {
                //
                // }
            });
            return;
        }
        let data = [];
        this.state.selectedRecord.forEach(record => {
            data.push({ dataId: record.dataId, group: record.group });
        });
        let reqData = {
            policy: 'abort',
            tenantFrom: this.tenant,
            tenantTo: '',
            dataId: this.dataId,
            group: this.group,
            appName: this.appName,
            configTags: this.state.config_tags,
            data: data
        };
        request({
            type: 'post',
            contentType: 'application/json',
            url: `/diamond-ops/batch/checkForClone/serverId/${this.serverId}`,
            data: JSON.stringify(reqData),
            beforeSend: function () {
                self.openLoading();
            },
            complete: function () {
                self.closeLoading();
            },
            success: res => {
                if (res.code === 200) {
                    let payload = {};
                    payload.serverId = this.serverId;
                    payload.tenantFrom = { id: this.tenant, name: this.state.nownamespace_name };
                    payload.dataId = this.dataId;
                    payload.group = this.group;
                    payload.appName = this.appName;
                    payload.configTags = this.state.config_tags;
                    payload.records = this.state.selectedRecord;
                    payload.total = res.data.total;
                    payload.checkData = reqData;
                    this.refs["cloneDialog"].openDialog(payload, this.doClone.bind(this));
                } else {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        title: aliwareIntl.get('newDiamond.page.configurationManagement.Cloning_check_fails'),
                        content: res.message
                    });
                }
            }
        });
    }
    doClone(payload, policy) {
        let self = this;
        request({
            type: 'post',
            contentType: 'application/json',
            url: `/diamond-ops/batch/clone/serverId/${this.serverId}`,
            data: JSON.stringify(payload),
            beforeSend: function () {
                self.openLoading();
            },
            complete: function () {
                self.closeLoading();
            },
            success: res => {
                if (res.code === 200) {
                    let msg = aliwareIntl.get('newDiamond.page.configurationManagement.process_is_successful,_the_cloned') + res.data.processedNum + aliwareIntl.get('newDiamond.page.configurationManagement.configuration');
                    if (res.data.duplicatedNum > 0) {
                        msg += aliwareIntl.get('newDiamond.page.configurationManagement.,_wherein') + res.data.duplicatedNum + aliwareIntl.get('newDiamond.page.configurationManagement.items_for') + policy;
                    }
                    Feedback.toast.success(msg);
                } else {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        title: aliwareIntl.get('newDiamond.page.configurationManagement.Clone_failed'),
                        content: self.getBatchFailedContent(res)
                    });
                }
            }
        });
    }
    doBatchDelete() {
        let self = this;
        if (this.state.selectedRecord.length < 1) {
            Dialog.alert({
                title: aliwareIntl.get('newDiamond.page.configurationManagement.bulk_delete'),
                content: <div style={{ fontSize: 18, color: "#373D41" }}>{aliwareIntl.get('newDiamond.page.configurationManagement.please_select_the_required_delete_the_configuration_item')}</div>,
                language: aliwareIntl.currentLanguageCode
            });
            return;
        }

        Dialog.confirm({
            language: window.pageLanguage || 'zh-cn',
            title: aliwareIntl.get('newDiamond.page.configurationManagement.bulk_delete'),
            content: <div style={{ fontSize: 18, color: "#373D41" }}>
                {aliwareIntl.get('newDiamond.page.configurationManagement.whether_to_delete_the_selected')}<span style={{ color: '#33cde5', margin: 5 }}>{this.state.selectedRecord.length}</span>{aliwareIntl.get('newDiamond.page.configurationManagement.configuration_item?')}
                </div>,
            onOk: () => {
                let payload = {
                    data: []
                };
                this.state.selectedRecord.forEach(value => {
                    payload.data.push({
                        dataId: value.dataId,
                        group: value.group
                    });
                });
                request({
                    type: 'post',
                    contentType: 'application/json',
                    url: `/diamond-ops/batch/delete/serverId/${this.serverId}/tenant/${this.tenant}`,
                    data: JSON.stringify(payload),
                    beforeSend: function () {
                        self.openLoading();
                    },
                    complete: function () {
                        self.closeLoading();
                    },
                    success: res => {
                        if (res.code === 200) {
                            Feedback.toast.success(aliwareIntl.get('newDiamond.page.configurationManagement._The_process_is_successful,_delete_the') + res.data.processedNum + aliwareIntl.get('newDiamond.page.configurationManagement.configuration'));
                        } else {
                            Dialog.alert({
                                language: window.pageLanguage || 'zh-cn',
                                title: aliwareIntl.get('newDiamond.page.configurationManagement.Delete_failed'),
                                content: self.getBatchFailedContent(res)
                            });
                        }
                        self.getData();
                    }
                });
            }
        });
    }

    detailNamespace(namespaceId) {
        let self = this;
        let serverId = getParams('serverId') || 'center';
        request({
            url: `/diamond-ops/service/namespaceOwnerInfo/${namespaceId}`,
            beforeSend: () => {
                this.openLoading();
            },
            success: res => {
                if (res.code === 200) {
                    let obj = {
                        regionId: res.data.regionId,
                        accessKey: res.data.accessKey,
                        secretKey: res.data.secretKey,
                        endpoint: res.data.endpoint
                    };

                    Dialog.alert({
                        needWrapper: false,
                        language: window.pageLanguage || 'zh-cn',
                        title: aliwareIntl.get('newDiamond.page.namespace.Namespace_details'),
                        content: <div>
                        
                        <div style={{ marginTop: '10px' }}>
                            <p>
                                <span style={{ color: '#999', marginRight: 5 }}>{aliwareIntl.get('newDiamond.page.namespace.region_ID')}</span>
                                <span style={{ color: '#c7254e' }}>
                                    {obj.regionId}
                                </span>
                            </p>
                            <p>
                                <span style={{ color: '#999', marginRight: 5 }}>{aliwareIntl.get('newDiamond.page.namespace.namespace_name')}</span>
                                <span style={{ color: '#c7254e' }}>
                                    {self.state.nownamespace_name}
                                </span>
                            </p>
                            <p>
                                <span style={{ color: '#999', marginRight: 5 }}>{aliwareIntl.get('newDiamond.page.namespace.namespace_ID')}</span>
                                <span style={{ color: '#c7254e' }}>
                                    {namespaceId}
                                </span>
                            </p>
                            <p>
                                <span style={{ color: '#999', marginRight: 5 }}>End Point:</span>
                                <span style={{ color: '#c7254e' }}>
                                    {obj.endpoint}
                                </span>
                            </p>
                            <p>
                                <span style={{ color: '#999', marginRight: 5 }}>{aliwareIntl.get('newDiamond.page.configurationManagement.AccessKey_recommended2')}</span>
                                <span style={{ color: '#c7254e' }}>
                                    <a href={window._getLink && window._getLink("getAk")} target="_blank">{aliwareIntl.get('newDiamond.page.configurationManagement.click_on_the_obtain_of3')}</a>
                                </span>
                            </p>
                            <p>
                                <span style={{ color: '#999', marginRight: 5 }}>{aliwareIntl.get('newDiamond.page.configurationManagement.SecretKey_recommended4')}</span>
                                <span style={{ color: '#c7254e' }}>
                                    <a href={window._getLink && window._getLink("getAk")} target="_blank">{aliwareIntl.get('newDiamond.page.configurationManagement.click_on_the_obtain_of3')}</a>
                                </span>
                            </p>
                            <p>
                                <span style={{ color: '#999', marginRight: 5 }}>{aliwareIntl.get('newDiamond.page.configurationManagement.ACM_dedicated_AccessKey_will_the_waste,_does_not_recommend_the_use_of5')}</span>
                                <span style={{ color: '#c7254e' }}>
                                    {obj.accessKey}
                                </span>
                            </p>
                            <p>
                                <span style={{ color: '#999', marginRight: 5 }}>{aliwareIntl.get('newDiamond.page.configurationManagement.ACM_special_SecretKey_will_be_abandoned,_not_recommended_for_use6')}</span>
                                <span style={{ color: '#c7254e' }}>
                                    {obj.secretKey}
                                </span>
                            </p>
                        </div>
                         <div style={{ marginTop: '20px', backgroundColor: '#eee', padding: 10, fontSize: 12 }}>{aliwareIntl.get('newDiamond.page.configurationManagement.note_ACM_is_dedicated_AK/SK_is_mainly_used_for_some_of_the_compatibility_scenario,_it_is_recommended_to_Unified_the_use_of_Ali_cloud_AK/SK.7')}<a href={window._getLink && window._getLink("akHelp")} target="_blank">{aliwareIntl.get('newDiamond.page.configurationManagement._Details_of8')}</a>
                         </div>
                        </div>
                    });
                }
            },
            complete: () => {
                this.closeLoading();
            }
        });
    }

    doImport() {
        let payload = {
            tenant: { id: this.tenant, name: this.state.nownamespace_name },
            serverId: this.serverId
        };
        this.refs["importDialog"].openDialog(payload, this.showImportResult.bind(this));
    }
    showImportResult(res, policy) {
        let self = this;
        if (res.code === 200) {
            let msg = aliwareIntl.get('newDiamond.page.configurationManagement.process_is_successful,_import_the') + res.data.processedNum + aliwareIntl.get('newDiamond.page.configurationManagement.configuration');
            if (res.data.duplicatedNum > 0) {
                msg += aliwareIntl.get('newDiamond.page.configurationManagement.,_wherein') + res.data.duplicatedNum + aliwareIntl.get('newDiamond.page.configurationManagement.items_for') + policy;
            }
            Feedback.toast.success(msg);
        } else {
            Dialog.alert({
                language: window.pageLanguage || 'zh-cn',
                title: aliwareIntl.get('newDiamond.page.configurationManagement.import_failed'),
                content: self.getBatchFailedContent(res)
            });
        }
        self.getData();
    }
    checkForExport() {
        if (this.state.selectedRecord.length < 1) {
            Dialog.alert({
                title: aliwareIntl.get('newDiamond.page.configurationManagement.configuration_export9'),
                content: <div style={{ fontSize: 18, color: "#373D41" }}>{aliwareIntl.get('newDiamond.page.configurationManagement.please_choose_the_required_export_configuration_items10')}</div>,
                language: aliwareIntl.currentLanguageCode
            });
            return;
        }
        let data = [];
        let self = this;
        this.state.selectedRecord.forEach(record => {
            data.push({ dataId: record.dataId, group: record.group });
        });
        let reqData = {
            dataId: this.dataId,
            group: this.group,
            appName: this.appName,
            configTags: this.state.config_tags,
            data: data
        };
        request({
            type: 'post',
            contentType: 'application/json',
            url: `/diamond-ops/batch/checkForExport/serverId/${this.serverId}/tenant/${this.tenant}`,
            data: JSON.stringify(reqData),
            beforeSend: function () {
                self.openLoading();
            },
            complete: function () {
                self.closeLoading();
            },
            success: res => {
                if (res.code === 200) {
                    let payload = {};
                    payload.serverId = this.serverId;
                    payload.tenant = { id: this.tenant, name: this.state.nownamespace_name };
                    payload.dataId = this.dataId;
                    payload.group = this.group;
                    payload.appName = this.appName;
                    payload.configTags = this.state.config_tags;
                    payload.records = this.state.selectedRecord;
                    payload.total = res.data.total;
                    this.refs["exportDialog"].openDialog(payload);
                } else {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        title: aliwareIntl.get('newDiamond.page.configurationManagement.export_check_failed'),
                        content: <div style={{ fontSize: 18, color: "#373D41" }}>{res.message}</div>
                    });
                }
            }
        });
    }
    onSelectChange(...args) {
        let record = [];
        args[1].forEach(item => {
            if (args[0].indexOf(item.id) >= 0 && this.state.selectedKeys.indexOf(item.id) < 0) {
                record.push(item);
            }
        });
        this.state.selectedRecord.forEach(item => {
            if (args[0].indexOf(item.id) >= 0) {
                record.push(item);
            }
        });
        this.setState({
            selectedRecord: record,
            selectedKeys: args[0]
        });
    }

    onPageSelectAll(selected, records) {
        console.log(this.refs["dataTable"].props.dataSource);
    }

    getBatchFailedContent(res) {
        return <div style={{ height: 500, overflow: "auto" }}>
            <div style={{ fontSize: 18, color: "#373D41", overflow: "auto" }}>{res.message}</div>
            {"data" in res && res.data != null && <Accordion style={{ width: '500px' }}>
                {"failedItems" in res.data && res.data.failedItems.length > 0 ? <Panel title={aliwareIntl.get('newDiamond.page.configurationManagement.failed_entry') + res.data.failedItems.length}>
                    <Table dataSource={res.data.failedItems} fixedHeader={true} maxBodyHeight={400} language={aliwareIntl.currentLanguageCode}>
                        <Table.Column title={'Data ID'} dataIndex="dataId" />
                        <Table.Column title={'Group'} dataIndex="group" />
                    </Table>
                    </Panel> : <Panel style={{ display: 'none' }} />}
                {"succeededItems" in res.data && res.data.succeededItems.length > 0 ? <Panel title={aliwareIntl.get('newDiamond.page.configurationManagement.successful_entry') + res.data.succeededItems.length}>
                        <Table dataSource={res.data.succeededItems} fixedHeader={true} maxBodyHeight={400} language={aliwareIntl.currentLanguageCode}>
                            <Table.Column title={'Data ID'} dataIndex="dataId" />
                            <Table.Column title={'Group'} dataIndex="group" />
                        </Table>
                    </Panel> : <Panel style={{ display: 'none' }} />}
                {"unprocessedItems" in res.data && res.data.unprocessedItems.length > 0 ? <Panel title={aliwareIntl.get('newDiamond.page.configurationManagement.unprocessed_entry') + res.data.unprocessedItems.length}>
                    <Table dataSource={res.data.unprocessedItems} fixedHeader={true} maxBodyHeight={400} language={aliwareIntl.currentLanguageCode}>
                        <Table.Column title={'Data ID'} dataIndex="dataId" />
                        <Table.Column title={'Group'} dataIndex="group" />
                    </Table>
                </Panel> : <Panel style={{ display: 'none' }} />}
            </Accordion>}
        </div>;
    }
    onClickBatchHandle() {
        this.batchHandle && this.batchHandle.openDialog({
            serverId: this.serverId,
            group: this.group,
            dataId: this.dataId,
            appName: this.appName,
            config_tags: this.state.config_tags || '',
            pageSize: this.state.pageSize
        });
    }
    render() {
        const { init, getValue } = this.field;

        const pubnodedata = aliwareIntl.get('pubnodata');

        const locale = {
            empty: pubnodedata
        };
        const helpDataId = <Balloon trigger={<span>Data ID <Icon type="help" size={'small'} style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle' }} /></span>} align="t" style={{ marginRight: 5 }} triggerType="hover">
                <a href={window._getLink && window._getLink("knowDataid")} target="_blank">{aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.click_to_learn_DataId')}</a>
            </Balloon>;
        const helpGroup = <Balloon trigger={<span>Group <Icon type="help" size={'small'} style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle' }} /></span>} align="t" style={{ marginRight: 5 }} triggerType="hover">
                <a href={window._getLink && window._getLink("knowGoup")} target="_blank">{aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.click_to_learn_Group')}</a>
            </Balloon>;
        return (
            <div style={{ padding: 10 }}>
                <BatchHandle ref={ref => this.batchHandle = ref} />
                <Loading shape="flower" style={{ position: 'relative' }} visible={this.state.loading} tip="Loading..." color="#333">
                <div style={{display:'none'}}>
                    <RegionGroup left={aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.configuration_management')} namespaceCallBack={this.cleanAndGetData.bind(this)} setNowNameSpace={this.setNowNameSpace.bind(this)}/>
                </div>
                    {}
                    {}
                    <div style={{ marginTop: 10 }}>

                        <Form inline>
                            {}

                            <FormItem label="Data ID:">
                                <Input htmlType="text" placeholder={aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.fuzzyd')} style={{ height: 32 }} value={this.state.dataId} onChange={this.getDataId.bind(this)} />
                            </FormItem>

                            <FormItem label="Group:">
                            <Combobox size="medium" hasArrow placeholder={aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.fuzzyg')} dataSource={this.state.groups} value={this.state.group} onChange={this.setGroup.bind(this)} onChange={this.setGroup.bind(this)} hasClear language={aliwareIntl.currentLanguageCode}>
                            </Combobox>
                             </FormItem>

                            <FormItem label={aliwareIntl.get('newDiamond.page.configurationManagement.Tags')}>
                            <Combobox size="medium" hasArrow multiple={true} tags={true} filterLocal={false} placeholder={aliwareIntl.get('newDiamond.page.configurationManagement.Please_enter_tag')} dataSource={this.state.tagLst} value={this.state.config_tags} onChange={this.setConfigTags.bind(this)} hasClear language={aliwareIntl.currentLanguageCode}>
                            </Combobox>
                            </FormItem>
                           
                            <FormItem label="">
                                <Button type="primary" style={{ marginRight: 10 }} onClick={this.selectAll.bind(this)}>{aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.query')}</Button>
                              
                                <Button type="primary" onClick={this.chooseEnv.bind(this)} style={{ float: 'right', marginLeft: 10 }}>
                                    <Icon type="edit" size={'small'} />{aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.new_listing')}</Button>
                            </FormItem>
                        </Form>

                    </div>
                 
                    <div>

                        <Table dataSource={this.state.dataSource} locale={locale} fixedHeader={true} maxBodyHeight={400} language={aliwareIntl.currentLanguageCode} rowSelection={{
                            onChange: this.onSelectChange.bind(this),
                            selectedRowKeys: this.state.selectedKeys
                        }} ref="dataTable">
                            <Table.Column title={helpDataId} dataIndex="dataId" />
                            <Table.Column title={helpGroup} dataIndex="group" />
                           
                            <Table.Column title={aliwareIntl.get('com.alibaba.cspupcloud.page.configurationManagement.operation')} cell={this.renderCol.bind(this)} />
                        </Table>

                    </div>
                    <div style={{ marginTop: 10, overflow: "hidden" }}>
                        <Button style={{ display: "none" }} type="primary" onClick={this.onClickBatchHandle.bind(this)}>{aliwareIntl.get('newDiamond.page.configurationManagement.Batch_processing0')}</Button>
                        <Pagination style={{ float: "right" }} current={this.state.currentPage} language={window.pageLanguage || 'zh-cn'} total={this.state.total} pageSize={this.state.pageSize} onChange={this.changePage.bind(this)} />
                </div>
                    <ShowCodeing ref="showcode" />
                    <DeleteDialog ref="delete" />
                    <CloneDialog ref="cloneDialog" />
                    <ImportDialog ref="importDialog" />
                    <ExportDialog ref="exportDialog" />
                </Loading>
            </div>
        );
    }

}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default EdasConfigurationManagement;