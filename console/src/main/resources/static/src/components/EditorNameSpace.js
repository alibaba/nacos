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
            group: [],
            type: 0,
            loading: false

        };

        this.field = new Field(this);
    }

    componentDidMount() {
        this.getGroup();
    }
    openDialog(record) {

        this.getGroup();
        this.field.setValues(record);
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
    getGroup() {
        window.request({
            type: 'get',
            beforeSend: function () { },
            url: '/diamond-ops/service/group',
            success: res => {
                if (res.code === 200) {
                    this.setState({
                        group: res.data
                    });
                }
            }
        });
    }
    openAddGroup() {
        this.refs['addgroup'].openDialog();
    }
    delGroup(value) {
        let group = this.state.group;
        let i = group.indexOf(value);
        group.splice(i, 1);
        this.setState({
            group: group
        });
    }
    handleSubmit() {
        this.field.validate((errors, values) => {
            if (errors) {
                return;
            }
            let serverId = window.getParams('serverId') || 'daily';
            window.request({
                type: 'put',
                beforeSend: () => {
                    this.openLoading();
                },
                url: `/diamond-ops/service/serverId/${serverId}/namespace`,
                contentType: 'application/json',
                data: JSON.stringify(values),
                success: res => {
                    if (res.code === 200) {
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
        // const list = [{
        //     value: '2',
        //     label: window.aliwareIntl.get('com.alibaba.nacos.component.editorNameSpace')

        // }, {
        //     value: '0',
        //     label: window.aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.private')

        // }];

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
                                <Input {...this.field.init('namespace')} htmlType="hidden" />
                                <Input {...this.field.init('type')} htmlType="hidden" />
                            </FormItem>
                            {this.state.type === 0 ? <FormItem label="group:" required {...formItemLayout}>
                                <div style={{ height: 300, border: '1px solid #ccc' }}>

                                    {this.state.group.map((value, index) => {
                                        return <Row style={{ width: '100%', margin: '0 auto', padding: 4, textAlign: 'center' }}>
                                            <Col span={'12'}>{value}</Col>
                                            <Col span={'12'}>
                                                <div style={{ width: 30, margin: '0 auto', cursor: 'pointer' }} onClick={this.delGroup.bind(this, value)}>
                                                    <MinusIcon />
                                                </div>
                                            </Col>
                                        </Row>;
                                    })}
                                    <Row style={{ width: '100%', margin: '0 auto', padding: 4, textAlign: 'center' }}>
                                        <Col span={'12'} style={{ cursor: 'pointer' }}>
                                            <div style={{ width: 30, margin: '0 auto' }} onClick={this.openAddGroup.bind(this)}>
                                                <AddIcon />
                                            </div></Col>
                                        <Col span={'12'}></Col>
                                    </Row>


                                </div>
                            </FormItem> : ''}
                        </Form>
                        <AddGroup getGroup={this.getGroup.bind(this)} ref={'addgroup'} />
                    </Loading>
                </Dialog>

            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default EditorNameSpace;