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
import {Button, Pagination, Table} from '@alifd/next';
import {I18N, HEALTHY_COLOR_MAPPING} from './constant'
import EditInstanceDialog from "./EditInstanceDialog";


/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class InstanceTable extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            instance: {count: 0, list: []},
            pageNum: 1,
            pageSize: 10
        }
    }

    componentDidMount() {
        this.getInstanceList()
    }

    openLoading() {
        this.setState({loading: true})
    }

    closeLoading() {
        this.setState({loading: false})
    }

    getInstanceList() {
        const {clusterName, serviceName} = this.props
        if (!clusterName) return
        const {pageSize, pageNum} = this.state
        window.request({
            url: '/nacos/v1/ns/catalog/instanceList',
            data: {
                serviceName,
                clusterName,
                pgSize: pageSize,
                startPg: pageNum
            },
            beforeSend: () => this.openLoading(),
            success: instance => this.setState({instance}),
            complete: () => this.closeLoading()
        })
    }

    openInstanceDialog(instance) {
        this.refs.editInstanceDialog.show(instance)
    }

    switchState(index, record) {
        const {instance} = this.state
        const {ip, port, weight, enabled} = record
        const {clusterName, serviceName} = this.props
        const newVal = Object.assign({}, instance)
        newVal.list[index]['enabled'] = !enabled
        window.request({
            method: 'POST',
            url: '/nacos/v1/ns/instance/update',
            data: {serviceName, clusterName, ip, port, weight, enable: !enabled},
            dataType: 'text',
            beforeSend: () => this.openLoading(),
            success: () => this.setState({instance: newVal}),
            complete: () => this.closeLoading()
        })
    }

    onChangePage(pageNum) {
        this.setState({pageNum}, () => this.getInstanceList())
    }

    rowColor = ({healthy}) => ({className: `row-bg-${HEALTHY_COLOR_MAPPING[`${healthy}`]}`})

    render() {
        const {clusterName, serviceName} = this.props
        const {instance, pageSize, loading} = this.state
        return instance.count ? (
            <div>
                <Table dataSource={instance.list} loading={loading} getRowProps={this.rowColor}>
                    <Table.Column width={138} title="IP" dataIndex="ip"/>
                    <Table.Column width={100} title={I18N.PORT} dataIndex="port"/>
                    <Table.Column width={100} title={I18N.WEIGHT} dataIndex="weight"/>
                    <Table.Column width={100} title={I18N.HEALTHY} dataIndex="healthy" cell={val => `${val}`}/>
                    <Table.Column
                        title={I18N.METADATA}
                        dataIndex="metadata"
                        cell={metadata => Object.keys(metadata).map(k => `${k}=${metadata[k]}`).join(',')}
                    />
                    <Table.Column
                        title={I18N.OPERATION}
                        width={150}
                        cell={(value, index, record) => (
                            <div>
                                <Button
                                    type="normal"
                                    className="edit-btn"
                                    onClick={() => this.openInstanceDialog(record)}
                                >{I18N.EDITOR}</Button>
                                <Button
                                    type={record.enabled ? 'normal' : 'secondary'}
                                    onClick={() => this.switchState(index, record)}
                                >{I18N[record.enabled ? 'OFFLINE' : 'ONLINE']}</Button>
                            </div>
                        )}/>
                </Table>
                {
                    instance.count > pageSize
                        ? (
                            <Pagination
                                className="pagination"
                                total={instance.count}
                                pageSize={pageSize}
                                onChange={currentPage => this.onChangePage(currentPage)}
                            />
                        )
                        : null
                }
                <EditInstanceDialog
                    ref="editInstanceDialog"
                    serviceName={serviceName}
                    clusterName={clusterName}
                    openLoading={() => this.openLoading()}
                    closeLoading={() => this.closeLoading()}
                    getInstanceList={() => this.getInstanceList()}
                />
            </div>
        ) : null
    }
}

/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default InstanceTable;
