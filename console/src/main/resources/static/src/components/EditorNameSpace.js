import React from 'react';
import MinusIcon from './MinusIcon';
import AddIcon from './AddIcon';
import AddGroup from './AddGroup';
import { Button, Dialog, Field, Form, Grid, Input, Loading } from '@alifd/next';
const FormItem = Form.Item;
const { Row, Col } = Grid;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class EditorNameSpace extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            dialogvisible: false,
            loading: false
        };
        this.field = new Field(this);
    }

    componentDidMount() {
    	
    }
    
    openDialog(record) {
        this.getNamespaceDetail(record);
        this.setState({
            dialogvisible: true,
            type: record.type
        });
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

    getNamespaceDetail(record){
        this.field.setValues(record);
        window.request({
            type: 'get',
            url: `/nacos/v1/console/namespaces?show=all&namespaceId=${record.namespace}`,
            success: res => {
                debugger;
                if (res !== null) {
                    this.field.setValue('namespaceDesc', res.namespaceDesc);
                } else {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        title: window.aliwareIntl.get('com.alibaba.nacos.component.NameSpaceList.Prompt'),
                        content: res.message
                    });
                }
            },
            error: res => {
                window.namespaceList = [];
                this.handleNameSpaces(window.namespaceList);
            }
        });
    }

    handleSubmit() {
        this.field.validate((errors, values) => {
            if (errors) {
                return;
            }
            window.request({
                type: 'put',
                beforeSend: () => {
                    this.openLoading();
                },
                url: `/nacos/v1/console/namespaces`,
                contentType: 'application/x-www-form-urlencoded',
                data: {
                	"namespace":values.namespace,
                	"namespaceShowName":values.namespaceShowName,
                	"namespaceDesc":values.namespaceDesc
                },
                success: res => {
                    if (res === true) {
                        this.closeDialog();
                        this.props.getNameSpaces();
                        this.refreshNameSpace(); //刷新全局namespace
                    } else {
                        Dialog.alert({
                            language: window.pageLanguage || 'zh-cn',
                            title: window.aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.prompt'),
                            content: res.message
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

        setTimeout(() => {
            window.request({
                type: 'get',
                url: `/nacos/v1/console/namespaces`,
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
            callback(window.aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.please_do'));
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

        let footer = this.state.type === 0 ? <div></div> : <Button type="primary" onClick={this.handleSubmit.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.public_space')}</Button>;
        return (
            <div>
                <Dialog title={window.aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.confirm_modify')} style={{ width: '50%' }} visible={this.state.dialogvisible} footer={footer} onCancel={this.closeDialog.bind(this)} onClose={this.closeDialog.bind(this)} language={window.aliwareIntl.currentLanguageCode}>
                    <Loading tip={window.aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.edit_namespace')} style={{ width: '100%', position: 'relative' }} visible={this.state.loading}>
                        <Form field={this.field}>
                            <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.load')} required {...formItemLayout}>
                                <Input {...this.field.init('namespaceShowName', {
                                    rules: [{
                                        required: true,
                                        message: window.aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.namespace')
                                    }, { validator: this.validateChart.bind(this) }]
                                })} disabled={this.state.type === 0 ? true : false} />
                            </FormItem>
                            <FormItem label={window.aliwareIntl.get('nacos.page.configdetail.Description')} required {...formItemLayout}>
                                <Input {...this.field.init('namespaceDesc', {
                                	rules: [{
                                		required: true,
                                		message: window.aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.namespace')
                                	}, { validator: this.validateChart.bind(this) }]
                                })} disabled={this.state.type === 0 ? true : false} />
                            </FormItem>
                        </Form>
                    </Loading>
                </Dialog>

            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default EditorNameSpace;