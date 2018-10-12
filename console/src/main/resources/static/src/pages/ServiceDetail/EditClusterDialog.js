import React from 'react';
import {Dialog, Form, Input, Switch, Select} from '@alifd/next';
import {I18N, DIALOG_FORM_LAYOUT} from './constant'

const FormItem = Form.Item;
const Option = Select.Option

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class EditClusterDialog extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            editCluster: {},
            editClusterDialogVisible: false
        }
        this.show = this.show.bind(this)
    }

    show(editCluster) {
        const {metadata = {}} = editCluster
        editCluster.metadataText = Object.keys(metadata).map(k => `${k}=${metadata[k]}`).join(',')
        this.setState({
            editCluster,
            editClusterDialogVisible: true
        }, () => console.log(this.state.editCluster))
    }

    hide() {
        this.setState({editClusterDialogVisible: false})
    }

    onConfirm() {
        console.log('confirm', this.props, this.state)
    }

    onChangeCluster(changeVal) {
        const {editCluster = {}} = this.state
        this.setState({
            editCluster: Object.assign({}, editCluster, changeVal)
        })
    }

    render() {
        const {editCluster = {}, editClusterDialogVisible} = this.state
        const {
            healthChecker = {},
            useIPPort4Check,
            defaultCheckPort = '80',
            metadataText = ''
        } = editCluster
        const {type, path, headers} = healthChecker
        const healthCheckerChange = changeVal => this.onChangeCluster({
            healthChecker: Object.assign({}, healthChecker, changeVal)
        })
        return (
            <Dialog
                className="cluster-edit-dialog"
                title={I18N.UPDATE_CLUSTER}
                visible={editClusterDialogVisible}
                onOk={() => this.onConfirm()}
                onCancel={() => this.hide()}
                onClose={() => this.hide()}
            >
                <Form {...DIALOG_FORM_LAYOUT}>
                    <FormItem label={`${I18N.CHECK_TYPE}:`}>
                        <Select
                            className="in-select"
                            defaultValue={type}
                            onChange={type => healthCheckerChange({type})}
                        >
                            <Option value="TCP">TCP</Option>
                            <Option value="HTTP">HTTP</Option>
                        </Select>
                    </FormItem>
                    <FormItem label={`${I18N.CHECK_PORT}:`}>
                        <Input className="in-text"
                               value={defaultCheckPort}
                               onChange={defaultCheckPort => this.onChangeCluster({defaultCheckPort})}
                        />
                    </FormItem>
                    <FormItem label={`${I18N.USE_IP_PORT_CHECK}:`}>
                        <Switch
                            checked={useIPPort4Check}
                            onChange={useIPPort4Check => this.onChangeCluster({useIPPort4Check})}
                        />
                    </FormItem>
                    {
                        type === 'HTTP'
                            ? (<div>
                                <div className="next-row next-form-item next-left next-medium">
                                    <div className="next-col next-col-fixed-12 next-form-item-label">
                                        <label>{`${I18N.CHECK_PATH}:`}</label>
                                    </div>
                                    <div className="next-col next-col-12 next-form-item-control">
                                        <Input
                                            className="in-text"
                                            value={path}
                                            onChange={path => healthCheckerChange({path})}
                                        />
                                    </div>
                                </div>
                                <div className="next-row next-form-item next-left next-medium">
                                    <div className="next-col next-col-fixed-12 next-form-item-label">
                                        <label>{`${I18N.CHECK_HEADERS}:`}</label>
                                    </div>
                                    <div className="next-col next-col-12 next-form-item-control">
                                        <Input
                                            className="in-text"
                                            value={headers}
                                            onChange={headers => healthCheckerChange({headers})}
                                        />
                                    </div>
                                </div>
                            </div>)
                            : null
                    }
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
export default EditClusterDialog;
