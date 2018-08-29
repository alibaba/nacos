import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alife/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class SuccessDialog extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            visible: false,
            title: aliwareIntl.get('com.alibaba.newDiamond.component.SuccessDialog.Configuration_management'),
            maintitle: '',
            content: '',
            isok: true,
            dataId: '',
            group: ''

        };
    }

    componentDidMount() {}
    openDialog(payload) {
        if(this.props.unpushtrace) {
            payload.title = '';
        }
        this.setState({
            visible: true,
            maintitle: payload.maintitle,
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
        const footer = <div style={{ textAlign: 'right' }}><Button type="primary" onClick={this.closeDialog.bind(this)}>{aliwareIntl.get('com.alibaba.newDiamond.component.SuccessDialog.determine')}</Button></div>;
        return <div>
            <Dialog visible={this.state.visible} footer={footer} style={{ width: 555 }} onCancel={this.closeDialog.bind(this)} onClose={this.closeDialog.bind(this)} title={this.state.maintitle || this.state.title} language={aliwareIntl.currentLanguageCode}>
                <div>
                    <Row>
                        <Col span={'4'} style={{ paddingTop: 16 }}>
                            {this.state.isok ? <Icon type="success-filling" style={{ color: 'green' }} size={'xl'} /> : <Icon type="delete-filling" style={{ color: 'red' }} size={'xl'} />}
                           
                        </Col>
                        <Col span={'20'}>
                            <div>
                                {this.state.isok ? <h3>{this.state.title}</h3> : <h3>{this.state.title} {aliwareIntl.get('com.alibaba.newDiamond.component.SuccessDialog.failure')}</h3>}
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
export default SuccessDialog;