import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from '@/App.vue'
import router from '@/app/router'
import { registerGlobalIcons } from '@/app/bootstrap/registerGlobalIcons'
import { setRouter } from '@/utils/request'

// 创建前端应用实例，并串联请求层、路由层和全局组件注册流程。
export const createVueApp = () => {
  // 根组件负责承载整个路由视图树。
  const app = createApp(App)

  // 把路由实例注入到请求层，便于 401/403 时在拦截器内执行跳转。
  setRouter(router)

  // 全局注册 Element Plus 图标，避免每个页面单独 import。
  registerGlobalIcons(app)

  // 按启动顺序注册 UI 组件库、全局状态和路由系统。
  app.use(ElementPlus)
  app.use(createPinia())
  app.use(router)

  // 返回已完成基础装配的应用实例，交由 main.js 挂载。
  return app
}
