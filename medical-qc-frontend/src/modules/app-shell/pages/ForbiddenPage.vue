<template>
  <div class="forbidden-page">
    <el-card class="forbidden-card" shadow="hover">
      <div class="forbidden-illustration">403</div>
      <h2 class="forbidden-title">当前页面仅限对应身份访问</h2>
      <p class="forbidden-desc">
        你已登录，但当前账号没有打开该页面的权限。请返回首页，或切换为具备权限的账号后重试。
      </p>
      <div class="forbidden-actions">
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button plain @click="goLogin">切换账号</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
/**
 * @file errors/Forbidden.vue
 * @description 403 无权限访问页面。
 * 提供统一的页面级权限拦截反馈，避免用户进入空白或报错页面。
 */

import { useRouter } from 'vue-router'
import { clearAuthState } from '@/utils/auth'
import { getCurrentRole, resolveDefaultRouteForRole } from '@/utils/permission'

const router = useRouter()

/**
 * 返回当前角色的默认首页。
 */
const goHome = () => {
  router.push(resolveDefaultRouteForRole(getCurrentRole()))
}

/**
 * 清理登录状态并跳转登录页，便于用户切换身份。
 */
const goLogin = () => {
  clearAuthState()
  router.push({ path: '/login', query: { mode: 'switch' } })
}
</script>

<style scoped>
.forbidden-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf9 100%);
  padding: 24px;
}

.forbidden-card {
  width: 100%;
  max-width: 520px;
  text-align: center;
  border-radius: 18px;
  border: 1px solid #e4ebf5;
  box-shadow: 0 16px 40px rgba(24, 144, 255, 0.1);
}

.forbidden-illustration {
  margin-top: 8px;
  font-size: 68px;
  line-height: 1;
  font-weight: 700;
  color: #409eff;
  letter-spacing: 2px;
}

.forbidden-title {
  margin: 18px 0 12px;
  font-size: 24px;
  color: #303133;
}

.forbidden-desc {
  margin: 0 auto;
  max-width: 360px;
  color: #606266;
  font-size: 14px;
  line-height: 1.8;
}

.forbidden-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin: 28px 0 8px;
}
</style>
