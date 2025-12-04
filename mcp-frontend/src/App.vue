<template>
  <el-container>
    <el-header>
      <el-menu
        mode="horizontal"
        :default-active="activeMenu"
        router
        class="header-menu"
      >
        <el-menu-item index="/tools">
          <el-icon><Tools /></el-icon>
          <span>工具管理</span>
        </el-menu-item>
        <el-menu-item index="/markets">
          <el-icon><Shop /></el-icon>
          <span>市场管理</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 聊天</span>
        </el-menu-item>
        <el-menu-item index="/mcp-test">
          <el-icon><Connection /></el-icon>
          <span>MCP 测试</span>
        </el-menu-item>
      </el-menu>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Tools, Shop, ChatDotRound, Connection } from '@element-plus/icons-vue'

const route = useRoute()

const activeMenu = computed(() => {
  return route.path.startsWith('/tools') ? '/tools' :
         route.path.startsWith('/markets') ? '/markets' :
         route.path.startsWith('/chat') ? '/chat' :
         route.path.startsWith('/mcp-test') ? '/mcp-test' : '/tools'
})
</script>

<style scoped>
.header-menu {
  border-bottom: 1px solid #e6e6e6;
}

.el-header {
  padding: 0;
  height: 60px;
  line-height: 60px;
}

.el-main {
  padding: 0;
  height: calc(100vh - 60px);
  overflow: auto;
}
</style>

<style>
.el-container {
  height: 100vh;
}

/* 确保下拉框弹出层正确显示 */
.el-select-dropdown,
.el-popper {
  z-index: 9999 !important;
}

/* 修复下拉选项可能不显示的问题 */
.el-select-dropdown__item {
  display: block !important;
  visibility: visible !important;
}
</style>
