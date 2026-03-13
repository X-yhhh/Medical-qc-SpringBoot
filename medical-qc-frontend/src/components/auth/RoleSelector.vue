<template>
  <div class="role-selector" role="radiogroup" aria-label="身份选择">
    <!-- 每个角色卡片都通过 update:modelValue 回写角色编码，父组件据此切换表单逻辑。 -->
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="role-card"
      :class="{ active: modelValue === option.value }"
      @click="$emit('update:modelValue', option.value)"
    >
      <!-- 左侧图标区用于快速识别医生/管理员两种身份。 -->
      <div class="role-card__icon">
        <el-icon><component :is="option.icon" /></el-icon>
      </div>
      <!-- 中间文案区展示角色标题和职责描述。 -->
      <div class="role-card__content">
        <span class="role-card__title">{{ option.label }}</span>
        <span class="role-card__desc">{{ option.description }}</span>
      </div>
      <!-- 右侧勾选态仅在当前角色被选中时显示。 -->
      <div class="role-card__check">
        <el-icon><Select /></el-icon>
      </div>
    </button>
  </div>
</template>

<script setup>
import { Select } from '@element-plus/icons-vue'

// 当前组件只负责角色选择 UI，本身不持久化状态，完全由父组件通过 v-model 驱动。
defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  options: {
    type: Array,
    default: () => [],
  },
})

// 仅暴露 update:modelValue，一个事件即可支撑登录页和注册页的双向绑定。
defineEmits(['update:modelValue'])
</script>

<style scoped>
/* 双列卡片布局，在桌面端同时展示两个角色。 */
.role-selector {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

/* 单个角色卡片。 */
.role-card {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 92px;
  padding: 16px 14px;
  border: 1px solid #d9e6f5;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  box-shadow: 0 10px 24px rgba(24, 144, 255, 0.08);
  cursor: pointer;
  text-align: left;
  transition: all 0.22s ease;
}

/* hover 态强化卡片可点击反馈。 */
.role-card:hover {
  border-color: #91caff;
  transform: translateY(-2px);
  box-shadow: 0 14px 28px rgba(64, 158, 255, 0.16);
}

/* 激活态强调当前选中的角色。 */
.role-card.active {
  border-color: #409eff;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.16), rgba(255, 255, 255, 0.98));
  box-shadow: 0 16px 32px rgba(64, 158, 255, 0.22);
}

/* 图标容器。 */
.role-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: linear-gradient(135deg, #409eff 0%, #69b1ff 100%);
  color: #ffffff;
  font-size: 20px;
  flex-shrink: 0;
}

/* 文案垂直排布。 */
.role-card__content {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

/* 角色名称。 */
.role-card__title {
  color: #1f2d3d;
  font-size: 16px;
  font-weight: 600;
}

/* 角色职责描述。 */
.role-card__desc {
  color: #6b778c;
  font-size: 12px;
  line-height: 1.5;
}

/* 右侧选中提示圆点。 */
.role-card__check {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-left: auto;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.12);
  color: #409eff;
  opacity: 0;
  transform: scale(0.85);
  transition: all 0.2s ease;
}

.role-card.active .role-card__check {
  opacity: 1;
  transform: scale(1);
}

/* 小屏下切换为单列，避免卡片挤压。 */
@media (max-width: 520px) {
  .role-selector {
    grid-template-columns: 1fr;
  }
}
</style>
