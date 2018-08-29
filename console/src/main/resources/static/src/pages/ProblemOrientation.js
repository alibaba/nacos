import React from 'react'; 

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