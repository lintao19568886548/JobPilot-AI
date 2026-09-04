import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { elementComponents, ElLoading } from './plugins/elementPlus.js'
import router from './router/index.js'
import './assets/main.css'

const app = createApp(App)
elementComponents.forEach((component) => app.component(component.name, component))
app.use(ElLoading).use(createPinia()).use(router).mount('#app')
