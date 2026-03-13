<template>
  <div class="user-management-page">
    <div class="page-header">
      <div>
        <h2>用户与权限</h2>
        <p>管理员可在此查看账号状态、调整身份权限，并维护机构档案信息。</p>
      </div>
      <el-tag type="primary" effect="dark">管理员工作台</el-tag>
    </div>

    <el-row :gutter="20" class="summary-row">
      <el-col :span="6" v-for="item in summaryCards" :key="item.label">
        <el-card class="summary-card" shadow="hover">
          <div class="summary-icon" :class="item.type">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div class="summary-content">
            <div class="summary-label">{{ item.label }}</div>
            <div class="summary-value">{{ item.value }}</div>
            <div class="summary-extra">{{ item.extra }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="filter-card" shadow="never">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索用户名、姓名、邮箱、医院或科室"
          clearable
          class="toolbar-item keyword-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="filters.role" clearable placeholder="身份筛选" class="toolbar-item" @change="handleSearch">
          <el-option v-for="option in ROLE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select v-model="filters.active" clearable placeholder="状态筛选" class="toolbar-item" @change="handleSearch">
          <el-option label="启用中" :value="true" />
          <el-option label="已停用" :value="false" />
        </el-select>
        <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
        <el-button icon="Refresh" @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">账号列表</span>
          <span class="header-extra">共 {{ pagination.total }} 个账号</span>
        </div>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading" class="user-table">
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="fullName" label="姓名" min-width="110">
          <template #default="{ row }">
            <span>{{ row.fullName || '--' }}</span>
            <el-tag v-if="row.id === currentUserId" size="small" type="info" effect="plain" class="self-tag">当前账号</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="roleLabel" label="身份" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'warning' : 'primary'" effect="plain">{{ row.roleLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hospital" label="医院" min-width="160" show-overflow-tooltip />
        <el-table-column prop="department" label="科室/单元" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.department || '--' }}</template>
        </el-table-column>
        <el-table-column prop="isActive" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" effect="plain">{{ row.isActive ? '启用中' : '已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" align="center" />
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑权限</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :current-page="pagination.page"
          :page-size="pagination.limit"
          :page-sizes="[10, 20, 30]"
          :total="pagination.total"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="编辑用户权限" width="640px" destroy-on-close>
      <div class="dialog-profile">
        <div class="dialog-profile__item"><span class="label">用户名</span><span>{{ editForm.username }}</span></div>
        <div class="dialog-profile__item"><span class="label">邮箱</span><span>{{ editForm.email }}</span></div>
      </div>

      <el-form ref="formRef" :model="editForm" :rules="rules" label-position="top">
        <el-form-item label="账号身份">
          <RoleSelector v-model="editForm.role" :options="ROLE_OPTIONS" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="fullName">
          <el-input v-model="editForm.fullName" placeholder="请输入真实姓名" clearable />
        </el-form-item>
        <el-form-item label="医院" prop="hospital">
          <el-input v-model="editForm.hospital" placeholder="请输入医院名称" clearable />
        </el-form-item>
        <el-form-item :label="editForm.role === 'doctor' ? '科室' : '管理单元（选填）'" prop="department">
          <el-input v-model="editForm.department" placeholder="请输入科室或管理单元" clearable />
        </el-form-item>
        <el-form-item label="账号状态">
          <div class="status-switch-row">
            <el-switch v-model="editForm.isActive" inline-prompt active-text="启用" inactive-text="停用" />
            <span class="status-tip">停用后该账号将无法登录系统。</span>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">保存修改</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * @file admin/UserManagement.vue
 * @description 管理员“用户与权限”页面。
 * 提供账号列表查看、身份切换、启停控制与基础档案维护能力。
 */

import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAdminUsers, updateAdminUser } from '@/modules/admin-user/api/adminUserApi'
import { logout } from '@/modules/auth/api/authApi'
import RoleSelector from '@/components/auth/RoleSelector.vue'
import { clearAuthState, getStoredUserInfo, ROLE_OPTIONS } from '@/utils/auth'

const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

const filters = ref({
  keyword: '',
  role: '',
  active: undefined,
})

const pagination = ref({
  page: 1,
  limit: 10,
  total: 0,
})

const summary = ref({
  totalUsers: 0,
  activeUsers: 0,
  inactiveUsers: 0,
  adminUsers: 0,
  doctorUsers: 0,
})

const tableData = ref([])

const editForm = ref({
  id: null,
  username: '',
  email: '',
  fullName: '',
  hospital: '',
  department: '',
  role: 'doctor',
  isActive: true,
  originalRole: 'doctor',
  originalIsActive: true,
})

const currentUserId = computed(() => getStoredUserInfo()?.id || null)

/**
 * 页面顶部统计卡片。
 */
const summaryCards = computed(() => [
  {
    label: '账号总数',
    value: summary.value.totalUsers,
    extra: '系统已注册全部账号',
    icon: 'User',
    type: 'primary',
  },
  {
    label: '启用账号',
    value: summary.value.activeUsers,
    extra: `${summary.value.inactiveUsers} 个账号当前停用`,
    icon: 'CircleCheck',
    type: 'success',
  },
  {
    label: '管理员',
    value: summary.value.adminUsers,
    extra: '负责系统治理与权限配置',
    icon: 'Setting',
    type: 'warning',
  },
  {
    label: '医生',
    value: summary.value.doctorUsers,
    extra: '负责日常影像质控与审核',
    icon: 'Avatar',
    type: 'info',
  },
])

