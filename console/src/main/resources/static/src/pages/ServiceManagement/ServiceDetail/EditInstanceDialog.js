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
import {Dialog, Form, Input, Switch, Message} from '@alifd/next';
import {I18N, DIALOG_FORM_LAYOUT} from './constant'

const FormItem = Form.Item;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class EditInstanceDialog extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            editInstance: {},
            editInstanceDialogVisible: false
        }
        this.show = this.show.bind(this)
    }

    show(editInstance) {
        const {metadata = {}} = editInstance
        if (Object.keys(metadata).length) {
            editInstance.metadataText = Object.keys(metadata).map(k => `${k}=${metadata[k]}`).join(',')
        }
        this.setState({editInstance, editInstanceDialogVisible: true})
    }

    hide() {
        this.setState({editInstanceDialogVisible: false})
    }

    onConfirm() {
        const {serviceName, clusterName, getInstanceList, openLoading, closeLoading} = this.props
        const {ip, port, weight, enabled, metadataText} = this.state.editInstance
        window.request({
            method: 'POST',
            url: '/nacos/v1/ns/instance/update',
            data: {serviceName, clusterName, ip, port, weight, enable: enabled, metadata: metadataText},
            dataType: 'text',
            beforeSend: () => openLoading(),
            success: res => {
                if (res !== 'ok') {
                    Message.error(res)
                    return
                }
                this.hide()
                getInstanceList()
            },
            complete: () => closeLoading()
        })
    }

    onChangeCluster(changeVal) {
        const {editInstance = {}} = this.state
        this.setState({
            editInstance: Object.assign({}, editInstance, changeVal)
        })
    }

    render() {
        const {editInstanceDialogVisible, editInstance} = this.state
        return (
            <Dialog
                className="instance-edit-dialog"
                title={I18N.UPDATE_INSTANCE}
                visible={editInstanceDialogVisible}
                onOk={() => this.onConfirm()}
                onCancel={() => this.hide()}
                onClose={() => this.hide()}
            >
                <Form {...DIALOG_FORM_LAYOUT}>
                    <FormItem label="IP:">
                        <p>{editInstance.ip}</p>
                    </FormItem>
                    <FormItem label={`${I18N.PORT}:`}>
                        <p>{editInstance.port}</p>
                    </FormItem>
                    <FormItem label={`${I18N.WEIGHT}:`}>
                        <Input
                            className="in-text"
                            value={editInstance.weight}
                            onChange={weight => this.onChangeCluster({weight})}
                        />
                    </FormItem>
                    <FormItem label={`${I18N.WHETHER_ONLINE}:`}>
                        <Switch
                            checked={editInstance.enabled}
                            onChange={enabled => this.onChangeCluster({enabled})}/>
                    </FormItem>
                    <FormItem label={`${I18N.METADATA}:`}>
                        <Input
                            className="in-text"
                            value={editInstance.metadataText}
                            onChange={metadataText => this.onChangeCluster({metadataText})}
                        />
                    </FormItem>
                </Form>
            </Dialog>
        )
    }
}

/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default EditInstanceDialog;
