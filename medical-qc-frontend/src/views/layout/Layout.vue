<!-- src/views/layout/Layout.vue -->
<!-- 
  @file layout/Layout.vue
  @description 全局布局组件
  主要功能：
  1. 系统整体布局框架（Header + Sider + Main）。
  2. 顶部导航栏：展示系统标题、当前登录用户信息、退出登录功能。
  3. 左侧侧边栏：提供系统功能的路由导航菜单。
  4. 主体区域：承载路由视图 (<router-view>)，动态渲染业务页面。

  @api Mocks/Local
  - SessionStorage: 读取 'user_info' 用于展示用户名，读取 'access_token' 用于判断登录状态（虽在此组件不直接判断，但退出时会清除）。
-->
<template>
  <el-container style="height: 100vh">
    <!-- 
      @section 顶部导航栏 (Header)
      功能: 
      - 展示系统名称 "医学影像质控平台"
      - 右侧展示当前登录用户信息及下拉菜单
    -->
    <el-header class="header">
      <div class="header-left">医学影像质控平台</div>
      <div class="header-right">
        <el-dropdown @command="handleCommand" placement="bottom-end">
          <span class="user-info">
            <el-icon><User /></el-icon>
            <span class="username">{{ currentUser }}</span>
            <el-icon class="arrow"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout" icon="switch-button"> 退出登录 </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <!-- 主体区域 -->
    <el-container>
      <!-- 
        @section 左侧侧边栏 (Sidebar)
        功能: 提供系统功能导航菜单
        组件: el-menu (开启 router 模式)
        对接路由: 
        - /dashboard: 首页仪表盘
        - /quality-group: 影像质控分组
          - /head: CT头部平扫
          - /hemorrhage: 头部出血检测
          - /chest-non-contrast: CT胸部平扫
          - /chest-contrast: CT胸部增强
          - /coronary-cta: 冠脉CTA
        - /issues: 异常数据汇总
      -->
      <el-aside width="220px" style="background: #333; color: white">
        <el-menu
          router
          background-color="#333"
          text-color="#fff"
          active-text-color="#409EFF"
          :default-active="$route.path"
          class="sidebar-menu"
        >
          <el-menu-item index="/dashboard">
            <el-icon><House /></el-icon>
            <span>首页</span>
          </el-menu-item>

          <el-sub-menu index="quality-group">
            <template #title>
              <el-icon><DocumentRemove /></el-icon>
              <span>影像质控</span>
            </template>
            <el-menu-item index="/head">CT头部平扫</el-menu-item>
            <el-menu-item index="/hemorrhage">头部出血检测</el-menu-item>
            <el-menu-item index="/chest-non-contrast">CT胸部平扫</el-menu-item>
            <el-menu-item index="/chest-contrast">CT胸部增强</el-menu-item>
            <el-menu-item index="/coronary-cta">冠脉CTA</el-menu-item>
          </el-sub-menu>

          <el-menu-item index="/issues">
            <el-icon><Warning /></el-icon>
            <span>异常汇总</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- 
        @section 页面内容区 (Main Content)
        功能: 渲染当前路由对应的业务视图组件
      -->
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
/**
 * @section Imports
 */
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { computed } from 'vue'
import { User, ArrowDown, House, DocumentRemove, Warning } from '@element-plus/icons-vue'

/**
 * @section Initialization
 */
const router = useRouter()

/**
 * @function handleCommand
 * @description 处理下拉菜单指令（如退出登录）
 * @param {string} command - 菜单指令 ('logout')
 * 
 * 逻辑:
 * 1. 判断指令是否为 'logout'
 * 2. 清除 sessionStorage 中的 'access_token' 和 'user_info'
 * 3. 弹出成功提示消息
 * 4. 强制跳转至登录页 '/login'
 */
const handleCommand = (command) => {
  if (command === 'logout') {
    sessionStorage.removeItem('access_token')
    sessionStorage.removeItem('user_info')
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

/**
 * @function currentUser
 * @description 获取当前登录用户名
 * @returns {string} 用户名或默认值 '用户'
 * 
 * 逻辑:
 * 1. 从 sessionStorage 获取 'user_info' 字符串
 * 2. 解析 JSON 并进行安全类型检查
 * 3. 优先返回 username，其次 full_name，否则返回 '用户'
 * 4. 包含异常处理，防止 JSON 解析错误导致页面崩溃
 */
const currentUser = computed(() => {
  const userInfoStr = sessionStorage.getItem('user_info')

  // ✅ 先判断是否为有效字符串
  if (!userInfoStr || typeof userInfoStr !== 'string') {
    return '用户'
  }

  try {
    const userInfo = JSON.parse(userInfoStr)

    // ✅ 再判断解析结果是否为对象且不为 null
    if (userInfo && typeof userInfo === 'object' && !Array.isArray(userInfo)) {
      return userInfo.username || userInfo.full_name || '用户'
    } else {
      return '用户'
    }
  } catch (e) {
    console.warn('user_info 解析失败:', e)
    return '用户'
  }
})
</script>

<style scoped>
/* 
  @section 样式定义
  包含头部、侧边栏和用户信息的样式
*/
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
  cursor: pointer;
  color: white;
  font-size: 14px;
}
.user-info .el-icon {
  margin-right: 6px;
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
