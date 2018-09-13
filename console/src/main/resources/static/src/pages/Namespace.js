import React from 'react';
import { Button, Dialog, Loading, Table } from '@alifd/next';
import RegionGroup from '../components/RegionGroup';
import DeleteDialog from '../components/DeleteDialog';
import NewNameSpace from '../components/NewNameSpace';
import EditorNameSpace from '../components/EditorNameSpace';

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class Namespace extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            defaultNamespace: "",
            dataSource: []

        };
    }

    componentDidMount() {
        this.getNameSpaces(0);
    }
    getNameSpaces(delayTime = 2000) {
        let self = this;
        // let serverId = window.getParams('serverId') || 'center';
        self.openLoading();
        setTimeout(() => {
            window.request({
                type: 'get',
                beforeSend: function () { },
                url: `/nacos/v1/cs/namespaces`,
                success: res => {
                    if (res.code === 200) {
                        let data = res.data;
                        for (var i = 0; i < data.length; i++) {
                            if (data[i].type === 1) {
                                this.setState({
                                    defaultNamespace: data[i].namespace
                                });
                            }
                        }

                        this.setState({
                            dataSource: data
                        });
                    } else {
                        Dialog.alert({
                            language: window.pageLanguage || 'zh-cn',
                            title: window.aliwareIntl.get('com.alibaba.nacos.page.namespace.prompt'),
                            content: res.message
                        });
                    }
                },
                complete: function () {
                    self.closeLoading();
                },
                error: res => {
                    window.namespaceList = [{
                        "namespace": "",
                        "namespaceShowName": "公共空间",
                        "type": 0
                    }];
                }
            });
        }, delayTime);
    }

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

    detailNamespace(record) {
        let namespace = record.namespace; //获取ak,sk
        window.request({
            url: `/diamond-ops/service/namespaceOwnerInfo/${namespace}`,
            beforeSend: () => {
                this.openLoading();
            },
            success: res => {
                if (res.code === 200) {
                    let obj = {
                        regionId: res.data.regionId,
                        accessKey: res.data.accessKey,
                        secretKey: res.data.secretKey,
                        endpoint: res.data.endpoint
                    };

                    Dialog.alert({
                        needWrapper: false,
                        language: window.pageLanguage || 'zh-cn',
                        title: window.aliwareIntl.get('nacos.page.namespace.Namespace_details'),
                        content: <div>
                            <div style={{ marginTop: '10px' }}>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.region_ID')}</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {obj.regionId}
                                    </span>
                                </p>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.namespace_name')}</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {record.namespaceShowName}
                                    </span>
                                </p>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.namespace_ID')}</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {record.namespace}
                                    </span>
                                </p>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>End Point:</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {obj.endpoint}
                                    </span>
                                </p>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.ecs_ram_role')}</span>
                                    <a href={window._getLink && window._getLink("ecsInstanceRamRolesUse")} rel="noopener noreferrer" target="_blank">{window.aliwareIntl.get('nacos.page.namespace._Details_of6')}</a>
                                </p>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.AccessKey_recommended1')}</span>
                                    <span style={{ color: '#c7254e' }}>
                                        <a href={window._getLink && window._getLink("getAk")} rel="noopener noreferrer" target="_blank">{window.aliwareIntl.get('nacos.page.namespace.click_on_the_obtain_of1')}</a>
                                    </span>
                                </p>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.SecretKey_recommended3')}</span>
                                    <span style={{ color: '#c7254e' }}>
                                        <a href={window._getLink && window._getLink("getAk")} rel="noopener noreferrer" target="_blank">{window.aliwareIntl.get('nacos.page.namespace.click_on_the_obtain_of1')}</a>
                                    </span>
                                </p>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.ACM_dedicated_AccessKey_will_the_waste,_does_not_recommend_the_use_of3')}</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {obj.accessKey}
                                    </span>
                                </p>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.ACM_special_SecretKey_will_be_abandoned,_not_recommended_for_use4')}</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {obj.secretKey}
                                    </span>
                                </p>
                            </div>
                            <div style={{ marginTop: '20px', backgroundColor: '#eee', padding: 10, fontSize: 12 }}>{window.aliwareIntl.get('nacos.page.namespace.note_ACM_is_dedicated_AK/SK_is_mainly_used_for_some_of_the_compatibility_scenario,_it_is_recommended_to_Unified_the_use_of_Ali_cloud_AK/SK.5')}<a href={window._getLink && window._getLink("akHelp")} rel="noopener noreferrer" target="_blank">{window.aliwareIntl.get('nacos.page.namespace._Details_of6')}</a>
                            </div>
                        </div>
                    });
                }
            },
            complete: () => {
                this.closeLoading();
            }
        });
    }

    removeNamespace(record) {
        let serverId = window.getParams('serverId') || 'center';
        Dialog.confirm({
            title: window.aliwareIntl.get('nacos.page.namespace.remove_the_namespace'),
            content: <div style={{ marginTop: '-20px' }}>
                <h3>{window.aliwareIntl.get('nacos.page.namespace.sure_you_want_to_delete_the_following_namespaces?')}</h3>
                <p>
                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.namespace_name')}</span>
                    <span style={{ color: '#c7254e' }}>
                        {record.namespaceShowName}
                    </span>
                </p>
                <p>
                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.namespace_ID')}</span>
                    <span style={{ color: '#c7254e' }}>
                        {record.namespace}
                    </span>
                </p>
            </div>,
            language: window.pageLanguage || 'zh-cn',
            onOk: () => {
                window.request({
                    url: "com.alibaba.nacos.service.deleteNameSpace",
                    type: 'delete',
                    data: {},
                    $data: {
                        serverId: serverId,
                        namespace: record.namespace
                    },
                    success: res => {
                        let _payload = {};
                        _payload.title = window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.configuration_management');
                        if (res.code === 200) {
                            let urlnamespace = window.getParams('namespace');
                            if (record.namespace === urlnamespace) {
                                window.setParams('namespace', this.state.defaultNamespace);
                            }
                            Dialog.confirm({
                                language: window.pageLanguage || 'zh-cn',
                                content: window.aliwareIntl.get('nacos.page.namespace._Remove_the_namespace_success'),
                                title: window.aliwareIntl.get('nacos.page.namespace.deleted_successfully')
                            });
                        } else {
                            Dialog.confirm({
                                language: window.pageLanguage || 'zh-cn',
                                content: res.message,
                                title: "删除失败"
                            });
                        }

                        this.getNameSpaces();
                    }
                });
            }
        });
    }

    refreshNameSpace() {
        window.request({
            type: 'get',
            url: `/nacos/v1/cs/namespaces`,
            success: res => {
                if (res.code === 200) {
                    window.namespaceList = res.data;
                }
            },
            error: res => {
                window.namespaceList = [{
                    "namespace": "",
                    "namespaceShowName": "公共空间",
                    "type": 0
                }];;
            }
        });
    }

    openToEdit(record) {
        this.refs['editgroup'].openDialog(record);
    }
    renderOption(value, index, record) {
        let _delinfo = <a onClick={this.removeNamespace.bind(this, record)} style={{ marginRight: 10 }}>{window.aliwareIntl.get('com.alibaba.nacos.page.namespace.delete')}</a>;
        if (record.type === 1 || record.type === 0) {
            _delinfo = <span style={{ marginRight: 10, cursor: 'not-allowed' }} disabled={true}>{window.aliwareIntl.get('com.alibaba.nacos.page.namespace.delete')}</span>;
        }
        let _detailinfo = <a onClick={this.detailNamespace.bind(this, record)} style={{ marginRight: 10 }}>{window.aliwareIntl.get('nacos.page.namespace.details')}</a>;

        let _editinfo = <a onClick={this.openToEdit.bind(this, record)}>{window.aliwareIntl.get('com.alibaba.nacos.page.namespace.edit')}</a>;
        if (record.type === 0 || record.type === 1) {
            _editinfo = <span style={{ marginRight: 10, cursor: 'not-allowed' }} disabled={true}>{window.aliwareIntl.get('com.alibaba.nacos.page.namespace.edit')}</span>;
        }
        return <div>
            {_detailinfo}
            {_delinfo}
            {_editinfo}
        </div>;
    }
    addNameSpace() {
        this.refs['newnamespace'].openDialog(this.state.dataSource);
    }
    renderName(value, index, record) {

        let name = record.namespaceShowName;
        if (record.type === 0) {
            name = window.aliwareIntl.get('com.alibaba.nacos.page.namespace.public');
        }
        return <div>{name}</div>;
    }
    renderConfigCount(value, index, record) {
        return <div>{value} / {record.quota}</div>;
    }
    render() {
        const pubnodedata = window.aliwareIntl.get('pubnodata');

        const locale = {
            empty: pubnodedata
        };
        return <div style={{ padding: 10 }} className='clearfix'>
            <RegionGroup left={window.aliwareIntl.get('nacos.page.namespace.Namespace')} />
            <div className="fusion-demo">
                <Loading shape="flower" tip="Loading..." color="#333" style={{ width: '100%' }} visible={this.state.loading}>
                    <div>
                        <div style={{ textAlign: 'right', marginBottom: 10 }}>

                            <Button type="primary" style={{ marginRight: 20, marginTop: 10 }} onClick={this.addNameSpace.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.page.namespace.add')}</Button>
                        </div>
                        <div>
                            <Table dataSource={this.state.dataSource} locale={locale} language={window.aliwareIntl.currentLanguageCode}>
                                <Table.Column title={window.aliwareIntl.get('com.alibaba.nacos.page.namespace.namespace_names')} dataIndex="namespaceShowName" cell={this.renderName.bind(this)} />
                                <Table.Column title={window.aliwareIntl.get('nacos.page.namespace.namespace_number')} dataIndex="namespace" />
                                <Table.Column title={window.aliwareIntl.get('com.alibaba.nacos.page.namespace.configuration')} dataIndex="configCount" cell={this.renderConfigCount.bind(this)} />

                                <Table.Column title={window.aliwareIntl.get('com.alibaba.nacos.page.namespace.operation')} dataIndex="time" cell={this.renderOption.bind(this)} />
                            </Table>
                        </div>
                    </div>

                    <DeleteDialog ref="delete" />
                    <NewNameSpace ref={'newnamespace'} getNameSpaces={this.getNameSpaces.bind(this)} />
                    <EditorNameSpace ref={'editgroup'} getNameSpaces={this.getNameSpaces.bind(this)} />
                </Loading>
            </div>




        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default Namespace;