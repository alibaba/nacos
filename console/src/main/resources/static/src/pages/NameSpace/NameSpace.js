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
import { Button, Dialog, Loading, Table } from '@alifd/next';
import RegionGroup from '../../components/RegionGroup';
import DeleteDialog from '../../components/DeleteDialog';
import NewNameSpace from '../../components/NewNameSpace';
import EditorNameSpace from '../../components/EditorNameSpace';
import './index.less';

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class NameSpace extends React.Component {
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
                url: `/nacos/v1/console/namespaces`,
                success: res => {
                    if (res.code === 200) {
                        let data = res.data || [];
                        window.namespaceList = data;

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
            url: `/nacos/v1/console/namespaces?show=all&namespaceId=${namespace}`,
            beforeSend: () => {
                this.openLoading();
            },
            success: res => {
            	if (res !== null) {
                    Dialog.alert({
                        style: { width: "500px" },
                        needWrapper: false,
                        language: window.pageLanguage || 'zh-cn',
                        title: window.aliwareIntl.get('nacos.page.namespace.Namespace_details'),
                        content: <div>
                            <div style={{ marginTop: '10px' }}>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.namespace_name')}</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {res.namespaceShowName}
                                    </span>
                                </p>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.namespace.namespace_ID')}</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {res.namespace}
                                    </span>
                                </p>
                                <p>
                                <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('com.alibaba.nacos.page.namespace.configuration')}</span>
                                <span style={{ color: '#c7254e' }}>
                                {res.configCount} / {res.quota}
                                </span>
                                </p>
                                <p>
                                <span style={{ color: '#999', marginRight: 5 }}>{window.aliwareIntl.get('nacos.page.configdetail.Description')}</span>
                                <span style={{ color: '#c7254e' }}>
                                {res.namespaceDesc}
                                </span>
                                </p>
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
        // let serverId = window.getParams('serverId') || 'center';
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
            	let url = `/nacos/v1/console/namespaces?namespaceId=${record.namespace}`;
                window.request({
                    url: url,
                    type: 'delete',
                    success: res => {
                        let _payload = {};
                        _payload.title = window.aliwareIntl.get('com.alibaba.nacos.page.configurationManagement.configuration_management');
                        if (res === true) {
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
            url: `/nacos/v1/console/namespaces`,
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

                            <Button type="primary" style={{ marginRight: 0, marginTop: 10 }} onClick={this.addNameSpace.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.page.namespace.add')}</Button>
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
export default NameSpace;