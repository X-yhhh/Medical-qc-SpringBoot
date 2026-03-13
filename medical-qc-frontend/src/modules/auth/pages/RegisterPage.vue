<template>
  <div class="auth-layout">
    <div class="auth-container">
      <div class="auth-header">
        <h1 class="platform-title">医学影像质控平台</h1>
        <p class="subtitle">Medical Imaging Quality Control System</p>
      </div>

      <el-card class="auth-card" shadow="hover">
        <div class="card-header">
          <h2>用户注册</h2>
          <p class="card-hint">先选择身份，再完善账号与机构信息</p>
        </div>

        <el-form
          ref="registerFormRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleRegister"
        >
          <el-form-item label="注册身份">
            <RoleSelector v-model="form.role" :options="ROLE_OPTIONS" />
            <div class="role-guide">{{ roleGuide }}</div>
          </el-form-item>

          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名（用于登录）"
              size="large"
              clearable
              prefix-icon="User"
            />
          </el-form-item>

          <el-form-item label="真实姓名" prop="fullName">
            <el-input
              v-model="form.fullName"
              placeholder="请输入您的真实姓名"
              size="large"
              clearable
              prefix-icon="Edit"
            />
          </el-form-item>

          <el-form-item label="医院" prop="hospital">
            <el-input
              v-model="form.hospital"
              placeholder="请输入所属医院"
              size="large"
              clearable
              prefix-icon="OfficeBuilding"
            />
          </el-form-item>

          <el-form-item :label="departmentLabel" prop="department">
            <el-input
              v-model="form.department"
              :placeholder="departmentPlaceholder"
              size="large"
              clearable
              prefix-icon="Suitcase"
            />
          </el-form-item>

          <el-form-item label="邮箱" prop="email">
            <el-input
              v-model="form.email"
              placeholder="请输入常用邮箱"
              size="large"
              clearable
              prefix-icon="Message"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="至少6位，建议包含字母和数字"
              size="large"
              show-password
              prefix-icon="Lock"
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
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
              注册账号
            </el-button>
          </el-form-item>

          <div class="footer-links">
            <span>已有账号？</span>
            <router-link to="/login" class="link">立即登录</router-link>
          </div>
        </el-form>
      </el-card>

      <div class="auth-footer">© 2026 医学影像质控平台 · 保障医学影像质量</div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/modules/auth/api/authApi'
import RoleSelector from '@/components/auth/RoleSelector.vue'
import { DEFAULT_ROLE, ROLE_OPTIONS } from '@/utils/auth'

// 注册成功后需要跳转到登录页，因此这里只需要路由 push 能力。
const router = useRouter()
// 表单实例用于触发 Element Plus 校验。
const registerFormRef = ref(null)
// 提交锁，防止重复注册。
const loading = ref(false)

// 注册表单字段与后端 RegisterReq 一一对应，confirmPassword 只用于前端校验。
const form = ref({
  role: DEFAULT_ROLE,
  username: '',
  fullName: '',
  hospital: '',
  department: '',
  email: '',
  password: '',
  confirmPassword: '',
})

// 医生身份会影响科室字段的必填性和页面文案。
const isDoctorRole = computed(() => form.value.role === 'doctor')

// 角色说明文案跟随所选身份变化，减少用户误选。
const roleGuide = computed(() => {
  return isDoctorRole.value
    ? '医生账号用于执行影像质控、病例审核与结果确认。'
    : '管理员账号用于管理账号、流程配置与系统运营。'
})

// 管理员没有严格意义上的临床科室，因此标签采用更宽泛的描述。
const departmentLabel = computed(() => (isDoctorRole.value ? '科室' : '管理单元（选填）'))

const departmentPlaceholder = computed(() => {
  return isDoctorRole.value ? '例如：放射科、影像中心' : '例如：医务处、质控办'
})

// 医生身份必须填写科室，管理员则允许留空。
const validateDepartment = (_, value) => {
  if (isDoctorRole.value && !value?.trim()) {
    return Promise.reject('医生身份需要填写科室')
  }

  return Promise.resolve()
}

// 确认密码只做前端体验校验，后端只接收 password。
const validateConfirmPassword = (_, value) => {
  if (value !== form.value.password) {
    return Promise.reject('两次输入的密码不一致')
  }

  return Promise.resolve()
}

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  fullName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  hospital: [{ required: true, message: '请输入医院名称', trigger: 'blur' }],
  department: [{ validator: validateDepartment, trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

// 提交注册表单，并在成功后把所选角色带回登录页。
const handleRegister = async () => {
  await registerFormRef.value.validate()
  loading.value = true

  try {
    // 请求体只提交后端需要的字段，confirmPassword 不参与接口传输。
    await register({
      username: form.value.username,
      role: form.value.role,
      fullName: form.value.fullName,
      hospital: form.value.hospital,
      department: form.value.department,
      email: form.value.email,
      password: form.value.password,
    })

    ElMessage.success('注册成功！请登录')
    // 回到登录页时保留角色，减少用户再次切换身份的成本。
    router.push({ path: '/login', query: { role: form.value.role } })
  } catch (error) {
    // 注册错误提示以服务端 detail 为准，保证唯一性校验文案一致。
    const detail = error.response?.data?.detail || '注册失败'
    ElMessage.error(detail)
  } finally {
    // 最终恢复按钮态。
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

/* 注册容器。 */
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

/* 注册卡片主体。 */
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

/* Element Plus 表单内边距。 */
:deep(.el-form) {
  padding: 0 24px 24px;
}

/* 表单项间距。 */
:deep(.el-form-item) {
  margin-bottom: 16px;
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

/* 角色说明文字。 */
.role-guide {
  margin-top: 10px;
  color: #6b778c;
  font-size: 12px;
  line-height: 1.6;
}

/* 提交按钮。 */
.submit-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
}

/* 底部登录链接。 */
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
