import React from 'react'; 
import { Button, Field, Form, Grid, Input, Loading, Pagination, Select, Table } from '@alifd/next';
const FormItem = Form.Item; 
const { Row, Col } = Grid; 
import RegionGroup from '../components/RegionGroup' ;

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
        this.group = getParams('group') || '';
        this.dataId = getParams('dataId') || '';
        this.serverId = getParams('serverId') || '';
        this.tenant = getParams('namespace') || '';
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
        var tenant = getParams('namespace') || '';
        var serverId = getParams('serverId') || '';
        if (this.getValue('type') == '1') {
            var ip = this.getValue('ip');
            queryUrl = `/diamond-ops/configList/listenerByIp/serverId/${serverId}?ip=${ip}&tenant=${tenant}`;
        } else {
            var dataId = this.getValue('dataId');
            var group = this.getValue('group');
            if(!dataId) return false;
            queryUrl = `/diamond-ops/configList/listenerIp/serverId/${serverId}?dataId=${dataId}&group=${group}`;
        }
        request({
            url: queryUrl,
            beforeSend: function () {
                self.openLoading();
            },
            success: function (data) {
                if (data.code === 200) {
                    // if (this.getValue('type') != '0') {
                    self.setState({
                        dataSource: data.data || [],
                        total: data.total
                        // } else {
                        //     self.setState({
                        //         dataSource: data.data,
                        //         total: data.total
                        //     })
                        // }

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
        return <div>{record.pushStatus === true ? <span style={{ color: 'green' }}>{aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.success')}</span> : <span style={{ color: 'red' }}>{aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.failure')}</span>}
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
        const pubnodedata = aliwareIntl.get('pubnodata');

        const locale = {
            empty: pubnodedata
        };
        const selectDataSource = [
            {
                label: aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.configuration'),
                value: "0"
            },
            {
                label: "IP",
                value: "1"
            }
        ]
        return (
            <div style={{ padding: 10 }}>
                <Loading shape="flower" style={{ position: 'relative' }} visible={this.state.loading} tip="Loading..." color="#333">
                    <RegionGroup left={aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.listener_query')} namespaceCallBack={this.getQueryLater.bind(this)} />
                    {/**<div className={'namespacewrapper'}>
                              <NameSpaceList namespaceCallBack={this.getQueryLater.bind(this)} />
                           </div>**/}
                    <Row className="demo-row" style={{ marginBottom: 10, padding: 0 }}>
                        <Col span="24">
                            <Form inline field={this.field}>
                                <FormItem label={aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.query_dimension')} initValue="0">
                                    <Select dataSource={selectDataSource} style={{ width: '100%' }} {...this.init('type')} language={aliwareIntl.currentLanguageCode} />
                                </FormItem>
                                <FormItem label="Data ID:" style={{
                                    display: this.getValue('type') == '0' ? '' : 'none'
                                }}>
                                    <Input placeholder={aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.please_enter_the_dataid')} style={{ height: '32px', lineHeight: '32px' }} {...this.init('dataId')} />
                                </FormItem>
                                <FormItem label="Group:" style={{
                                    display: this.getValue('type') == '0' ? '' : 'none'
                                }}>
                                    <Input placeholder={aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.please_input_group')} style={{ height: '32px', lineHeight: '32px' }} {...this.init('group')} />
                                </FormItem>
                                <FormItem label="IP:" style={{
                                    display: this.getValue('type') == '0' ? 'none' : ''
                                }}>
                                    <Input placeholder={aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.please_input_ip')} style={{ height: '32px', lineHeight: '32px', boxSize: 'border-box' }} {...this.init('ip')} />
                                </FormItem>
                                <FormItem label="">
                                    <Button type="primary" onClick={this.queryTrackQuery.bind(this)} style={{ marginRight: 10 }}>{aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.query')}</Button>
                                   {}
                                </FormItem>
                            </Form>
                        </Col>
                    </Row>
                    <div style={{ position: 'relative' }}>
                        <h3 style={{ height: 28, lineHeight: '28px', paddingLeft: 10, borderLeft: '3px solid #09c' }}>{aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.query_results:_query')}<strong style={{ fontWeight: 'bold' }}> {this.state.total} </strong>{aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery.article_meet_the_requirements_of_the_configuration.')}</h3>
                    </div>
                    <Row style={{ padding: 0 }}>
                        <Col span="24" style={{ padding: 0 }}>
                            {this.getValue('type') == '1' ? <Table dataSource={this.state.dataSource} fixedHeader={true} maxBodyHeight={500} locale={locale} language={aliwareIntl.currentLanguageCode}>
                                <Table.Column title="Data ID" dataIndex="dataId" />
                                <Table.Column title="Group" dataIndex="group" />
                            </Table> : <Table dataSource={this.state.dataSource} fixedHeader={true} maxBodyHeight={400} locale={locale} language={aliwareIntl.currentLanguageCode}>
                                    <Table.Column title="IP" dataIndex="ip" />
                                    <Table.Column title={aliwareIntl.get('com.alibaba.nacos.page.listeningToQuery._Push_state')} dataIndex="pushStatus" cell={this.renderStatus.bind(this)} />
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