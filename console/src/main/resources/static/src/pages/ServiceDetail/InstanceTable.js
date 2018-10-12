import React from 'react';
import {Button, Pagination, Table} from '@alifd/next';
import {I18N} from './constant'
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
            beforeSend: () => this.setState({loading: true}),
            success: res => this.setState({instance: res}),
            complete: () => this.setState({loading: false})
        })
    }

    openInstanceDialog(instance) {
        this.refs.editInstanceDialog.show(instance)
    }

    switchState() {
    }

    render() {
        const {clusterName} = this.props
        const {instance = {}, pageSize, loading} = this.state
        return instance.count ? (
            <div>
                <Table dataSource={instance.list} loading={loading}>
                    <Table.Column title="IP" dataIndex="ip"/>
                    <Table.Column title={I18N.PORT} dataIndex="port"/>
                    <Table.Column title={I18N.WEIGHT} dataIndex="weight"/>
                    <Table.Column title={I18N.HEALTHY} dataIndex="healthy"/>
                    <Table.Column title={I18N.METADATA} dataIndex="metadata"/>
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
                                    type={record.online ? 'normal' : 'secondary'}
                                    onClick={() => this.switchState(index, record)}
                                >{I18N[record.online ? 'OFFLINE' : 'ONLINE']}</Button>
                            </div>
                        )}/>
                </Table>
                {
                    instance.count > pageSize
                        ? (
                            <Pagination
                                className="pagination"
                                onChange={currentPage => this.onChange(clusterName, currentPage)}
                            />
                        )
                        : null
                }
                <EditInstanceDialog ref="editInstanceDialog"/>
            </div>
        ) : null
    }
}

/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default InstanceTable;
