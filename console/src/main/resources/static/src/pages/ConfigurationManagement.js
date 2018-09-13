import React from 'react'; 
import BatchHandle from '../components/BatchHandle' ;
import RegionGroup from '../components/RegionGroup' ;
import ShowCodeing from '../components/ShowCodeing' ;
import DeleteDialog from '../components/DeleteDialog' ;
import CloneDialog from '../components/CloneDialog' ;
import ImportDialog from '../components/ImportDialog' ;
import ExportDialog from '../components/ExportDialog' ;
import { Balloon, Button, Checkbox, Collapse, Dialog, Dropdown, Field, Form, Icon, Input, Loading, Menu, Message, Pagination, Select, Slider, Table } from '@alifd/next';
const Accordion = Collapse; 
const FormItem = Form.Item; 
const { Panel } = Collapse; 
const { AutoComplete: Combobox } = Select;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
const DashboardCard = ({ data, height }) => <div>
   {data.modeType === 'notice' ? <div data-spm-click={"gostr=/aliyun;locaid=notice"}><Slider style={{ marginBottom: data.modeList.length > 1 ? 20 : 10 }} arrows={false}>	
            {data.modeList.map((item, index) => <div key={index} className={"slider-img-wrapper"}>
                    <div className={"alert alert-success"} style={{ minHeight: 120, backgroundColor: '#e9feff' }}>
                        <div className={"alert-success-text"} style={{ fontWeight: 'bold' }}>{window.aliwareIntl.get("nacos.page.configurationManagement.Important_reminder0") /*重要提醒*/}</div>
                        <strong style={{ color: '#777a7e' }}>
                            <span>{item.title}</span>
                        </strong>
                        <strong>
                            <span><a style={{ marginLeft: 10, color: '#33cde5' }} href={item.url} target={"_blank"}>{window.aliwareIntl.get("nacos.page.configurationManagement.view_details1") /*查看详情*/}</a></span>
                        </strong> 
                    </div>
                </div>)}	
        </Slider> </div> : <div className={"dash-card-contentwrappers"} style={{ height: height ? height : 'auto' }} data-spm-click={`gostr=/aliyun;locaid=${data.modeType}`}>	
        <h3 className={"dash-card-title"}>{data.modeName}</h3>	
        <div className={"dash-card-contentlist"}>	
            {data.modeList ? data.modeList.map(item => {
                return <div className={"dash-card-contentitem"}>	
                    <a href={item.url} target={"_blank"}>{item.title}</a>	
                    {item.tag === 'new' ? <img style={{ width: 28, marginLeft: 2, verticalAlign: 'text-bottom' }} src={"//img.alicdn.com/tps/TB1pS2YMVXXXXcCaXXXXXXXXXXX-56-24.png"} alt="" /> : ''}	
                    {item.tag === 'hot' ? <img style={{ width: 28, marginLeft: 2, verticalAlign: 'text-bottom' }} src={"//img.alicdn.com/tps/TB1nusxPXXXXXb0aXXXXXXXXXXX-56-24.png"} alt="" /> : ''}	
                </div>;
            }) : ''}	
        </div>	
    </div>}	</div>;
