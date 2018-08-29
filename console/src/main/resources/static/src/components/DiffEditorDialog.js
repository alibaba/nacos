import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alife/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class DiffEditorDialog extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            dialogvisible: false
        };
    }

    componentDidMount() {}
    openDialog(letfcode, rightcode) {
        this.setState({
            dialogvisible: true
        });
        setTimeout(() => {
            this.createDiffCodeMirror(letfcode, rightcode);
        });
    }

    closeDialog() {
        this.setState({
            dialogvisible: false
        });
    }
    createDiffCodeMirror(leftCode, rightCode) {
        let target = this.refs["diffeditor"];
        target.innerHTML = '';

        this.diffeditor = CodeMirror.MergeView(target, {
            value: leftCode || '',
            readOnly: true,
            origLeft: null,
            orig: rightCode || '',
            lineNumbers: true,
            mode: this.mode,
            theme: 'xq-light',
            highlightDifferences: true,
            connect: 'align',
            collapseIdentical: false
        });
        // this.diffeditor.leftOriginal().setSize(null,480);
        // this.diffeditor.rightOriginal().setSize(null, 480);
        // this.diffeditor.wrap.style.height= '480px';
        // this.diffeditor.edit.setSize('100%',480);
        // this.diffeditor.right.edit.setSize('100%',480);
    }
    confirmPub() {
        this.closeDialog();
        this.props.publishConfig();
    }
    render() {
        const footer = <div> <Button type="primary" onClick={this.confirmPub.bind(this)}>{aliwareIntl.get('com.alibaba.newDiamond.component.DiffEditorDialog.confirm_that_the')}</Button></div>;
        return <div>
            <Dialog title={aliwareIntl.get('com.alibaba.newDiamond.component.DiffEditorDialog.contents')} language={window.pageLanguage || 'zh-cn'} style={{ width: '80%' }} visible={this.state.dialogvisible} footer={footer} onClose={this.closeDialog.bind(this)}>
            <div style={{ height: 400 }}>
                <div>
                    <Row>
                        <Col style={{ textAlign: 'center' }}>{aliwareIntl.get('com.alibaba.newDiamond.component.DiffEditorDialog.of_the_current_area')}</Col>
                        <Col style={{ textAlign: 'center' }}>{aliwareIntl.get('com.alibaba.newDiamond.component.DiffEditorDialog.original_value')}</Col>
                    </Row>
                </div>
                <div style={{ clear: 'both', height: 480 }} ref="diffeditor"></div>
               
            </div>
            </Dialog>
        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default DiffEditorDialog;