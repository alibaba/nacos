import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alifd/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ConsistencyEfficacy extends React.Component {
    constructor(props) {
        super(props);

        this.field = new Field(this);
        this.appName = getParams('appName') || '';
        this.preAppName = this.appName;
        this.group = getParams('group') || 'DEFAULT_GROUP';
        this.preGroup = this.group;

        this.dataId = getParams('dataId') || '';
        this.preDataId = this.dataId;
        this.serverId = getParams('serverId') || 'center';
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
            selectValue: []
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

        this.getData();
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

    getData(pageNo = 1) {
        let self = this;
        request({
            url: `/diamond-ops/configList/consistency/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}`,
            success: function (data) {
                if (data.code === 200) {
                    var arr = new Array();
                    for (var key in data.data) {
                        for (var subKey in data.data[key]) {
                            arr.push(data.data[key][subKey]);
                        }
                    }
                    self.setState({
                        dataSource: arr,
                        total: data.total,
                        currentPage: pageNo
                    });
                }
            }
        });
    }
    showMore() {}
    renderCol(value, index, record) {
        return <div>
            <a href="javascript:;" onClick={this.goDetail.bind(this, record)} style={{ marginRight: 5 }}>{aliwareIntl.get('com.alibaba.cspupcloud.page.consistencyEfficacy.details')}</a>
            <span style={{ marginRight: 5 }}>|</span>
            <a href="javascript:;" style={{ marginRight: 5 }}>{aliwareIntl.get('com.alibaba.cspupcloud.page.consistencyEfficacy.edit')}</a>
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
    getgroup(value) {

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
        if (this.appName !== this.preAppName) {
            setParam('appName', this.appName);
            this.preAppName = this.appName;
        }
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
    chooseEnv(value) {}
    goDetail(record) {
        hashHistory.push(`/configdetail?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${record.group}`);
    }
    render() {
        const { init, getValue } = this.field;
        const pubnodedata = aliwareIntl.get('pubnodata');

        const locale = {
            empty: pubnodedata
        };
        return (
            <div style={{ padding: 10 }}>
                <h1>{aliwareIntl.get('com.alibaba.cspupcloud.page.consistencyEfficacy.configuration_consistency_check')}</h1>
                <div>
                    <Form inline>
                        <FormItem label="Data ID:">
                            <Input htmlType="text" placeholder="" style={{ height: 32 }} value={this.state.dataId} onChange={this.getDataId.bind(this)} />
                        </FormItem>
                        {this.state.showgroup ? <FormItem label="Group ID:">
                            <Input placeholder="" id="userName" name="userName" value={this.state.group} onChange={this.getgroup.bind(this)} />
                        </FormItem> : ''}

                        <FormItem label="">
                            <Button type="primary" style={{ marginRight: 10 }} onClick={this.selectAll.bind(this)}>{aliwareIntl.get('com.alibaba.cspupcloud.page.consistencyEfficacy.query')}</Button>
                            <Button type="light" style={{ marginRight: 2 }} onClick={this.resetAll.bind(this)}>{aliwareIntl.get('com.alibaba.cspupcloud.page.consistencyEfficacy.reset')}</Button>

                        </FormItem>
                        <Select value={this.state.selectValue} onChange={this.showSelect.bind(this)} placeholder={aliwareIntl.get('com.alibaba.cspupcloud.page.consistencyEfficacy.advanced_query')} mode="multiple" style={{ display: 'inline-block' }} language={aliwareIntl.currentLanguageCode}>
                            <Select.Option value="group">group Id</Select.Option>
                        </Select>
                    </Form>


                </div>
                <div style={{ position: 'relative', width: '100%', overflow: 'hidden', height: '40px' }}>
                    <h3 style={{ height: 30, width: '100%', lineHeight: '30px', padding: 0, margin: 0, paddingLeft: 10, borderLeft: '3px solid #09c' }}>{aliwareIntl.get('com.alibaba.cspupcloud.page.consistencyEfficacy.query_results')}</h3>

                </div>
                <div>

                    <Table dataSource={this.state.dataSource} locale={locale} language={aliwareIntl.currentLanguageCode}>
                        <Table.Column title={aliwareIntl.get('com.alibaba.cspupcloud.page.consistencyEfficacy.environment_name')} dataIndex="showName" />
                        {this.state.fieldValue.map((value, index) => {
                            return <Table.Column title={value} dataIndex={value} />;
                        })}
                        <Table.Column title={aliwareIntl.get('com.alibaba.cspupcloud.page.consistencyEfficacy.environment_marked')} dataIndex="serverId" />
                        <Table.Column title={aliwareIntl.get('com.alibaba.cspupcloud.page.consistencyEfficacy.configuration_content_md5')} dataIndex="md5" />
                        <Table.Column title={aliwareIntl.get('com.alibaba.cspupcloud.page.consistencyEfficacy.operation')} cell={this.renderCol.bind(this)} />
                    </Table>
                </div>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ConsistencyEfficacy;