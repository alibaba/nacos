import dva from 'dva';
import '@alifd/next/dist/next.css';
import './index.css';
import './index.less';

// 1. Initialize
const app = dva();

// 2. Plugins
// app.use({});

// 3. Model
// app.model(require('./models/example').default);
app.model(require('./models/error').default);
app.model(require('./models/loading').default);
// 4. Router
app.router(require('./router').default);

// 5. Start
app.start('#root');
