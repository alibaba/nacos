import React from 'react'; 

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class MinusIcon extends React.Component {
    constructor(props) {
        super(props);
        this.state = {};
    }

    componentDidMount() {}

    render() {
        const circle = {
            borderRadius: '50%',
            width: '26px',
            height: '26px',
            backgroundColor: '#33cde5'
        };
        const circlePlus = {
            position: 'relative',
            backgroundColor: '#FFFFFF',
            width: '50%',
            height: '12.5%',
            left: '25%',
            top: '43.75%'
        };
        // const verticalPlus = {
        //     position: 'relative',
        //     backgroundColor: '#FFFFFF',
        //     width: '12.5%',
        //     height: '50%',
        //     left: '43.75%',
        //     top: '12.5%'
        // };
        return <div onClick={this.props.onClick} style={{
            ...circle
        }}>
            <div style={{ ...circlePlus }}></div>

        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default MinusIcon;