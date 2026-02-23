<template>
  <!-- 
    @file auth/Register.vue
    @description 用户注册页面
    提供新用户注册功能，包含表单验证和 API 提交。
  -->
  <div class="auth-layout">
    <div class="auth-container">
      <!-- 
        @section 页面头部
        显示平台标题和副标题
      -->
      <div class="auth-header">
        <h1 class="platform-title">医学影像质控平台</h1>
        <p class="subtitle">Medical Imaging Quality Control System</p>
      </div>

      <el-card class="auth-card" shadow="hover">
        <div class="card-header">
          <h2>用户注册</h2>
        </div>

        <!-- 
          @section 注册表单
          功能: 收集用户注册信息
          字段: 用户名, 真实姓名, 医院, 科室, 邮箱, 密码, 确认密码
        -->
        <el-form
          :model="form"
          :rules="rules"
          ref="registerFormRef"
          label-position="top"
          @submit.prevent="handleRegister"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名（用于登录）"
              size="large"
              clearable
              prefix-icon="User"
            />
          </el-form-item>

          <el-form-item label="真实姓名" prop="full_name">
            <el-input
              v-model="form.full_name"
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

          <el-form-item label="科室" prop="department">
            <el-input
              v-model="form.department"
              placeholder="例如：放射科、影像中心"
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
              placeholder="至少6位，包含字母和数字更安全"
              size="large"
              show-password
              prefix-icon="Lock"
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirm_password">
            <el-input
              v-model="form.confirm_password"
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
              style="width: 100%;"
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

      <div class="auth-footer">
        © 2026 医学影像质控平台 · 保障医学影像质量
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Edit, OfficeBuilding, Suitcase, Message, Lock } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()
const registerFormRef = ref(null)
const loading = ref(false)

/**
 * @description 表单数据模型
 */
const form = ref({
  username: '',
  full_name: '',
  hospital: '',
  department: '',
  email: '',
  password: '',
  confirm_password: '' // 仅用于前端验证，不提交给后端
})

/**
 * @description 表单验证规则
 * 包含必填校验、邮箱格式校验、密码长度校验及两次密码一致性校验
 */
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  full_name: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  hospital: [{ required: true, message: '请输入医院名称', trigger: 'blur' }],
  department: [{ required: true, message: '请输入科室', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirm_password: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value) => {
        if (value !== form.value.password) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ]
}

/**
 * @function handleRegister
 * @description 处理注册请求
 * 
 * 逻辑:
 * 1. 验证表单数据
 * 2. 调用 /auth/register API 提交注册信息
 * 3. 成功后跳转至登录页
 * 
 * 对接API:
 * - POST /auth/register
 */
const handleRegister = async () => {
  await registerFormRef.value.validate()
  loading.value = true
  try {
    await request.post('/auth/register', {
      username: form.value.username,
      full_name: form.value.full_name,
      hospital: form.value.hospital,
      department: form.value.department,
      email: form.value.email,
      password: form.value.password
    })
    ElMessage.success('注册成功！请登录')
    router.push('/login')
  } catch (error) {
    const detail = error.response?.data?.detail || '注册失败'
    ElMessage.error(detail)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 
  @section 样式定义
  包含注册页面的布局、容器、表单卡片及底部版权信息的样式
*/
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
  max-width: 420px;
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
  border-radius: 12px;
  border: 1px solid #ebeef5;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.card-header {
  padding: 24px 24px 16px;
  text-align: center;
}

.card-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

:deep(.el-form-item) {
  margin-bottom: 16px; /* ✅ 从 20px 改为 16px，更紧凑 */
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #5a5e66;
  padding-bottom: 6px;
  font-size: 14px;
}

:deep(.el-input__wrapper) {
  border-radius: 8px !important;
  border: 1px solid #dcdfe6;
}

:deep(.el-input__inner) {
  text-align: left !important;
  padding: 10px 16px;
  font-size: 14px;
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
