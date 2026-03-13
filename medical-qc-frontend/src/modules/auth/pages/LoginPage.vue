<template>
  <div class="auth-layout">
    <div class="auth-container">
      <div class="auth-header">
        <h1 class="platform-title">医学影像质控平台</h1>
        <p class="subtitle">Medical Imaging Quality Control System</p>
      </div>

      <el-card class="auth-card" shadow="hover">
        <div class="card-header">
          <h2>{{ cardTitle }}</h2>
          <p class="card-hint">{{ cardHint }}</p>
        </div>

        <div v-if="showStatusTip" class="status-tip">
          {{ statusTipText }}
        </div>

        <el-form
          ref="loginFormRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="登录身份">
            <RoleSelector v-model="form.role" :options="ROLE_OPTIONS" />
          </el-form-item>

          <el-form-item label="用户名或邮箱" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名或注册邮箱"
              size="large"
              clearable
              prefix-icon="User"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              prefix-icon="Lock"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="submit-btn"
              :loading="loading"
              native-type="submit"
            >
              登录系统
            </el-button>
          </el-form-item>

          <div class="footer-links">
            <span>还没有账号？</span>
            <router-link to="/register" class="link">立即注册</router-link>
          </div>
        </el-form>
      </el-card>

      <div class="auth-footer">© 2026 医影质控平台</div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/modules/auth/api/authApi'
import RoleSelector from '@/components/auth/RoleSelector.vue'
import { DEFAULT_ROLE, ROLE_OPTIONS, isSupportedRole, saveUserInfo } from '@/utils/auth'

// 路由实例负责登录成功后的跳转；当前路由负责读取 mode、redirect 等上下文参数。
const router = useRouter()
const route = useRoute()
// 表单实例用于触发 Element Plus 的异步校验。
const loginFormRef = ref(null)
// 登录过程中的提交态，避免用户重复点击。
const loading = ref(false)

// 路由 query 中的 role 可能为空或非法，这里统一回退到默认角色。
const resolveRole = (role) => (isSupportedRole(role) ? role : DEFAULT_ROLE)

// 登录表单状态直接对应后端 LoginReq 的三个核心字段。
const form = ref({
  username: '',
  password: '',
  role: resolveRole(route.query.role),
})

// 与后端最小登录校验保持一致：用户名/邮箱和密码都不能为空。
const rules = {
  username: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// mode 由拦截器或其他页面注入，用于控制登录页提示文案。
const pageMode = computed(() => route.query.mode)
const isSwitchMode = computed(() => pageMode.value === 'switch')
const isExpiredMode = computed(() => pageMode.value === 'expired')
const isPermissionUpdatedMode = computed(() => pageMode.value === 'permission-updated')

// 卡片标题根据会话切换原因动态变化，让用户知道为何回到登录页。
const cardTitle = computed(() => {
  if (isPermissionUpdatedMode.value) {
    return '权限已更新'
  }

  return isSwitchMode.value ? '切换账号' : '用户登录'
})

// 卡片副标题描述当前登录场景，帮助用户理解身份选择和重新登录原因。
const cardHint = computed(() => {
  if (isPermissionUpdatedMode.value) {
    return '账号权限或状态已调整，请重新选择身份并登录'
  }

  if (isSwitchMode.value) {
    return '当前账号已退出，请选择新的身份并重新登录'
  }

  return '请选择身份并使用用户名或邮箱登录'
})

const showStatusTip = computed(() => isSwitchMode.value || isExpiredMode.value || isPermissionUpdatedMode.value)

// 只允许站内相对路径作为回跳目标，避免被外部地址注入。
const redirectPath = computed(() => {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return redirect.startsWith('/') ? redirect : '/'
})

// 顶部状态提示文案同样由 mode 驱动，与拦截器跳转原因保持一致。
const statusTipText = computed(() => {
  if (isPermissionUpdatedMode.value) {
    return '检测到账号权限或状态发生变化，原登录会话已失效，请重新登录。'
  }

  if (isSwitchMode.value) {
    return '已进入切换用户模式，当前会话已清理。'
  }

  return '登录状态已失效，请重新验证身份。'
})

// 提交登录表单，并把成功返回的用户写入前端缓存。
const handleLogin = async () => {
  await loginFormRef.value.validate()
  loading.value = true

  try {
    // 后端会写 Session；前端这里只缓存脱敏后的用户摘要用于菜单和页面展示。
    const response = await login(form.value)
    saveUserInfo(response)
    ElMessage.success('登录成功！')
    // 优先回跳用户原本要访问的页面，否则回到默认首页。
    router.push(redirectPath.value)
  } catch (error) {
    let errorMsg = '登录失败，请稍后重试'

    // 优先展示后端 detail，保证前后端错误提示口径一致。
    if (error.response?.data?.detail) {
      errorMsg = error.response.data.detail
    } else if (error.request) {
      // 请求已发出但没有响应，多为网络或服务不可达问题。
      errorMsg = '网络连接失败，请检查网络'
    } else if (error.message) {
      errorMsg = error.message
    }

    ElMessage.error(errorMsg)
  } finally {
    // 无论成功还是失败都要恢复按钮状态。
    loading.value = false
  }
}
</script>

<style scoped>
/* 页面整体布局。 */
.auth-layout {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf9 100%);
  padding: 20px;
}

/* 登录容器，限制最大宽度并保持居中。 */
.auth-container {
  width: 100%;
  max-width: 460px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 顶部品牌区。 */
.auth-header {
  text-align: center;
  margin-bottom: 32px;
}

/* 平台标题。 */
.platform-title {
  font-size: 28px;
  font-weight: 700;
  color: #1890ff;
  margin: 0 0 8px;
  letter-spacing: 1px;
}

/* 英文副标题。 */
.subtitle {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

/* 登录卡片主体。 */
.auth-card {
  width: 100%;
  border-radius: 16px;
  border: 1px solid #ebeef5;
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

/* 卡片头部。 */
.card-header {
  padding: 24px 24px 12px;
  text-align: center;
}

.card-header h2 {
  margin: 0;
  font-size: 22px;
  color: #303133;
}

.card-hint {
  margin: 10px 0 0;
  color: #7a8599;
  font-size: 13px;
}

/* 顶部状态提示。 */
.status-tip {
  margin: 0 24px 8px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(64, 158, 255, 0.08);
  border: 1px solid rgba(64, 158, 255, 0.16);
  color: #2f6fb2;
  font-size: 13px;
}

/* Element Plus 表单内边距。 */
:deep(.el-form) {
  padding: 0 24px 24px;
}

/* 表单项间距。 */
:deep(.el-form-item) {
  margin-bottom: 18px;
}

/* 标签样式。 */
:deep(.el-form-item__label) {
  font-weight: 500;
  color: #5a5e66;
  padding-bottom: 6px;
  font-size: 14px;
}

/* 输入框圆角。 */
:deep(.el-input__wrapper) {
  border-radius: 10px !important;
}

/* 提交按钮。 */
.submit-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
}

/* 底部注册链接。 */
.footer-links {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
  color: #606266;
  font-size: 14px;
}

.link {
  color: #1890ff;
  text-decoration: none;
  font-weight: 500;
}

.link:hover {
  color: #409eff;
}

/* 页面页脚。 */
.auth-footer {
  margin-top: 32px;
  color: #909399;
  font-size: 12px;
  text-align: center;
}
</style>