const rules = {
  fullName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  hospital: [{ required: true, message: '请输入医院名称', trigger: 'blur' }],
  department: [
    {
      validator: (_, value) => {
        if (editForm.value.role === 'doctor' && !value?.trim()) {
          return Promise.reject('医生身份需要填写科室')
        }

        return Promise.resolve()
      },
      trigger: 'blur',
    },
  ],
}

/**
 * 读取用户分页数据。
 */
const loadUsers = async () => {
  loading.value = true

  try {
    const response = await getAdminUsers({
      page: pagination.value.page,
      limit: pagination.value.limit,
      keyword: filters.value.keyword || undefined,
      role: filters.value.role || undefined,
      active: filters.value.active,
    })

    tableData.value = Array.isArray(response?.items) ? response.items : []
    pagination.value.total = response?.total || 0
    summary.value = {
      ...summary.value,
      ...(response?.summary || {}),
    }
  } catch (error) {
    console.error('加载用户列表失败', error)
    ElMessage.error(error.response?.data?.detail || '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

/**
 * 打开编辑弹窗并回填表单。
 *
 * @param {Object} row 表格当前行数据
 */
const openEditDialog = (row) => {
  editForm.value = {
    id: row.id,
    username: row.username,
    email: row.email,
    fullName: row.fullName || '',
    hospital: row.hospital || '',
    department: row.department || '',
    role: row.role,
    isActive: row.isActive,
    originalRole: row.role,
    originalIsActive: row.isActive,
  }
  dialogVisible.value = true
}

/**
 * 判断本次保存是否涉及权限变更。
 * 仅当前登录账号的角色或启停状态发生变化时，才要求重新登录。
 *
 * @returns {boolean} 是否发生权限变更
 */
const hasPermissionChanged = () => {
  if (!currentUserId.value || String(editForm.value.id) !== String(currentUserId.value)) {
    return false
  }

  return editForm.value.role !== editForm.value.originalRole
    || editForm.value.isActive !== editForm.value.originalIsActive
}

/**
 * 清理当前会话并返回登录页。
 */
const redirectToRelogin = async () => {
  try {
    await logout()
  } catch (error) {
    console.warn('登出接口调用失败，已执行本地清理。', error)
  }

  clearAuthState()
  await router.push({ path: '/login', query: { mode: 'permission-updated' } })
}

/**
 * 保存管理员对目标用户的修改。
 */
const handleSubmit = async () => {
  await formRef.value.validate()
  submitting.value = true
  const permissionChanged = hasPermissionChanged()

  try {
    await updateAdminUser(editForm.value.id, {
      fullName: editForm.value.fullName,
      hospital: editForm.value.hospital,
      department: editForm.value.department,
      role: editForm.value.role,
      isActive: editForm.value.isActive,
    })

    ElMessage.success(permissionChanged ? '权限已更新，请重新登录后继续操作' : '用户信息已更新')
    dialogVisible.value = false

    if (permissionChanged) {
      await redirectToRelogin()
      return
    }

    await loadUsers()
  } catch (error) {
    console.error('更新用户失败', error)
    ElMessage.error(error.response?.data?.detail || '更新用户失败')
  } finally {
    submitting.value = false
  }
}

/**
 * 执行筛选查询。
 */
const handleSearch = () => {
  pagination.value.page = 1
  loadUsers()
}

/**
 * 重置全部筛选条件。
 */
const resetFilters = () => {
  filters.value = {
    keyword: '',
    role: '',
    active: undefined,
  }
  handleSearch()
}

/**
 * 处理分页页码切换。
 *
 * @param {number} page 新页码
 */
const handlePageChange = (page) => {
  pagination.value.page = page
  loadUsers()
}

/**
 * 处理分页大小切换。
 *
 * @param {number} size 每页条数
 */
const handlePageSizeChange = (size) => {
  pagination.value.limit = size
  pagination.value.page = 1
  loadUsers()
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-management-page {
  min-height: calc(100vh - 60px);
  padding: 24px;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 20px 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 24px;
  color: #303133;
}

.page-header p {
  margin: 0;
  color: #606266;
  font-size: 14px;
}

.summary-row {
  margin-bottom: 20px;
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 118px;
}

.summary-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  color: #fff;
  font-size: 24px;
}

.summary-icon.primary { background: linear-gradient(135deg, #409eff, #69b1ff); }
.summary-icon.success { background: linear-gradient(135deg, #67c23a, #95d475); }
.summary-icon.warning { background: linear-gradient(135deg, #e6a23c, #f3c66d); }
.summary-icon.info { background: linear-gradient(135deg, #909399, #b1b3b8); }

.summary-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-label {
  color: #606266;
  font-size: 14px;
}

.summary-value {
  color: #303133;
  font-size: 28px;
  font-weight: 700;
}

.summary-extra {
  color: #909399;
  font-size: 12px;
}

.filter-card,
.table-card {
  border-radius: 12px;
  margin-bottom: 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-item {
  width: 180px;
}

.keyword-input {
  width: 320px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.header-extra {
  color: #909399;
  font-size: 13px;
}

.self-tag {
  margin-left: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.dialog-profile {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f7faff;
}

.dialog-profile__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #303133;
}

.dialog-profile__item .label {
  color: #909399;
  font-size: 12px;
}

.status-switch-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-tip {
  color: #909399;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .summary-row :deep(.el-col) {
    margin-bottom: 16px;
  }
}

@media (max-width: 768px) {
  .page-header,
  .toolbar,
  .status-switch-row {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-item,
  .keyword-input {
    width: 100%;
  }

  .dialog-profile {
    grid-template-columns: 1fr;
  }
}
</style>

