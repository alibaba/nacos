import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alife/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;
import RegionGroup from '../components/RegionGroup' ;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class HistoryRollback extends React.Component {
    constructor(props) {
        super(props);

        this.field = new Field(this);
        this.appName = getParams('appName') || '';
        this.preAppName = this.appName;
        this.group = getParams('group') || '';
        this.preGroup = this.group;

        this.dataId = getParams('dataId') || '';
        this.preDataId = this.dataId;
        this.serverId = getParams('serverId') || '';
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
            selectValue: [],
            loading: false
        };
        let obj = {
            dataId: this.dataId || '',
            group: this.preGroup || '',
            appName: this.appName || '',
            serverId: this.serverId || ''
        };
        setParams(obj);
    }

    componentDidMount() {

        //this.getData()
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
    }
    getData(pageNo = 1) {
        let self = this;
        this.serverId = getParams('serverId') || '';
        if(!this.dataId) return false;
        request({
            beforeSend: function () {
                self.openLoading();
            },
            url: `/diamond-ops/historys/listData/serverId/${this.serverId}?dataId=${this.dataId}&group=${this.group}&&pageNo=${pageNo}&pageSize=${this.state.pageSize}`,
            success: function (data) {
                if (data.code === 200) {
                    self.setState({
                        dataSource: data.data || [],
                        total: data.total,
                        currentPage: pageNo
                    });
                }
            },
            complete: function () {
                self.closeLoading();
            }
        });
    }
    showMore() {}
    renderCol(value, index, record) {
        return <div>
            <a href="javascript:;" onClick={this.goDetail.bind(this, record)} style={{ marginRight: 5 }}>{aliwareIntl.get('com.alibaba.cspupcloud.page.historyRollback.details')}</a>
            <span style={{ marginRight: 5 }}>|</span>
            <a href="javascript:;" style={{ marginRight: 5 }} onClick={this.goRollBack.bind(this, record)}>{aliwareIntl.get('com.alibaba.cspupcloud.page.historyRollback.rollback')}</a>
        </div>;
    }
    changePage(value) {
        this.setState({
            currentPage: value
        });
        this.getData(value);
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
    getDataId(value) {
        this.dataId = value;
        this.setState({
            dataId: value
        });
    }
    getGroup(value) {

        this.group = value;
        this.setState({
            group: value
        });
    }
    selectAll() {
        if (this.dataId !== this.preDataId) {
            setParam('dataId', this.dataId);
            this.preDataId = this.dataId;
        }
        if (this.group !== this.preGroup) {
            setParam('group', this.preGroup);
            this.preGroup = this.group;
        }
        this.getData();
    }
    resetAll() {
        this.dataId = '';
        this.group = '';
        this.setState({
            selectValue: [],
            dataId: '',
            appName: '',
            group: '',
            showAppName: false,
            showgroup: false
        });
        setParams({
            group: '',
            dataId: ''
        });
    }
    chooseEnv(value) {
        console.log(value);
    }
    renderLastTime(value, index, record) {
        return aliwareIntl.intlTimeFormat(record.lastModifiedTime);
    }
    goDetail(record) {
        this.serverId = getParams('serverId') || 'center';
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        hashHistory.push(`/historyDetail?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${record.group}&nid=${record.id}&namespace=${this.tenant}`);
    }
    goRollBack(record) {
        this.serverId = getParams('serverId') || 'center';
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        hashHistory.push(`/configRollback?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${record.group}&nid=${record.id}&namespace=${this.tenant}&nid=${record.id}`);
    }
    render() {
        const { init, getValue } = this.field;
        const pubnodedata = aliwareIntl.get('pubnodata');

        const locale = {
            empty: pubnodedata
        };
        return (
            <div style={{ padding: 10 }}>
                <Loading shape="flower" style={{ position: 'relative' }} visible={this.state.loading} tip="Loading..." color="#333">
                <RegionGroup left={<h5 style={{ borderLeft: '2px solid rgb(136, 183, 224)', textIndent: 8, lineHeight: '32px', marginTop: 8, fontSize: '16px' }}>{aliwareIntl.get('com.alibaba.cspupcloud.page.historyRollback.to_configure')}</h5>} namespaceCallBack={this.cleanAndGetData.bind(this)} />
                    {/**<div className={'namespacewrapper'}>
                              <NameSpaceList namespaceCallBack={this.cleanAndGetData.bind(this)} />
                           </div>**/}
                    
                    <div>
                        <Form inline>

                            <FormItem label="Data ID:">
                                <Input htmlType="text" placeholder={aliwareIntl.get('com.alibaba.cspupcloud.page.historyRollback.dataid')} 
                                style={{ height: '32px', lineHeight: '32px' }} value={this.state.dataId} onChange={this.getDataId.bind(this)} />
                            </FormItem>
                            <FormItem label="Group:">
                                <Input placeholder={aliwareIntl.get('com.alibaba.cspupcloud.page.historyRollback.group')} id="userName" name="userName" value={this.state.group} 
                                style={{ height: 30, lineHeight: '30px' }} onChange={this.getGroup.bind(this)} />
                            </FormItem>

                            <FormItem label="">
                                <Button type="primary" style={{ marginRight: 10 }} onClick={this.selectAll.bind(this)}>
                                {aliwareIntl.get('com.alibaba.cspupcloud.page.historyrollback.query')}</Button>
                              {} 

                            </FormItem>

                        </Form>


                    </div>
                    <div style={{ position: 'relative', width: '100%', overflow: 'hidden', height: '40px' }}>

                        <h3 style={{ height: 30, width: '100%', lineHeight: '30px', padding: 0, margin: 0, paddingLeft: 10, borderLeft: '3px solid #09c' }}>{aliwareIntl.get('com.alibaba.cspupcloud.page.historyRollback.queryresult')}<strong style={{ fontWeight: 'bold' }}> {this.state.total} </strong>{aliwareIntl.get('com.alibaba.cspupcloud.page.historyRollback.article_meet')}</h3>

                    </div>
                    <div>

                        <Table dataSource={this.state.dataSource} locale={locale} language={aliwareIntl.currentLanguageCode}>
                            <Table.Column title="Data ID" dataIndex="dataId" />
                            <Table.Column title="Group" dataIndex="group" />
                            <Table.Column title={aliwareIntl.get('com.alibaba.cspupcloud.page.historyRollback.last_update_time')} dataIndex="time" cell={this.renderLastTime.bind(this)} />
                            <Table.Column title={aliwareIntl.get('com.alibaba.cspupcloud.page.historyRollback.operation')} cell={this.renderCol.bind(this)} />
                        </Table>

                    </div>
                    <div style={{ marginTop: 10, textAlign: 'right' }}>
                        <Pagination current={this.state.currentPage} language={window.pageLanguage} total={this.state.total} pageSize={this.state.pageSize} onChange={this.changePage.bind(this)} />,
                </div>
                </Loading>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default HistoryRollback;