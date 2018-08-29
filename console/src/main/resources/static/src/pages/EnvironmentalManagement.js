import React from 'react'; 
import { Dialog, Field, Table } from '@alifd/next';

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class EnvironmentalManagement extends React.Component {

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
            ipDialog: {
                visible: false,
                unitIps: []
            },
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
            url: `/diamond-ops/env`,
            success: function (data) {
                if (data.code === 200) {
                    var arr = new Array();
                    for (var key in data.data.units) {
                        arr.push(data.data.units[key]);
                    }
                    self.setState({
                        dataSource: arr
                    });
                }
            }
        });
    }
    showMore() {}
    renderCol(value, index, record) {
        return <div>
            <a href="javascript:;" onClick={this.goDetail.bind(this, record)} style={{ marginRight: 5 }}>{aliwareIntl.get('com.alibaba.cspupcloud.page.environmentalManagement.Into_the')}</a>
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

    chooseEnv(value) {
        console.log(value);
    }

    goDetail(record) {
        hashHistory.push(`/configurationManagement?serverId=${record.unitId}`);
    }
    enderShowIpCol(value, index, record) {
        return <div>
            <a href="javascript:;" onClick={this.showIp.bind(this, value, index, record)} style={{ marginRight: 5 }}>{aliwareIntl.get('com.alibaba.cspupcloud.page.environmentalManagement.view')}</a>
        </div>;
    }
    showIp(value, index, record) {
        this.setState({
            ipDialog: {
                visible: true,
                unitIps: record.unitIps,
                value: record
            }
        });
    }

    render() {
        const { init, getValue } = this.field;
        const pubnodedata = aliwareIntl.get('pubnodata');

        const locale = {
            empty: pubnodedata
        };
        return <div style={{ padding: 10 }}>
            <Dialog visible={this.state.ipDialog.visible} onOk={() => {
                this.setState({
                    ipDialog: {
                        visible: false,
                        unitIps: []
                    }
                });
            }} onCancel={() => {
                this.setState({
                    ipDialog: {
                        visible: false,
                        unitIps: []
                    }
                });
            }} onClose={() => {
                this.setState({
                    ipDialog: {
                        visible: false,
                        unitIps: []
                    }
                });
            }} title={aliwareIntl.get('com.alibaba.cspupcloud.page.environmentalManagement.view_environment_IP')} language={window.pageLanguage || 'zh-cn'}>
                <ul>

                    {this.state.ipDialog.unitIps.map(v => {
                        return <div dangerouslySetInnerHTML={{ __html: "<li>" + v + "</li>" }} />;
                    })}

                </ul>
            </Dialog>
            <h1>{aliwareIntl.get('com.alibaba.cspupcloud.page.environmentalManagement.all_available_environment')}</h1>
            <div>
                <Table dataSource={this.state.dataSource} local={local} language={aliwareIntl.currentLanguageCode}>
                    <Table.Column title={aliwareIntl.get('com.alibaba.cspupcloud.page.environmentalManagement.environment_name')} dataIndex="showName" />
                    {this.state.fieldValue.map((value, index) => {
                        return <Table.Column title={value} dataIndex={value} />;
                    })}

                    <Table.Column title={aliwareIntl.get('com.alibaba.cspupcloud.page.environmentalManagement.environment_marked')} dataIndex="unitId" />
                    <Table.Column title={aliwareIntl.get('com.alibaba.cspupcloud.page.environmentalManagement.environment_ip')} cell={this.enderShowIpCol.bind(this)} />
                    <Table.Column title={aliwareIntl.get('com.alibaba.cspupcloud.page.environmentalManagement.operation')} cell={this.renderCol.bind(this)} />
                </Table>
            </div>
        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default EnvironmentalManagement;