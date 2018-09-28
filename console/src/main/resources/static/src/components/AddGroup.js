import React from 'react'; 
import { Dialog, Field, Form, Input } from '@alifd/next';
const FormItem = Form.Item; 

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class AddGroup extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            dialogvisible: false
        };

        this.field = new Field(this);
    }

    componentDidMount() {}
    openDialog() {
        this.setState({
            dialogvisible: true
        });
    }

    closeDialog() {
        this.setState({
            dialogvisible: false
        });
    }
    handeSubmit() {
        this.field.validate((error, value) => {
            if (error) {
                return;
            }
            let group = value.group;
            this.addGroup(group);
        });
    }
    addGroup(group) {
        window.request({
            type: 'post',
            url: `/diamond-ops/service/group?group=${group}`,
            contentType: 'application/json',
            data: JSON.stringify({
                group: group
            }),
            success: res => {
                if (res.code === 200) {
                    this.closeDialog();
                    this.props.getGroup();
                } else {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        title: window.aliwareIntl.get('com.alibaba.nacos.component.AddGroup.prompt'),
                        content: res.message
                    });
                }
            }
        });
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

        return (
            <div>
                <Dialog title={window.aliwareIntl.get('com.alibaba.nacos.component.AddGroup.a_new_group')} style={{ width: '60%' }} visible={this.state.dialogvisible} language={window.pageLanguage || 'zh-cn'} onOk={this.handeSubmit.bind(this)} onCancel={this.closeDialog.bind(this)} onClose={this.closeDialog.bind(this)}><Form field={this.field}>


                        <FormItem label={window.aliwareIntl.get('com.alibaba.nacos.component.AddGroup.group_name')} required {...formItemLayout}>
                            <Input {...this.field.init('group')} />
                        </FormItem>

                    </Form>
                </Dialog>

            </div>
        );
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default AddGroup;