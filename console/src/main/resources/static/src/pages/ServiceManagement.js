import React from 'react';
import RegionGroup from '../components/RegionGroup' ;
import {Button, Field, Form, Grid, Input, Loading, Pagination, Table} from '@alifd/next';

const FormItem = Form.Item;
const {Row, Col} = Grid;
const {Column} = Table

const getI18N = (key, prefix = 'com.alibaba.nacos.page.serviceManagement.') => window.aliwareIntl.get(prefix + key)
/**
 * 服务列表
 */
const I18N_SERVICE_LIST = getI18N('service_list')
/**
 * 服务名称
 */
const I18N_SERVICE_NAME = getI18N('service_name')
/**
 * 请输入服务名称
 */
const I18N_ENTER_SERVICE_NAME = getI18N('please_enter_the_service_name')
/**
 * 查询
 */
const I18N_QUERY = window.aliwareIntl.get('com.alibaba.nacos.page.serviceManagement.query')
/**
 * 查询
 */
const I18N_PUBNODEDATA = window.aliwareIntl.get('pubnodedata', '')

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ServiceManagement extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            total: 0,
            pageSize: 10,
            currentPage: 1,
            keyword: '',
            dataSource: []
        };
        this.field = new Field(this);
    }

    componentDidMount() {
    }

    openLoading() {
        this.setState({loading: true})
    }

    closeLoading() {
        this.setState({loading: false})
    }

    queryServiceList() {
        const {currentPage, pageSize, keyword} = this.state
        const parameter = [`startPg=${currentPage}`, `pgSize=${pageSize}`, `keyword=${keyword}`]
        window.request({
            url: `/nacos/v1/ns/catalog/serviceList?${parameter.join('&')}`,
            beforeSend: () => this.openLoading(),
            success: ({count = 0, serviceList = []} = {}) => this.setState({
                dataSource: serviceList,
                total: count
            }),
            error: () => this.setState({
                dataSource: [],
                total: 0,
                currentPage: 0
            }),
            complete: () => this.closeLoading()
        });
    }

    getQueryLater() {
        setTimeout(() => this.queryServiceList());
    }

    render() {
        const {keyword} = this.state
        const {init, getValue} = this.field;
        this.init = init;
        this.getValue = getValue;
        const locale = {empty: I18N_PUBNODEDATA}

        return (
            <div style={{padding: 10}}>
                <Loading
                    shape="flower"
                    style={{position: 'relative'}}
                    visible={this.state.loading}
                    tip="Loading..."
                    color="#333"
                >
                    <RegionGroup
                        left={I18N_SERVICE_LIST}
                        namespaceCallBack={this.getQueryLater.bind(this)}
                    />
                    <Row className="demo-row" style={{marginBottom: 10, padding: 0}}>
                        <Col span="24">
                            <Form inline field={this.field}>
                                <FormItem label={I18N_SERVICE_NAME}>
                                    <Input
                                        placeholder={I18N_ENTER_SERVICE_NAME}
                                        style={{width: 200}}
                                        value={keyword}
                                        onChange={keyword => this.setState({keyword})}
                                    />
                                </FormItem>
                                <FormItem label="">
                                    <Button
                                        type="primary"
                                        onClick={() => this.setState({currentPage: 1}, () => this.queryServiceList())}
                                        style={{marginRight: 10}}
                                    >{I18N_QUERY}</Button>
                                </FormItem>
                            </Form>
                        </Col>
                    </Row>
                    <Row style={{padding: 0}}>
                        <Col span="24" style={{padding: 0}}>
                            <Table
                                dataSource={this.state.dataSource}
                                fixedHeader={true}
                                maxBodyHeight={530}
                                locale={locale}
                                language={window.aliwareIntl.currentLanguageCode}
                                className={r => this.rowColor[r.status]}
                            >
                                <Column title="服务名" dataIndex="name"/>
                                <Column title="集群数目" dataIndex="clusterCount"/>
                                <Column title="实例数目" dataIndex="ipCount"/>
                                <Column title="健康程度" dataIndex="status"/>
                                <Column title="操作" align="center" cell={(value, index, record) => (
                                    <Button type="normal" disabled>详情</Button>
                                )}/>
                            </Table>
                        </Col>
                    </Row>
                    <div style={{marginTop: 10, textAlign: 'right'}}>
                        <Pagination
                            current={this.state.currentPage}
                            total={this.state.total}
                            pageSize={this.state.pageSize}
                            onChange={currentPage => this.setState({currentPage}, () => this.queryServiceList())}
                            language={window.pageLanguage}
                        />
                    </div>
                </Loading>
            </div>
        );
    }
}

/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ServiceManagement;
