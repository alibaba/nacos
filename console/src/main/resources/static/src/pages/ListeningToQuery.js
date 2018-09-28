import React from 'react'; 
import RegionGroup from '../components/RegionGroup' ;
import { Button, Field, Form, Grid, Input, Loading, Pagination, Select, Table } from '@alifd/next';
const FormItem = Form.Item; 
const { Row, Col } = Grid; 

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ListeningToQuery extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            value: "",
            visible: false,
            loading: false,
            total: 0,
            pageSize: 10,
            currentPage: 1,
            dataSource: []
        };
        this.field = new Field(this);
        this.group = window.getParams('group') || '';
        this.dataId = window.getParams('dataId') || '';
        this.serverId = window.getParams('serverId') || '';
        this.tenant = window.getParams('namespace') || '';
    }

    componentDidMount() {
        this.field.setValue('type', 0);
        this.field.setValue('group', this.group);
        this.field.setValue('dataId', this.dataId);
    }

    onSearch() {}

    onChange() {}
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
    queryTrackQuery() {
        var self = this;
        var queryUrl = "";
        var type = this.getValue('type');
        if (type === '1') {
            var ip = this.getValue('ip');
            queryUrl = `/nacos/v1/cs/listener?ip=${ip}`;
//            queryUrl = `/diamond-ops/configList/listenerByIp/serverId/${serverId}?ip=${ip}&tenant=${tenant}`;
        } else {
            var dataId = this.getValue('dataId');
            var group = this.getValue('group');
            if(!dataId) return false;
            queryUrl = `/nacos/v1/cs/configs/listener?dataId=${dataId}&group=${group}`;
//            queryUrl = `/diamond-ops/configList/listenerIp/serverId/${serverId}?dataId=${dataId}&group=${group}`;
        }
        window.request({
            url: queryUrl,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (data) {
                if (data.collectStatus === 200) {
                	let dataSoureTmp = [];
                	let status = data.lisentersGroupkeyStatus;
                	for (var key in status) {
                		if (type === '1') {
                			let obj = {};
                            obj.dataId = key.split("+")[0];
                            obj.group = key.split("+")[1];
                            obj.md5 = status[key];
                            dataSoureTmp.push(obj);
                    	} else {
                    		let obj = {};
                            obj.ip = key;
                            obj.md5 = status[key];
                            dataSoureTmp.push(obj);
                    	}
                    }
                	self.setState({
                        dataSource: dataSoureTmp || [],
                        total: data.length
                    });
                }
            },
            complete: function () {
                self.closeLoading();
            }
        });
    }

    showMore() {}
    changePage(value) {
        this.setState({
            currentPage: value
        });
    }
    resetSearch() {
        this.field.reset();
        this.forceUpdate();
    }
    renderStatus(values, index, record) {
        return <div>{record.pushStatus === true ? <span style={{ color: 'green' }}>{window.aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.success')}</span> : <span style={{ color: 'red' }}>{window.aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.failure')}</span>}
        </div>;
    }
    getQueryLater() {
        setTimeout(() => {
            //子组件可能优先于父组件所以延迟执行
            this.queryTrackQuery();
        });
    }
    render() {
        const { init, getValue } = this.field;
        this.init = init;
        this.getValue = getValue;
        const pubnodedata = window.aliwareIntl.get('pubnodata');

        const locale = {
            empty: pubnodedata
        };
        const selectDataSource = [
            {
                label: window.aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.configuration'),
                value: 0
            },
            {
                label: "IP",
                value: 1
            }
        ]
        return (
            <div style={{ padding: 10 }}>
                <Loading shape="flower" style={{ position: 'relative' }} visible={this.state.loading} tip="Loading..." color="#333">
                    <RegionGroup left={window.aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.listener_query')} namespaceCallBack={this.getQueryLater.bind(this)} />
                    {/**<div className={'namespacewrapper'}>
                              <NameSpaceList namespaceCallBack={this.getQueryLater.bind(this)} />
                           </div>**/}
                    <Row className="demo-row" style={{ marginBottom: 10, padding: 0 }}>
                        <Col span="24">
                            <Form inline field={this.field}>
                                <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.query_dimension')} initValue="0">
                                    <Select dataSource={selectDataSource} style={{ width: 200 }} {...this.init('type')} language={window.aliwareIntl.currentLanguageCode} />
                                </FormItem>
                                <FormItem label="Data ID:" style={{
                                    display: this.getValue('type') === 0 ? '' : 'none'
                                }}>
                                    <Input placeholder={window.aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.please_enter_the_dataid')} style={{ width: 200 }} {...this.init('dataId')} />
                                </FormItem>
                                <FormItem label="Group:" style={{
                                    display: this.getValue('type') === 0 ? '' : 'none'
                                }}>
                                    <Input placeholder={window.aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.please_input_group')} style={{ width: 200 }} {...this.init('group')} />
                                </FormItem>
                                <FormItem label="IP:" style={{
                                    display: this.getValue('type') === 0 ? 'none' : ''
                                }}>
                                    <Input placeholder={window.aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.please_input_ip')} style={{ width: 200, boxSize: 'border-box' }} {...this.init('ip')} />
                                </FormItem>
                                <FormItem label="">
                                    <Button type="primary" onClick={this.queryTrackQuery.bind(this)} style={{ marginRight: 10 }}>{window.aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.query')}</Button>
                                   {}
                                </FormItem>
                            </Form>
                        </Col>
                    </Row>
                    <div style={{ position: 'relative' }}>
                        <h3 style={{ height: 28, lineHeight: '28px', paddingLeft: 10, borderLeft: '3px solid #09c' }}>{window.aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.query_results:_query')}<strong style={{ fontWeight: 'bold' }}> {this.state.total} </strong>{window.aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.article_meet_the_requirements_of_the_configuration.')}</h3>
                    </div>
                    <Row style={{ padding: 0 }}>
                        <Col span="24" style={{ padding: 0 }}>
                            {this.getValue('type') ==='1' ? <Table dataSource={this.state.dataSource} fixedHeader={true} maxBodyHeight={500} locale={locale} language={window.aliwareIntl.currentLanguageCode}>
                                <Table.Column title="Data ID" dataIndex="dataId" />
                                <Table.Column title="Group" dataIndex="group" />
                                <Table.Column title="MD5" dataIndex="md5" />
                            </Table> : <Table dataSource={this.state.dataSource} fixedHeader={true} maxBodyHeight={400} locale={locale} language={window.aliwareIntl.currentLanguageCode}>
                                    <Table.Column title="IP" dataIndex="ip" />
                                    <Table.Column title="MD5" dataIndex="md5" />
                                </Table>}
                        </Col>
                    </Row>
                    <div style={{ marginTop: 10, textAlign: 'right' }}>
                        <Pagination current={this.state.currentPage} total={this.state.total} pageSize={this.state.pageSize} onChange={this.changePage.bind(this)} language={window.pageLanguage} />,
                </div>
                </Loading>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ListeningToQuery;