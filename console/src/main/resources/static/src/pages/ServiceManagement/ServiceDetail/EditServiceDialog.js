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
import {Dialog, Form, Input, Select, Message} from '@alifd/next';
import {I18N, DIALOG_FORM_LAYOUT} from './constant'

const FormItem = Form.Item;
const Option = Select.Option

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class EditServiceDialog extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            editService: {},
            editServiceDialogVisible: false
        }
        this.show = this.show.bind(this)
    }

    show(editService) {
        const {metadata = {}} = editService
        if (Object.keys(metadata).length) {
            editService.metadataText = Object.keys(metadata).map(k => `${k}=${metadata[k]}`).join(',')
        }
        this.setState({editService, editServiceDialogVisible: true})
    }

    hide() {
        this.setState({editServiceDialogVisible: false})
    }

    onConfirm() {
        const editService = Object.assign({}, this.state.editService)
        const {name, protectThreshold, healthCheckMode, metadataText} = editService
        window.request({
            method: 'POST',
            url: '/nacos/v1/ns/service/update',
            data: {serviceName: name, protectThreshold, healthCheckMode, metadata: metadataText},
            dataType: 'text',
            beforeSend: () => this.setState({loading: true}),
            success: res => {
                if (res !== 'ok') {
                    Message.error(res)
                    return
                }
                this.props.getServiceDetail()
            },
            complete: () => this.setState({loading: false})
        })
        this.hide()
    }

    onChangeCluster(changeVal) {
        const {editService = {}} = this.state
        this.setState({
            editService: Object.assign({}, editService, changeVal)
        })
    }

    render() {
        const {editService, editServiceDialogVisible} = this.state
        const {
            name,
            protectThreshold,
            healthCheckMode,
            metadataText
        } = editService
        return (
            <Dialog
                className="service-detail-edit-dialog"
                title={I18N.UPDATE_SERVICE}
                visible={editServiceDialogVisible}
                onOk={() => this.onConfirm()}
                onCancel={() => this.hide()}
                onClose={() => this.hide()}
            >
                <Form {...DIALOG_FORM_LAYOUT}>
                    <FormItem label={`${I18N.SERVICE_NAME}:`}>
                        <p>{name}</p>
                    </FormItem>
                    <FormItem label={`${I18N.PROTECT_THRESHOLD}:`}>
                        <Input
                            className="in-text"
                            value={protectThreshold}
                            onChange={protectThreshold => this.onChangeCluster({protectThreshold})}
                        />
                    </FormItem>
                    <FormItem label={`${I18N.HEALTH_CHECK_PATTERN}:`}>
                        <Select
                            className="in-select"
                            defaultValue={healthCheckMode}
                            onChange={healthCheckMode => this.onChangeCluster({healthCheckMode})}
                        >
                            <Option value="server">{I18N.HEALTH_CHECK_PATTERN_SERVICE}</Option>
                            <Option value="client">{I18N.HEALTH_CHECK_PATTERN_CLIENT}</Option>
                            <Option value="none">{I18N.HEALTH_CHECK_PATTERN_NONE}</Option>
                        </Select>
                    </FormItem>
                    <FormItem label={`${I18N.METADATA}:`}>
                        <Input
                            className="in-text"
                            value={metadataText}
                            onChange={metadataText => this.onChangeCluster({metadataText})}
                        />
                    </FormItem>
                </Form>
            </Dialog>
        )
    }
}

/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default EditServiceDialog;
