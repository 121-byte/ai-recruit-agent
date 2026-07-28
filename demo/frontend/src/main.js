import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  Button,
  Card,
  ConfigProvider,
  DatePicker,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Menu,
  Modal,
  Progress,
  Select,
  Spin,
  Tag,
  Upload,
} from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)
const antdComponents = [
  Button,
  Card,
  ConfigProvider,
  DatePicker,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Menu,
  Modal,
  Progress,
  Select,
  Spin,
  Tag,
  Upload,
]

app.use(createPinia())
app.use(router)
antdComponents.forEach((component) => app.use(component))

app.mount('#app')
