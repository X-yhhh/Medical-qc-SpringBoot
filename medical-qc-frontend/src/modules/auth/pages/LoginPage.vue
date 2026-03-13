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

const router = useRouter()
const route = useRoute()
const loginFormRef = ref(null)
const loading = ref(false)

const resolveRole = (role) => (isSupportedRole(role) ? role : DEFAULT_ROLE)

const form = ref({
  username: '',
  password: '',
  role: resolveRole(route.query.role),
})

const rules = {
  username: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const pageMode = computed(() => route.query.mode)
const isSwitchMode = computed(() => pageMode.value === 'switch')
const isExpiredMode = computed(() => pageMode.value === 'expired')
const isPermissionUpdatedMode = computed(() => pageMode.value === 'permission-updated')

const cardTitle = computed(() => {
  if (isPermissionUpdatedMode.value) {
    return '权限已更新'
  }

  return isSwitchMode.value ? '切换账号' : '用户登录'
})

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

const redirectPath = computed(() => {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return redirect.startsWith('/') ? redirect : '/'
})

const statusTipText = computed(() => {
  if (isPermissionUpdatedMode.value) {
    return '检测到账号权限或状态发生变化，原登录会话已失效，请重新登录。'
  }

  if (isSwitchMode.value) {
    return '已进入切换用户模式，当前会话已清理。'
  }

  return '登录状态已失效，请重新验证身份。'
})

const handleLogin = async () => {
  await loginFormRef.value.validate()
  loading.value = true

  try {
    const response = await login(form.value)
    saveUserInfo(response)
    ElMessage.success('登录成功！')
    router.push(redirectPath.value)
  } catch (error) {
    let errorMsg = '登录失败，请稍后重试'

    if (error.response?.data?.detail) {
      errorMsg = error.response.data.detail
    } else if (error.request) {
      errorMsg = '网络连接失败，请检查网络'
    } else if (error.message) {
      errorMsg = error.message
    }

    ElMessage.error(errorMsg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-layout {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf9 100%);
  padding: 20px;
}

.auth-container {
  width: 100%;
  max-width: 460px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.auth-header {
  text-align: center;
  margin-bottom: 32px;
}

.platform-title {
  font-size: 28px;
  font-weight: 700;
  color: #1890ff;
  margin: 0 0 8px;
  letter-spacing: 1px;
}

.subtitle {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.auth-card {
  width: 100%;
  border-radius: 16px;
  border: 1px solid #ebeef5;
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

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

.status-tip {
  margin: 0 24px 8px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(64, 158, 255, 0.08);
  border: 1px solid rgba(64, 158, 255, 0.16);
  color: #2f6fb2;
  font-size: 13px;
}

:deep(.el-form) {
  padding: 0 24px 24px;
}

:deep(.el-form-item) {
  margin-bottom: 18px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #5a5e66;
  padding-bottom: 6px;
  font-size: 14px;
}

:deep(.el-input__wrapper) {
  border-radius: 10px !important;
}

.submit-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
}

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

.auth-footer {
  margin-top: 32px;
  color: #909399;
  font-size: 12px;
  text-align: center;
}
</style>


