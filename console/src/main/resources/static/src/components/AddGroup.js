import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alifd/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

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
        request({
            type: 'post',
            url: `/diamond-ops/service/group?group=${group}`,
            contentType: 'application/json',
            data: JSON.stringify({
                group: group
            }),
            success: res => {
                if (res.code == 200) {
                    this.closeDialog();
                    this.props.getGroup();
                } else {
                    Dialog.alert({
                        language: window.pageLanguage || 'zh-cn',
                        title: aliwareIntl.get('com.alibaba.newDiamond.component.AddGroup.prompt'),
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
                <Dialog title={aliwareIntl.get('com.alibaba.newDiamond.component.AddGroup.a_new_group')} style={{ width: '60%' }} visible={this.state.dialogvisible} language={window.pageLanguage || 'zh-cn'} onOk={this.handeSubmit.bind(this)} onCancel={this.closeDialog.bind(this)} onClose={this.closeDialog.bind(this)}><Form field={this.field}>


                        <FormItem label={aliwareIntl.get('com.alibaba.newDiamond.component.AddGroup.group_name')} required {...formItemLayout}>
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