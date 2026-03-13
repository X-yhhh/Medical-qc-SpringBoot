import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from '@/App.vue'
import router from '@/app/router'
import { registerGlobalIcons } from '@/app/bootstrap/registerGlobalIcons'
import { setRouter } from '@/utils/request'

export const createVueApp = () => {
  const app = createApp(App)

  setRouter(router)

  registerGlobalIcons(app)

  app.use(ElementPlus)
  app.use(createPinia())
  app.use(router)

  return app
}
