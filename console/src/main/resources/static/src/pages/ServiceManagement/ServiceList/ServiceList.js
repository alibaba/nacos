/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import RegionGroup from '../../../components/RegionGroup/index' ;
import {Button, Field, Form, Grid, Input, Loading, Pagination, Table} from '@alifd/next';
import {I18N, STATUS_COLOR_MAPPING} from './constant'
import './ServiceList.less'

const FormItem = Form.Item;
const {Row, Col} = Grid;
const {Column} = Table

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ServiceList extends React.Component {
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

    rowColor = ({status}) => ({className: `row-bg-${STATUS_COLOR_MAPPING[status]}`})

    render() {
        const {keyword} = this.state
        const {init, getValue} = this.field;
        this.init = init;
        this.getValue = getValue;
        const locale = {empty: I18N.PUBNODEDATA}

        return (
            <div className="main-container service-management">
                <Loading
                    shape="flower"
                    style={{position: 'relative'}}
                    visible={this.state.loading}
                    tip="Loading..."
                    color="#333"
                >
                    <RegionGroup
                        left={I18N.SERVICE_LIST}
                        namespaceCallBack={this.getQueryLater.bind(this)}
                    />
                    <Row className="demo-row" style={{marginBottom: 10, padding: 0}}>
                        <Col span="24">
                            <Form inline field={this.field}>
                                <FormItem label={I18N.SERVICE_NAME}>
                                    <Input
                                        placeholder={I18N.ENTER_SERVICE_NAME}
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
                                    >{I18N.QUERY}</Button>
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
                                getRowProps={this.rowColor}
                            >
                                <Column title={I18N.COLUMN_SERVICE_NAME} dataIndex="name"/>
                                <Column title={I18N.COLUMN_CLUSTER_COUNT} dataIndex="clusterCount"/>
                                <Column title={I18N.COLUMN_IP_COUNT} dataIndex="ipCount"/>
                                <Column title={I18N.COLUMN_HEALTH_STATUS} dataIndex="status"/>
                                <Column title={I18N.COLUMN_OPERATION} align="center" cell={(value, index, record) => (
                                    <Button
                                        type="normal"
                                        onClick={() => this.props.history.push(`/serviceDetail?name=${record.name}`)}
                                    >{I18N.DETAIL}</Button>
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
export default ServiceList;
