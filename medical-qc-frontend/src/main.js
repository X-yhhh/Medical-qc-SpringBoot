// src/main.js
/**
 * @file src/main.js
 * @description 前端入口文件
 * 主要功能：
 * 1. 初始化 Vue 应用实例。
 * 2. 注册全局插件（Pinia, Router, Element Plus）。
 * 3. 注册 Element Plus 图标组件。
 * 4. 解决路由与 Axios 拦截器的循环依赖问题。
 */

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import request, { setRouter } from '@/utils/request' // 引入 setRouter

// 引入 Element Plus 及其样式
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const app = createApp(App)

// ----------------------------------------------------------------------------------
// 核心逻辑：解决循环依赖
// 将 router 实例注入到 request 模块中，以便在拦截器中使用 router.push 跳转
// ----------------------------------------------------------------------------------
setRouter(router)

// ----------------------------------------------------------------------------------
// 注册 Element Plus 图标
// 遍历所有图标组件并注册为全局组件
// ----------------------------------------------------------------------------------
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// ----------------------------------------------------------------------------------
// 插件挂载与应用启动
// ----------------------------------------------------------------------------------
app.use(ElementPlus)
app.use(createPinia())
app.use(router)
app.mount('#app')
