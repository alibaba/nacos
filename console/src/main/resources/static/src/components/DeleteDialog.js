import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alife/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class DeleteDialog extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            visible: false,
            title: aliwareIntl.get('newDiamond.component.DeleteDialog.Configuration_management'),
            content: '',
            isok: true,
            dataId: '',
            group: ''

        };
    }

    componentDidMount() {}
    openDialog(payload) {
        this.setState({
            visible: true,
            title: payload.title,
            content: payload.content,
            isok: payload.isok,
            dataId: payload.dataId,
            group: payload.group,
            message: payload.message
        });
    }
    closeDialog() {
        this.setState({
            visible: false
        });
    }
    render() {
        const footer = <div style={{ textAlign: 'right' }}><Button type="primary" onClick={this.closeDialog.bind(this)}>{aliwareIntl.get('newDiamond.component.DeleteDialog.determine')}</Button></div>;
        return <div>
            <Dialog visible={this.state.visible} footer={footer} language={window.pageLanguage || 'zh-cn'} style={{ width: 555 }} onCancel={this.closeDialog.bind(this)} onClose={this.closeDialog.bind(this)} title={aliwareIntl.get('newDiamond.component.DeleteDialog.deletetitle')}>
                <div>
                    <Row>
                        <Col span={'4'} style={{ paddingTop: 16 }}>
                            {this.state.isok ? <Icon type="success-filling" style={{ color: 'green' }} size={'xl'} /> : <Icon type="delete-filling" style={{ color: 'red' }} size={'xl'} />}
                           
                        </Col>
                        <Col span={'20'}>
                            <div>
                                <h3>{this.state.isok ? aliwareIntl.get('newDiamond.component.DeleteDialog.deleted_successfully_configured') : aliwareIntl.get('newDiamond.component.DeleteDialog.delete_the_configuration_failed')}</h3>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>Data ID:</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {this.state.dataId}
                                    </span>
                                </p>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>Group:</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {this.state.group}
                                    </span>
                                </p>
                                {this.state.isok ? '' : <p style={{ color: 'red' }}>{this.state.message}</p>}
                            </div>
                        </Col>
                    </Row>

                </div>
            </Dialog>

        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default DeleteDialog;