import React from 'react';
import {Button, Card, Dialog, Table, Form, Pagination, Loading, Input, Switch, Select} from '@alifd/next';
import {I18N} from './constant'
import './ServiceDetail.less'

const FormItem = Form.Item;
const Option = Select.Option

const dataSource = () => {
    const result = [];
    for (let i = 0; i < 8; i++) {
        result.push({ip: '1.1.1.1', port: '80', weight: '50', healthy: 'true', metadata: 'k1=v1', online: true});
    }
    return result;
};

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ServiceDetail extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            tableLoading: false,
            currentPage: 1,
            instanceList: [],
            editServiceDialogVisible: false,
            editClusterDialogVisible: false,
            editInstanceDialogVisible: false,
            checkType: 'tcp'
        }
    }

    componentDidMount() {
        setTimeout(() => this.setState({loading: true}, () => {
            this.setState({
                instanceList: dataSource(),
                loading: false
            })
        }), 300)
    }

    openLoading() {
        this.setState({loading: true})
    }

    closeLoading() {
        this.setState({loading: false})
    }

    editServiceDialog() {
        const hideDialog = () => this.setState({editServiceDialogVisible: false})
        const {editServiceDialogVisible} = this.state
        return (
            <Dialog
                className="service-detail-edit-dialog"
                title={I18N.UPDATE_SERVICE}
                visible={editServiceDialogVisible}
                onOk={hideDialog}
                onCancel={hideDialog}
                onClose={hideDialog}
            >
                <Form
                    labelCol={{fixedSpan: 8}}
                    wrapperCol={{span: 16}}
                >
                    <FormItem label={`${I18N.SERVICE_NAME}:`}>
                        <p>test.com</p>
                    </FormItem>
                    <FormItem label={`${I18N.PROTECT_THRESHOLD}:`}>
                        <Input className="in-text" value="0.5"/>
                    </FormItem>
                    <FormItem label={`${I18N.HEALTH_CHECK_PATTERN}:`}>
                        <Select defaultValue="service">
                            <Option value="service">{I18N.HEALTH_CHECK_PATTERN_SERVICE}</Option>
                            <Option value="client">{I18N.HEALTH_CHECK_PATTERN_CLIENT}</Option>
                            <Option value="forbidden">{I18N.HEALTH_CHECK_PATTERN_FORBIDDEN}</Option>
                        </Select>
                    </FormItem>
                    <FormItem label={`${I18N.METADATA}:`}>
                        <Input className="in-text" value="k1=v1,k2=v2"/>
                    </FormItem>
                </Form>
            </Dialog>
        )
    }

    editClusterDialog() {
        const hideDialog = () => this.setState({editClusterDialogVisible: false})
        const {editClusterDialogVisible} = this.state
        const formInit = {labelCol: {fixedSpan: 6}, wrapperCol: {span: 18}}
        return (
            <Dialog
                className="cluster-edit-dialog"
                title={I18N.UPDATE_CLUSTER}
                visible={editClusterDialogVisible}
                onOk={hideDialog}
                onCancel={hideDialog}
                onClose={hideDialog}
            >
                <Form {...formInit}>
                    <FormItem label={`${I18N.CHECK_TYPE}:`}>
                        <Select
                            className="in-select"
                            defaultValue={this.state.checkType}
                            onChange={checkType => this.setState({checkType})}
                        >
                            <Select.Option value="tcp">TCP</Select.Option>
                            <Select.Option value="http">HTTP</Select.Option>
                        </Select>
                    </FormItem>
                    <FormItem label={`${I18N.CHECK_PORT}:`}>
                        <Input className="in-text" value="80"/>
                    </FormItem>
                    <FormItem label={`${I18N.USE_IP_PORT_CHECK}:`}>
                        <Switch onChange={f => f}/>
                    </FormItem>
                    {
                        this.state.checkType === 'http'
                            ? (
                                <Form {...formInit}>
                                    <FormItem label={`${I18N.CHECK_PATH}:`}>
                                        <Input className="in-text"/>
                                    </FormItem>
                                    <FormItem label={`${I18N.CHECK_HEADERS}:`}>
                                        <Input className="in-text"/>
                                    </FormItem>
                                </Form>
                            )
                            : null
                    }
                    <FormItem label={`${I18N.METADATA}:`}>
                        <Input className="in-text" value="k1=v1,k2=v2"/>
                    </FormItem>
                </Form>
            </Dialog>
        )
    }

    editInstanceDialog() {
        const hideDialog = () => this.setState({editInstanceDialogVisible: false})
        const {editInstanceDialogVisible} = this.state
        return (
            <Dialog
                className="instance-edit-dialog"
                title={I18N.UPDATE_INSTANCE}
                visible={editInstanceDialogVisible}
                onOk={hideDialog}
                onCancel={hideDialog}
                onClose={hideDialog}
            >
                <Form
                    labelCol={{fixedSpan: 6}}
                    wrapperCol={{span: 18}}
                >
                    <FormItem label="IP:">
                        <p>1.1.1.1</p>
                    </FormItem>
                    <FormItem label={`${I18N.PORT}:`}>
                        <p>8080</p>
                    </FormItem>
                    <FormItem label={`${I18N.WEIGHT}:`}>
                        <Input className="in-text" value="0.5"/>
                    </FormItem>
                    <FormItem label={`${I18N.WHETHER_ONLINE}:`}>
                        <Switch onChange={f => f}/>
                    </FormItem>
                    <FormItem label={`${I18N.METADATA}:`}>
                        <Input className="in-text" value="k1=v1,k2=v2"/>
                    </FormItem>
                </Form>
            </Dialog>
        )
    }


    switchState(index, record) {
        const {instanceList} = this.state
        this.setState({tableLoading: true}, () => {
            setTimeout(() => {
                instanceList[index].online = !record.online
                this.setState({
                    instanceList,
                    tableLoading: false
                })
            }, 300)
        })
    }

    onChange(currentPage) {
        this.setState({tableLoading: true})
        setTimeout(() => {
            this.setState({tableLoading: false, currentPage})
        }, 200)
    }

    openEditServiceDialog = () => this.setState({editServiceDialogVisible: true})
    openClusterDialog = () => this.setState({editClusterDialogVisible: true})
    openInstanceDialog = () => this.setState({editInstanceDialogVisible: true})

    render() {
        const {loading, tableLoading, instanceList} = this.state
        const formItemLayout = {
            labelCol: {fixedSpan: 10},
            wrapperCol: {span: 14}
        };
        return (
            <div className="main-container service-detail">
                <Loading
                    shape={"flower"}
                    tip={"Loading..."}
                    className="loading"
                    visible={loading} color={"#333"}
                >
                    <h1 style={{
                        position: 'relative',
                        width: '100%'
                    }}>
                        {I18N.SERVICE_DETAILS}
                        <Button
                            type="normal"
                            className="edit-service-btn"
                            onClick={this.openEditServiceDialog}
                        >{I18N.EDIT_SERVICE}</Button>
                    </h1>

                    <Form style={{width: '60%'}} {...formItemLayout}>
                        <FormItem label={`${I18N.SERVICE_NAME}:`}>
                            <p>test.com</p>
                        </FormItem>
                        <FormItem label={`${I18N.PROTECT_THRESHOLD}:`}>
                            <p>0.5</p>
                        </FormItem>
                        <FormItem label={`${I18N.HEALTH_CHECK_PATTERN}:`}>
                            <p>true</p>
                        </FormItem>
                        <FormItem label={`${I18N.METADATA}:`}>
                            <p>k1=v1,k2=v2</p>
                        </FormItem>
                    </Form>

                    <Card
                        title={`${I18N.CLUSTER}:`}
                        subTitle="DEFAULT"
                        contentHeight="auto"
                        extra={<Button type="normal" onClick={this.openClusterDialog}>{I18N.EDIT_CLUSTER}</Button>}
                    >
                        <Loading
                            shape={"flower"}
                            tip={"Loading..."}
                            className="loading"
                            visible={tableLoading} color={"#333"}
                        >
                            <Table dataSource={instanceList}>
                                <Table.Column title="IP" dataIndex="ip"/>
                                <Table.Column title={I18N.PORT} dataIndex="port"/>
                                <Table.Column title={I18N.WEIGHT} dataIndex="weight"/>
                                <Table.Column title={I18N.HEALTHY} dataIndex="healthy"/>
                                <Table.Column title={I18N.METADATA} dataIndex="metadata"/>
                                <Table.Column title={I18N.OPERATION} width={150} cell={(value, index, record) => (
                                    <div>
                                        <Button
                                            type="normal"
                                            className="edit-btn"
                                            onClick={this.openInstanceDialog}
                                        >{I18N.EDITOR}</Button>
                                        <Button
                                            type={record.online ? 'normal' : 'secondary'}
                                            onClick={() => this.switchState(index, record)}
                                        >{I18N[record.online ? 'OFFLINE' : 'ONLINE']}</Button>
                                    </div>
                                )}/>
                            </Table>
                        </Loading>
                        <Pagination
                            className="pagination"
                            onChange={currentPage => this.onChange(currentPage)}
                        />
                    </Card>
                    {this.editServiceDialog()}
                    {this.editInstanceDialog()}
                    {this.editClusterDialog()}
                </Loading>
            </div>
        );
    }
}

/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ServiceDetail;
