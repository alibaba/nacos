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
import { getParams, request, aliwareIntl } from '../../../globalLib';
import './index.less';
import { Button, Field, Form, Input } from '@alifd/next';
const FormItem = Form.Item;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class HistoryDetail extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            showmore: false
        };
        this.edasAppName = getParams('edasAppName');
        this.edasAppId = getParams('edasAppId');
        this.inApp = this.edasAppName;
        this.field = new Field(this);
        this.dataId = getParams('dataId') || 'yanlin';
        this.group = getParams('group') || 'DEFAULT_GROUP';
        this.serverId = getParams('serverId') || 'center';
        this.nid = getParams('nid') || '123509854';
        this.tenant = getParams('namespace') || ''; //为当前实例保存tenant参数
        //this.params = window.location.hash.split('?')[1]||'';
        this.typeMap = {
            'U': aliwareIntl.get('com.alibaba.nacos.page.historyDetail.update'),
            'I': aliwareIntl.get('com.alibaba.nacos.page.historyDetail.insert'),
            'D': aliwareIntl.get('com.alibaba.nacos.page.historyDetail.delete')
        };
    }

    componentDidMount() {
        this.getDataDetail();
    }
    toggleMore() {
        this.setState({
            showmore: !this.state.showmore
        });
    }
    getDataDetail() {
        let self = this;

        request({
            url: `/nacos/v1/cs/history?dataId=${this.dataId}&group=${this.group}&nid=${this.nid}`,
            success: function (result) {
                if (result != null) {
                    let data = result;
                    self.field.setValue('dataId', data.dataId);
                    self.field.setValue('content', data.content);
                    self.field.setValue('appName', self.inApp ? self.edasAppName : data.appName);
                    self.field.setValue('envs', self.serverId);
                    self.field.setValue('opType', self.typeMap[data.opType.trim()]);
                    self.field.setValue('group', data.group);
                    self.field.setValue('md5', data.md5);
                }
            }
        });
    }
    goList() {

        //console.log(`/configurationManagement?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}`)
        this.props.history.push(`/historyRollback?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}&namespace=${this.tenant}`);
    }
    render() {
        const init = this.field.init;
        const formItemLayout = {
            labelCol: {
                fixedSpan: 6
            },
            wrapperCol: {
                span: 18
            }
        };
        return (
            <div style={{ padding: 10 }}>
                <h1>{aliwareIntl.get('com.alibaba.nacos.page.historyDetail.history_details')}</h1>
                <Form field={this.field}>

                    <FormItem label="Data ID:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('dataId')} />
                        <div style={{ marginTop: 10 }}>
                            <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>{this.state.showmore ? aliwareIntl.get('com.alibaba.nacos.page.historyDetail.recipient_from') : aliwareIntl.get('com.alibaba.nacos.page.historyDetail.more_advanced_options')}</a>
                        </div>
                    </FormItem>
                    <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
                        <FormItem label="Group:" required {...formItemLayout}>
                            <Input htmlType="text" readOnly={true} {...init('group')} />
                        </FormItem>
                        <FormItem label={aliwareIntl.get('com.alibaba.nacos.page.historyDetail.home')} {...formItemLayout}>
                            <Input htmlType="text" readOnly={true} {...init('appName')} />
                        </FormItem>
                    </div>
                    <FormItem label={aliwareIntl.get('com.alibaba.nacos.page.historyDetail.action_type')} required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('opType')} />
                    </FormItem>
                    <FormItem label="MD5:" required {...formItemLayout}>
                        <Input htmlType="text" readOnly={true} {...init('md5')} />
                    </FormItem>
                    <FormItem label={aliwareIntl.get('com.alibaba.nacos.page.historyDetail.configure_content')} required {...formItemLayout}>
                        <Input.TextArea htmlType="text" multiple rows={15} readOnly={true} {...init('content')} />
                    </FormItem>
                    <FormItem label=" " {...formItemLayout}>
                        <Button type="primary" onClick={this.goList.bind(this)}>{aliwareIntl.get('com.alibaba.nacos.page.historyDetail.return')}</Button>

                    </FormItem>
                </Form>
            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default HistoryDetail;