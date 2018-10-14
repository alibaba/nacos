import React from 'react';
import {Button, Card, Form, Loading} from '@alifd/next';
import EditServiceDialog from './EditServiceDialog'
import EditClusterDialog from './EditClusterDialog'
import InstanceTable from './InstanceTable'
import queryString from 'query-string'
import {I18N} from './constant'
import './ServiceDetail.less'

const FormItem = Form.Item;
const pageFormLayout = {
    labelCol: {fixedSpan: 10},
    wrapperCol: {span: 14}
};

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ServiceDetail extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            serviceName: queryString.parse(props.location.search).name,
            loading: false,
            currentPage: 1,
            clusters: [],
            instances: {},
            service: {},
            pageSize: 10,
            pageNum: {}
        }
    }

    componentDidMount() {
        if (!this.state.serviceName) {
            this.props.history.goBack()
            return
        }
        this.getServiceDetail()
    }

    getServiceDetail() {
        const {serviceName} = this.state
        window.request({
            url: `/nacos/v1/ns/catalog/serviceDetail?serviceName=${serviceName}`,
            beforeSend: () => this.openLoading(),
            success: ({clusters = [], service = {}}) => this.setState({service, clusters}),
            complete: () => this.closeLoading()
        })
    }

    openLoading() {
        this.setState({loading: true})
    }

    closeLoading() {
        this.setState({loading: false})
    }

    openEditServiceDialog() {
        this.refs.editServiceDialog.show(this.state.service)
    }

    openClusterDialog(cluster) {
        this.refs.editClusterDialog.show(cluster)
    }

    render() {
        const {serviceName, loading, service = {}, clusters} = this.state
        const {metadata = {}} = service
        const metadataText = Object.keys(metadata).map(key => `${key}=${metadata[key]}`).join(',')
        return (
            <div className="main-container service-detail">
                <Loading
                    shape={"flower"}
                    tip={"Loading..."}
                    className="loading"
                    visible={loading}
                    color={"#333"}
                >
                    <h1 style={{
                        position: 'relative',
                        width: '100%'
                    }}>
                        {I18N.SERVICE_DETAILS}
                        <Button
                            type="normal"
                            className="edit-service-btn"
                            onClick={() => this.openEditServiceDialog()}
                        >{I18N.EDIT_SERVICE}</Button>
                    </h1>

                    <Form style={{width: '60%'}} {...pageFormLayout}>
                        <FormItem label={`${I18N.SERVICE_NAME}:`}>
                            <p>{service.name}</p>
                        </FormItem>
                        <FormItem label={`${I18N.PROTECT_THRESHOLD}:`}>
                            <p>{service.protectThreshold}</p>
                        </FormItem>
                        <FormItem label={`${I18N.HEALTH_CHECK_PATTERN}:`}>
                            <p>{service.healthCheckMode}</p>
                        </FormItem>
                        <FormItem label={`${I18N.METADATA}:`}>
                            <p>{metadataText}</p>
                        </FormItem>
                    </Form>
                    {
                        clusters.map(cluster => (
                            <Card
                                key={cluster.name}
                                className="cluster-card"
                                title={`${I18N.CLUSTER}:`}
                                subTitle={cluster.name}
                                contentHeight="auto"
                                extra={(
                                    <Button
                                        type="normal"
                                        onClick={() => this.openClusterDialog(cluster)}
                                    >{I18N.EDIT_CLUSTER}</Button>
                                )}
                            >
                                <InstanceTable
                                    clusterName={cluster.name}
                                    serviceName={serviceName}
                                />
                            </Card>
                        ))
                    }
                </Loading>
                <EditServiceDialog
                    ref="editServiceDialog"
                    openLoading={() => this.openLoading()}
                    closeLoading={() => this.closeLoading()}
                    getServiceDetail={() => this.getServiceDetail()}
                />
                <EditClusterDialog
                    ref="editClusterDialog"
                    openLoading={() => this.openLoading()}
                    closeLoading={() => this.closeLoading()}
                    getServiceDetail={() => this.getServiceDetail()}
                />
            </div>
        );
    }
}

/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ServiceDetail;
