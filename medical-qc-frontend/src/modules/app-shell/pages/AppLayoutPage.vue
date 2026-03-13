<!-- AppLayoutPage.vue -->
<!--
  @file AppLayoutPage.vue
  @description 全局布局组件。
  主要功能：
  1. 提供统一的头部与侧边栏框架。
  2. 根据当前登录角色动态展示菜单。
  3. 支持切换用户、退出登录等全局操作。
-->
<template>
  <el-container style="height: 100vh">
    <el-header class="header">
      <div class="header-left">医学影像质控平台</div>
      <div class="header-right">
        <el-dropdown @command="handleCommand" placement="bottom-end">
          <span class="user-info">
            <el-icon><User /></el-icon>
            <span class="username">{{ currentUser }}</span>
            <span class="role-badge">{{ currentRoleLabel }}</span>
            <el-icon class="arrow"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="switch-user">
                <el-icon><RefreshRight /></el-icon>
                <span>切换用户</span>
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>
                <span>退出登录</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container>
      <el-aside width="220px" style="background: #333; color: white">
        <el-menu
          router
          background-color="#333"
          text-color="#fff"
          active-text-color="#409EFF"
          :default-active="$route.path"
          class="sidebar-menu"
        >
          <template v-for="section in visibleMenus" :key="section.index">
            <el-menu-item v-if="section.type === 'item'" :index="section.index">
              <el-icon><component :is="section.icon" /></el-icon>
              <span>{{ section.label }}</span>
            </el-menu-item>

            <el-sub-menu v-else :index="section.index">
              <template #title>
                <el-icon><component :is="section.icon" /></el-icon>
                <span>{{ section.label }}</span>
              </template>
              <el-menu-item v-for="child in section.children" :key="child.index" :index="child.index">
                {{ child.label }}
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-aside>

      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, RefreshRight, SwitchButton, User } from '@element-plus/icons-vue'
import { logout } from '@/modules/auth/api/authApi'
import { clearAuthState, getStoredUserInfo } from '@/utils/auth'
import { getMenusByRole } from '@/utils/permission'

const router = useRouter()

/**
 * 当前登录用户信息。
 */
const currentUserInfo = computed(() => getStoredUserInfo())

/**
 * 当前头部展示用户名。
 */
const currentUser = computed(() => {
  return currentUserInfo.value?.username || currentUserInfo.value?.fullName || '用户'
})

/**
 * 当前头部展示角色名称。
 */
const currentRoleLabel = computed(() => {
  return currentUserInfo.value?.roleLabel || '医生'
})

/**
 * 根据当前身份过滤左侧菜单。
 */
const visibleMenus = computed(() => getMenusByRole(currentUserInfo.value?.role))

/**
 * 清理会话并跳转登录页。
 *
 * @param {string} mode 登录页模式
 * @param {string} message 操作成功提示
 */
const redirectToLogin = async (mode, message) => {
  try {
    await logout()
  } catch (error) {
    console.warn('登出接口调用失败，已执行本地清理。', error)
  }

  clearAuthState()
  await router.push({ path: '/login', query: { mode } })
  ElMessage.success(message)
}

/**
 * 处理右上角用户菜单操作。
 *
 * @param {string} command 菜单命令
 */
const handleCommand = async (command) => {
  if (command === 'switch-user') {
    await redirectToLogin('switch', '已切换到登录页，请使用其他账号登录')
    return
  }

  if (command === 'logout') {
    await redirectToLogin('logout', '已退出登录')
  }
}
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #409eff;
  color: white;
  padding: 0 20px;
  font-size: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.header-left {
  font-weight: bold;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: white;
  font-size: 14px;
}

.user-info .el-icon {
  margin-right: 0;
}

.username {
  font-weight: 600;
}

.role-badge {
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.92);
  font-size: 12px;
  line-height: 20px;
}

.arrow {
  margin-left: 4px;
  font-size: 12px;
}

.sidebar-menu :deep(.el-sub-menu__title) {
  font-weight: normal;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 50px;
  line-height: 50px;
}
</style>