class ConfigurationManagement extends React.Component {
    constructor(props) {
        super(props);
        this.field = new Field(this);
        this.appName = window.getParams('appName') || window.getParams('edasAppId') || '';
        this.preAppName = this.appName;
        this.group = window.getParams('group') || '';
        this.preGroup = this.group;
        this.dataId = window.getParams('dataId') || '';
        this.preDataId = this.dataId;
        this.serverId = window.getParams('serverId') || 'center';
        this.edasAppId = window.getParams('edasAppId') || '';
        this.edasAppName = window.getParams('edasAppName') || '';
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
            selectedKeys: [],
            hasdash: false,
            isCn: true,
            contentList: [],
            isAdvancedQuery: false,
            isCheckAll: false
        };
        let obj = {
            dataId: this.dataId || '',
            group: this.preGroup || '',
            appName: this.appName || ''
        };
        window.setParams(obj);
        this.batchHandle = null;
        this.toggleShowQuestionnaire = this.toggleShowQuestionnaire.bind(this);
    }

    componentDidMount() {
        this.getGroup();
        if (window.pageLanguage === 'zh-cn') {
            this.getContentList(); //在中文站获取概览页
            this.setState({
                isCn: true
            });
        } else {
            this.setState({
                isCn: false
            });
        }
        if (window._getLink && window._getLink("isCn") === "true") {
            if (!this.checkQuestionnaire()) {
                if (window.location.host === 'acm.console.aliyun.com') {
                    Dialog.alert({
                        title: window.aliwareIntl.get("nacos.page.configurationManagement.questionnaire2") /*问卷调查*/
                        , style: {
                            width: '60%'
                        },
                        content: <div>	
                            <div style={{ fontSize: '15px', lineHeight: '22px' }}>{window.aliwareIntl.get("nacos.page.configurationManagement.a_ACM_front-end_monitoring_questionnaire,_the_time_limit_to_receive_Ali_cloud_voucher_details_shoved_stamp_the3") /*答ACM前端监控调查问卷，限时领取阿里云代金券详情猛戳：*/}<a href={"https://survey.aliyun.com/survey/k0BjJ2ARC"} target={"_blank"}>{window.aliwareIntl.get("nacos.page.configurationManagement.questionnaire2") /*问卷调查*/}</a>	
                        </div>	
                        <div style={{ fontSize: '15px' }}>{window.aliwareIntl.get("nacos.page.configurationManagement.no_longer_display4") /*不再显示：*/}<Checkbox onChange={this.toggleShowQuestionnaire} />	
                            </div>	
                        </div>,
                        language: window.aliwareIntl.currentLanguageCode
                    });
                }
            }
        }
    }
    /**
     * 获取概览页数据
     */
    getContentList() {

        window.request({
            url: 'com.alibaba.nacos.service.dashlist', //以 com.alibaba. 开头最终会转换为真正的url地址
            data: {},
            $data: {}, //替换请求url路径中{}占位符的内容
            success: res => {
                console.log(res);
                if (res.code === 200 && res.data) {
                    if (res.data.length === 0) {
                        this.setState({
                            hasdash: false
                        });
                    } else {
                        this.setState({
                            hasdash: true,
                            contentList: res.data
                        });
                    }
                }
            }
        });
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
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数	
        this.serverId = window.getParams('serverId') || '';
        window.request({

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
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数	
        this.serverId = window.getParams('serverId') || '';
        window.request({

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
        window.request({
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
        if (code === 13) {
            this.getData();
            return false;
        }
        return true;
    }
    navTo(url, record) {

        this.serverId = window.getParams('serverId') || '';
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数	
        window.hashHistory.push(`${url}?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${record.group}&namespace=${this.tenant}`);
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
    UNSAFE_componentWillMount() {
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
            window.setParams({
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
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数	
        this.serverId = window.getParams('serverId') || '';
        let urlPrefix = "";
        if (this.dataId.indexOf("*") !== -1 || this
        		.group.indexOf("*") !== -1) {
        	urlPrefix = "/nacos/v1/cs/configs?search=blur";
		} else {
			urlPrefix = "/nacos/v1/cs/configs?search=accurate";
		}
       
        window.request({
            url: `${urlPrefix}&dataId=${this.dataId}&group=${this.group}&appName=${this.appName}&config_tags=${this.state.config_tags || ''}&pageNo=${pageNo}&pageSize=${this.state.pageSize}`,
            //            url: `/diamond-ops/configList/serverId/${this.serverId}?dataId=${this.dataId}&group=${this.group}&appName=${this.appName}&config_tags=${this.state.config_tags || ''}&pageNo=${pageNo}&pageSize=${this.state.pageSize}`,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (data) {
            	if (data!=null) {
                    self.setState({
                        dataSource: data.pageItems,
                        total: data.totalCount,
                        currentPage: data.pageNumber
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
            error: function (data) {
            	self.setState({
                        dataSource: [],
                        total: 0,
                        currentPage: 0
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
            default:
            case 'nav3':
                self.navTo('/listeningToQuery', record);
                break;
        }
    }
    removeConfig(record) {
        let self = this;
        Dialog.confirm({
            language: window.pageLanguage || 'zh-cn',
            title: window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.Remove_configuration'),
            content: <div style={{ marginTop: '-20px' }}>	
                <h3>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.suredelete')}</h3>	
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
                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.environment')}</span>	
                    <span style={{ color: '#c7254e' }}>	
                        {self.serverId || ''}	
                                </span>	
                </p>	
	
            </div>,
            onOk: () => {
                let url = `/nacos/v1/cs/configs?dataId=${record.dataId}&group=${record.group}`;
                window.request({
                    url: url,
                    type: 'delete',
                    success: function (res) {
                        let _payload = {};

                        _payload.title = window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.configuration_management');
                        _payload.content = '';
                        _payload.dataId = record.dataId;
                        _payload.group = record.group;
                        if (res === true) {
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
        return <div>{window.aliwareIntl.intlNumberFormat(record.lastModifiedTime)}</div>;
    }
    showCode(record) {
        this.refs['showcode'].openDialog(record);
    }
    renderCol(value, index, record) {

        return <div>	
            <a onClick={this.goDetail.bind(this, record)} style={{ marginRight: 5 }}>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.details')}</a>	
            <span style={{ marginRight: 5 }}>|</span>	
            <a style={{ marginRight: 5 }} onClick={this.showCode.bind(this, record)}>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.the_sample_code')}</a>	
            <span style={{ marginRight: 5 }}>|</span>	
            <a style={{ marginRight: 5 }} onClick={this.goEditor.bind(this, record)}>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.edit')}</a>	
            <span style={{ marginRight: 5 }}>|</span>	
            <a style={{ marginRight: 5 }} onClick={this.removeConfig.bind(this, record)}>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.delete')}</a>	
            <span style={{ marginRight: 5 }}>|</span>	
	
            <Dropdown trigger={<span style={{ color: '#33cde5' }}>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.more')}<Icon type={"arrow-down-filling"} size={'xxs'} /></span>} triggerType={"click"}>	
                <Menu onItemClick={this.chooseNav.bind(this, record)}>	
                    <Menu.Item key={"nav1"}>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.version')}</Menu.Item>	
                    <Menu.Item key={"nav3"}>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.listener_query')}</Menu.Item>	
                </Menu>	
            </Dropdown>	
	
        </div>;
    }
    changePage(value) {
        this.setState({
            currentPage: value
        });
        this.getData(value, false);
    }
    handlePageSizeChange(pageSize) {
        this.setState({
            pageSize
        });
        this.changePage(1);
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
        window.setParam('dataId', this.dataId);
        window.setParam('group', this.group);
        window.setParam('appName', this.appName);
        // if (this.dataId !== this.preDataId) {	
        //     window.setParam('dataId', this.dataId);	
        //     this.preDataId = this.dataId;	
        // }	
        // if (this.group !== this.preGroup) {	
        //     window.setParam('group', this.preGroup);	
        //     this.preGroup = this.group;	
        // }	
        // if (this.appName !== this.preAppName) {	
        //     window.setParam('appName', this.appName);	
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
        this.serverId = window.getParams('serverId') || 'center';
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数	
        window.hashHistory.push(`/newconfig?serverId=${this.serverId || ''}&namespace=${this.tenant}&edasAppName=${this.edasAppName}&edasAppId=${this.edasAppId}&searchDataId=${this.dataId}&searchGroup=${this.group}`);
    }
    setNowNameSpace(name, id) {
        this.setState({
            nownamespace_name: name,
            nownamespace_id: id
        });
    }
    goDetail(record) {
        this.serverId = window.getParams('serverId') || 'center';
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数
        // 点击详情到另一个页面, 返回时候要保留原来的搜索条件 比如: record.dataId为详情的, this.dataId为搜索条件的.
        window.hashHistory.push(`/configdetail?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${record.group}&namespace=${this.tenant}&edasAppName=${this.edasAppName}&searchDataId=${this.dataId}&searchGroup=${this.group}`);
    }
    goEditor(record) {
        this.serverId = window.getParams('serverId') || 'center';
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数	
        window.hashHistory.push(`/configeditor?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${record.group}&namespace=${this.tenant}&edasAppName=${this.edasAppName}&edasAppId=${this.edasAppId}&searchDataId=${this.dataId}&searchGroup=${this.group}`);
    }
    goConfigSync(record) {
        this.serverId = window.getParams('serverId') || 'center';
        this.tenant = window.getParams('namespace') || ''; //为当前实例保存tenant参数	
        window.hashHistory.push(`/configsync?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${record.group}&namespace=${this.tenant}`);
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
            default:
            case 'export':
                this.checkForExport();
                break;
        }
    }
    checkForClone() {
        let self = this;
        if (this.state.selectedRecord.length < 1) {
            Dialog.alert({
                title: window.aliwareIntl.get('nacos.page.configurationManagement.Configuration_cloning0'),
                content: <div style={{ fontSize: 18, color: "#373D41" }}>{window.aliwareIntl.get('nacos.page.configurationManagement.select_need_to_clone_the_configuration_items1')}</div>,
                // content: <div ><span style={{fontSize:18,color:"#373D41"}}>您未选择配置项，确定按以下条件克隆所有数据？</span><p>DataId:2321, Group:testgroup, 标签:tag, 归属应用:testapp</p></div>,	
                language: window.aliwareIntl.currentLanguageCode
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
        window.request({
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
                        title: window.aliwareIntl.get('nacos.page.configurationManagement.Cloning_check_fails'),
                        content: res.message
                    });
                }
            }
        });
    }
    doClone(payload, policy) {
        let self = this;
        window.request({
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
                    let msg = window.aliwareIntl.get('nacos.page.configurationManagement.process_is_successful,_the_cloned') + res.data.processedNum + window.aliwareIntl.get('nacos.page.configurationManagement.configuration');
                    if (res.data.duplicatedNum > 0) {
                        msg += window.aliwareIntl.get('nacos.page.configurationManagement.,_wherein') + res.data.duplicatedNum + window.aliwareIntl.get('nacos.page.configurationManagement.items_for') + policy;
                    }
                    Message.success(msg);
                } else {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        title: window.aliwareIntl.get('nacos.page.configurationManagement.Clone_failed'),
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
                title: window.aliwareIntl.get('nacos.page.configurationManagement.bulk_delete'),
                content: <div style={{ fontSize: 18, color: "#373D41" }}>{window.aliwareIntl.get('nacos.page.configurationManagement.please_select_the_required_delete_the_configuration_item')}</div>,
                language: window.aliwareIntl.currentLanguageCode
            });
            return;
        }

        Dialog.confirm({
            language: window.pageLanguage || 'zh-cn',
            title: window.aliwareIntl.get('nacos.page.configurationManagement.bulk_delete'),
            content: <div style={{ fontSize: 18, color: "#373D41" }}>	
                {window.aliwareIntl.get('nacos.page.configurationManagement.whether_to_delete_the_selected')}<span style={{ color: '#33cde5', margin: 5 }}>{this.state.selectedRecord.length}</span>{window.aliwareIntl.get('nacos.page.configurationManagement.configuration_item?')}	
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
                window.request({
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
                            Message.success(window.aliwareIntl.get('nacos.page.configurationManagement._The_process_is_successful,_delete_the') + res.data.processedNum + window.aliwareIntl.get('nacos.page.configurationManagement.configuration'));
                        } else {
                            Dialog.alert({
                                language: window.pageLanguage || 'zh-cn',
                                title: window.aliwareIntl.get('nacos.page.configurationManagement.Delete_failed'),
                                content: self.getBatchFailedContent(res)
                            });
                        }
                        self.getData();
                    }
                });
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
            let msg = window.aliwareIntl.get('nacos.page.configurationManagement.process_is_successful,_import_the') + res.data.processedNum + window.aliwareIntl.get('nacos.page.configurationManagement.configuration');
            if (res.data.duplicatedNum > 0) {
                msg += window.aliwareIntl.get('nacos.page.configurationManagement.,_wherein') + res.data.duplicatedNum + window.aliwareIntl.get('nacos.page.configurationManagement.items_for') + policy;
            }
            Message.success(msg);
        } else {
            Dialog.alert({
                language: window.pageLanguage || 'zh-cn',
                title: window.aliwareIntl.get('nacos.page.configurationManagement.import_failed'),
                content: self.getBatchFailedContent(res)
            });
        }
        self.getData();
    }
    checkForExport() {
        if (this.state.selectedRecord.length < 1) {
            Dialog.alert({
                title: window.aliwareIntl.get('nacos.page.configurationManagement.configuration_export9'),
                content: <div style={{ fontSize: 18, color: "#373D41" }}>{window.aliwareIntl.get('nacos.page.configurationManagement.please_choose_the_required_export_configuration_items10')}</div>,
                language: window.aliwareIntl.currentLanguageCode
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
        window.request({
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
                        title: window.aliwareIntl.get('nacos.page.configurationManagement.export_check_failed'),
                        content: <div style={{ fontSize: 18, color: "#373D41" }}>{res.message}</div>
                    });
                }
            }
        });
    }
    onSelectChange(...args) {
        let record = [];
        console.log(args, 'args');
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
            selectedKeys: args[0],
            isCheckAll: record.length > 0 && record.length === this.state.dataSource.length
        });
        console.log(this.state, 'this.state');
    }

    onPageSelectAll(selected, records) {
        console.log(this.refs["dataTable"].props.dataSource);
    }

    getBatchFailedContent(res) {
        return <div>	
            <div style={{ fontSize: 18, color: "#373D41", overflow: "auto" }}>{res.message}</div>	
            {"data" in res && res.data != null && <Accordion style={{ width: '500px' }}>	
                {"failedItems" in res.data && res.data.failedItems.length > 0 ? <Panel title={window.aliwareIntl.get('nacos.page.configurationManagement.failed_entry') + res.data.failedItems.length}>	
                    <Table dataSource={res.data.failedItems} fixedHeader={true} maxBodyHeight={400} language={window.aliwareIntl.currentLanguageCode}>	
                        <Table.Column title={'Data ID'} dataIndex={"dataId"} />	
                        <Table.Column title={'Group'} dataIndex={"group"} />	
                    </Table>	
                    </Panel> : <Panel style={{ display: 'none' }} />}	
                {"succeededItems" in res.data && res.data.succeededItems.length > 0 ? <Panel title={window.aliwareIntl.get('nacos.page.configurationManagement.successful_entry') + res.data.succeededItems.length}>	
                        <Table dataSource={res.data.succeededItems} fixedHeader={true} maxBodyHeight={400} language={window.aliwareIntl.currentLanguageCode}>	
                            <Table.Column title={'Data ID'} dataIndex={"dataId"} />	
                            <Table.Column title={'Group'} dataIndex={"group"} />	
                        </Table>	
                    </Panel> : <Panel style={{ display: 'none' }} />}	
                {"unprocessedItems" in res.data && res.data.unprocessedItems.length > 0 ? <Panel title={window.aliwareIntl.get('nacos.page.configurationManagement.unprocessed_entry') + res.data.unprocessedItems.length}>	
                    <Table dataSource={res.data.unprocessedItems} fixedHeader={true} maxBodyHeight={400} language={window.aliwareIntl.currentLanguageCode}>	
                        <Table.Column title={'Data ID'} dataIndex={"dataId"} />	
                        <Table.Column title={'Group'} dataIndex={"group"} />	
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
    changeAdvancedQuery = () => {
        this.setState({
            isAdvancedQuery: !this.state.isAdvancedQuery
        });
    };
    checkAllHandle(checked) {
        this.setState({
            isCheckAll: checked,
            selectedKeys: checked ? this.state.dataSource.map(item => item.id) : [],
            selectedRecord: checked ? this.state.dataSource : []
        });
    }
    render() {
        const pubnodedata = window.aliwareIntl.get('pubnodata');
        const locale = {
            empty: pubnodedata
        };
        const helpDataId = <Balloon trigger={<span>Data ID <Icon type={"help"} size={'small'} style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle' }} /></span>} align={"t"} style={{ marginRight: 5 }} triggerType={"hover"}>	
                <a href={window._getLink && window._getLink("knowDataid")} target={"_blank"}>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.click_to_learn_DataId')}</a>	
            </Balloon>;
        const helpGroup = <Balloon trigger={<span>Group <Icon type={"help"} size={'small'} style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle' }} /></span>} align={"t"} style={{ marginRight: 5 }} triggerType={"hover"}>	
                <a href={window._getLink && window._getLink("knowGoup")} target={"_blank"}>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.click_to_learn_Group')}</a>	
            </Balloon>;
        return (
            <div>	
                <BatchHandle ref={ref => this.batchHandle = ref} />	
                <Loading shape={"flower"} style={{ position: 'relative', width: '100%', overflow: 'auto' }} visible={this.state.loading} tip={"Loading..."} color={"#333"}>	
                <div className={this.state.hasdash ? 'dash-page-container' : ''}>	
                <div className={this.state.hasdash ? 'dash-left-container' : ''} style={{ position: 'relative', padding: 10 }}>
                <div style={{ display: this.inApp ? 'none' : 'block', marginTop: -15 }}>	
                    <RegionGroup namespaceCallBack={this.cleanAndGetData.bind(this)} setNowNameSpace={this.setNowNameSpace.bind(this)} />	
                </div>	
                <div style={{ display: this.inApp ? 'none' : 'block', position: 'relative', width: '100%', overflow: 'hidden', height: '40px' }}>	
                    <h3 style={{ height: 30, width: '100%', lineHeight: '30px', padding: 0, margin: 0, paddingLeft: 10, borderLeft: '3px solid #09c', color: '#ccc', fontSize: '12px' }}>
                        <span style={{ fontSize: '14px', color: '#000', marginRight: 8 }}>{window.aliwareIntl.get("nacos.page.configurationManagement.configuration_management8") /*配置管理*/}</span>
                        <span style={{ fontSize: '14px', color: '#000', marginRight: 8 }}>|</span>
                        <span style={{ fontSize: '14px', color: '#000', marginRight: 8 }}>{this.state.nownamespace_name}</span>
                        <span style={{ fontSize: '14px', color: '#000', marginRight: 18 }}>{this.state.nownamespace_id}</span>
                        {window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.query_results')}
                        <strong style={{ fontWeight: 'bold' }}> {this.state.total} </strong>
                        {window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.article_meet_the_requirements')}
                    </h3>	
                    <div style={{ position: 'absolute', textAlign: 'right', zIndex: 2, right: 0, top: 0 }}>	
                    </div>	
                </div>	
                    <div style={{ position: 'relative', marginTop: 10, height: this.state.isAdvancedQuery ? 'auto' : 48 }}>	
                        <Form direction={"hoz"} inline>	
                            <FormItem label={"Data ID:"}>	
                                <Input htmlType={"text"} placeholder={window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.fuzzyd')} style={{ width: 200 }} value={this.state.dataId} onChange={this.getDataId.bind(this)} />	
                            </FormItem>	
        
                            <FormItem label={"Group:"}>	
                                <Combobox style={{ width: 200 }} size={"medium"} hasArrow placeholder={window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.fuzzyg')} dataSource={this.state.groups} value={this.state.group} onChange={this.setGroup.bind(this)} hasClear language={window.aliwareIntl.currentLanguageCode}>	
                                </Combobox>	
                            </FormItem>	
                            <FormItem label={""}>	
                                <Button type={"primary"} style={{ marginRight: 10 }} onClick={this.selectAll.bind(this)} data-spm-click={"gostr=/aliyun;locaid=dashsearch"}>{window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.query')}</Button>	
                            </FormItem>	
                            <FormItem style={this.inApp ? { display: "none" } : { verticalAlign: "middle", marginTop: 0, marginLeft: 10 }}>	
                                <div style={{ color: '#33cde5', fontSize: 12, cursor: 'pointer' }} onClick={this.changeAdvancedQuery}>
                                    <span style={{ marginRight: 5, lineHeight: "28px" }}>{window.aliwareIntl.get("nacos.page.configurationManagement.advanced_query9") /*高级查询*/}</span><Icon type={this.state.isAdvancedQuery ? 'arrow-up-filling' : 'arrow-down-filling'} size={'xs'} />
                                </div>
                            </FormItem>
                            <br />
                            <FormItem style={this.inApp ? { display: "none" } : {}} label={window.aliwareIntl.get("nacos.page.configurationManagement.HOME_Application0") /*归属应用：*/}>	
                                <Input htmlType={"text"} placeholder={window.aliwareIntl.get("nacos.page.configurationManagement.Please_enter_the_name_of_the_app1") /*请输入应用名称*/} style={{ width: 200 }} value={this.state.appName} onChange={this.setAppName.bind(this)} />	
                            
                            </FormItem>	
                            <FormItem label={window.aliwareIntl.get('nacos.page.configurationManagement.Tags')}>	
                                <Select style={{ width: 200 }} size={"medium"} hasArrow multiple={true} mode="tag" filterLocal={false} placeholder={window.aliwareIntl.get('nacos.page.configurationManagement.Please_enter_tag')} dataSource={this.state.tagLst} value={this.state.config_tags} onChange={this.setConfigTags.bind(this)} hasClear language={window.aliwareIntl.currentLanguageCode} />	
                            </FormItem>	
                        </Form>	
                        <div style={{ position: 'absolute', right: 10, top: 4 }}>
                            <Icon type={"add"} size={'medium'} style={{ color: 'black', marginRight: 0, verticalAlign: 'middle', cursor: 'pointer', backgroundColor: '#eee', border: '1px solid #ddd', padding: '3px 6px' }} onClick={this.chooseEnv.bind(this)} />
                        </div>
                    </div>	
                    <div>	
        
                        <Table dataSource={this.state.dataSource} locale={locale} fixedHeader={true} maxBodyHeight={400} language={window.aliwareIntl.currentLanguageCode} ref={"dataTable"}>	
                            <Table.Column title={helpDataId} dataIndex={"dataId"} />	
                            <Table.Column title={helpGroup} dataIndex={"group"} />	
                            {!this.inApp ? <Table.Column title={window.aliwareIntl.get('nacos.page.configurationManagement.HOME_Application')} dataIndex={"appName"} /> : <div></div>}	
                            <Table.Column title={window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.operation')} cell={this.renderCol.bind(this)} />	
                        </Table>	
                        {this.state.dataSource.length > 0 && <div style={{ marginTop: 10, overflow: "hidden" }}>
                            <Pagination style={{ float: "right" }} pageSizeList={[10, 20, 30]} pageSizeSelector={"dropdown"} onPageSizeChange={this.handlePageSizeChange.bind(this)} current={this.state.currentPage} language={window.pageLanguage || 'zh-cn'} total={this.state.total} pageSize={this.state.pageSize} onChange={this.changePage.bind(this)} />	
                        </div>}	
                    </div>
                    <ShowCodeing ref={"showcode"} />	
                    <DeleteDialog ref={"delete"} />	
                    <CloneDialog ref={"cloneDialog"} />	
                    <ImportDialog ref={"importDialog"} />	
                    <ExportDialog ref={"exportDialog"} />	
                    </div>	
                    {this.state.hasdash ? <div className={"dash-right-container"} style={{ overflow: 'auto', height: window.innerHeight - 40 }}>	
                        {this.state.contentList.map((v, i) => {
                                return <DashboardCard data={v} height={'auto'} key={`show${i}`} />;
                            })}	
                    </div> : ''}
                    </div>	
                </Loading>	
            </div>
        );
    }

}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ConfigurationManagement;