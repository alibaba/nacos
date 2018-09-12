import React from 'react';
import { Button, Dialog, Field, Form, Input, Loading } from '@alifd/next';
const FormItem = Form.Item;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class NewNameSpace extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            dialogvisible: false,
            loading: false,
            disabled: false,
            dataSource: []
        };

        this.field = new Field(this);
        this.disabled = false;
    }

    componentDidMount() {
        this.groupLabel = document.getElementById('groupwrapper');
    }
    openDialog(dataSource) {
        this.setState({
            dialogvisible: true,
            disabled: false,
            dataSource
        });
        this.disabled = false;
    }

    closeDialog() {
        this.setState({
            dialogvisible: false
        });
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
    showGroup() {
        this.groupLabel.style.display = 'block';
    }
    hideGroup() {
        this.groupLabel.style.display = 'none';
    }
    changeType(value) {
        if (value === 0) {
            this.showGroup();
        } else {
            this.hideGroup();
        }
    }
    handleSubmit() {
        this.field.validate((errors, values) => {
            if (errors) {
                return;
            }
            let flag = this.state.dataSource.every(val => {
                if (val.namespaceShowName === values.namespaceShowName) {
                    return false;
                }
                return true;
            });
            if (!flag) {
                Dialog.alert({
                    content: window.aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.norepeat'),
                    language: window.aliwareIntl.currentLanguageCode
                });
                return;
            }
            let serverId = window.getParams('serverId') || 'daily';
            this.disabled = true;
            this.setState({
                disabled: true
            });
            window.request({
                type: 'post',
                url: `/diamond-ops/service/serverId/${serverId}/namespace`,
                contentType: 'application/json',
                beforeSend: () => {
                    this.openLoading();
                },
                data: JSON.stringify({
                    namespaceShowName: values.namespaceShowName
                }),
                success: res => {
                    this.disabled = false;
                    this.setState({
                        disabled: false
                    });
                    if (res.code === 200) {
                        this.closeDialog();
                        this.props.getNameSpaces();
                        this.refreshNameSpace(); //刷新全局namespace
                    } else {
                        Dialog.alert({
                            title: window.aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.prompt'),
                            content: res.message,
                            language: window.aliwareIntl.currentLanguageCode
                        });
                    }
                },
                complete: () => {
                    this.closeLoading();
                }
            });
        });
    }
    refreshNameSpace() {

        let serverId = window.getParams('serverId') || 'center';
        setTimeout(() => {
            window.request({
                type: 'get',
                url: `/diamond-ops/service/serverId/${serverId}/namespaceInfo`,
                success: res => {
                    if (res.code === 200) {
                        window.namespaceList = res.data;
                    }
                }
            });
        }, 2000);
    }
    validateChart(rule, value, callback) {
        const chartReg = /[@#\$%\^&\*]+/g;

        if (chartReg.test(value)) {
            callback(window.aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.input'));
        } else {
            callback();
        }
    }
    render() {
        const formItemLayout = {
            labelCol: {
                fixedSpan: 6
            },
            wrapperCol: {
                span: 18
            }
        };

        let footer = <div>
            <Button type="primary" onClick={this.handleSubmit.bind(this)} disabled={this.disabled}>{window.aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.confirm')}</Button>
            <Button type="normal" onClick={this.closeDialog.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.cancel')}</Button>
        </div>;
        return (
            <div>
                <Dialog title={window.aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.newnamespce')} style={{ width: '50%' }} visible={this.state.dialogvisible} onOk={this.handleSubmit.bind(this)} onCancel={this.closeDialog.bind(this)} footer={footer} onClose={this.closeDialog.bind(this)} language={window.aliwareIntl.currentLanguageCode}><Form field={this.field}>

                    <Loading tip={window.aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.loading')} style={{ width: '100%', position: 'relative' }} visible={this.state.loading}>
                        <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.name')} required {...formItemLayout}>
                            <Input {...this.field.init('namespaceShowName', {
                                rules: [{
                                    required: true,
                                    message: window.aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.namespacenotnull')
                                }, { validator: this.validateChart.bind(this) }]
                            })} style={{ width: '100%' }} />
                        </FormItem>
                    </Loading>
                </Form>
                </Dialog>

            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default NewNameSpace;