import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alifd/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ProblemOrientation extends React.Component {
    constructor(props) {
        super(props);
    }

    componentDidMount() {
        window.require.config({ paths: { 'vs': '//midwayfe.oss-cn-shanghai.aliyuncs.com/monaco-editor/min/vs' } });
        window.require(['vs/editor/editor.main'], () => {
            monaco.editor.defineTheme('myTheme', {
                base: 'vs',
                inherit: true,
                rules: [{ background: 'EDF9FA' }],
                colors: {
                    'editor.foreground': '#000000',
                    'editor.background': '#EDF9FA',
                    'editorCursor.foreground': '#8B0000',
                    'editor.lineHighlightBackground': '#0000FF20',
                    'editorLineNumber.foreground': '#008800',
                    'editor.selectionBackground': '#88000030',
                    'editor.inactiveSelectionBackground': '#88000015'
                }
            });
            monaco.editor.setTheme('myTheme');
            monaco.editor.create(document.getElementById("container"), {
                value: "My to-do list:\n* buy milk\n* buy coffee\n* write awesome code",
                language: "text/plain",
                fontFamily: "Arial",
                fontSize: 20
            });
        });
    }
    render() {

        return <div>
            <div id="container" style={{ width: 800, height: 600, border: 'none' }}></div>
        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ProblemOrientation;