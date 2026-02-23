<!-- 
  @file src/App.vue
  @description 根组件
  主要功能：
  1. 应用的顶层容器。
  2. 渲染 <router-view> 以承载页面内容。
-->

<template>
  <div class="app">
    <router-view />
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
}
</style>
