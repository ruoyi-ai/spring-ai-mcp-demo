<template>
  <div class="mcp-test-view">
    <el-card class="full-height-card">
      <template #header>
        <div class="card-header">
          <span>MCP 服务器连接测试</span>
          <el-button type="primary" @click="handleTestConnection" :loading="testingConnection">
            <el-icon><Connection /></el-icon>
            测试连接
          </el-button>
        </div>
      </template>

      <!-- 连接配置表单 -->
      <el-form :model="connectionForm" label-width="140px" class="connection-form">
        <el-form-item label="服务器地址" required>
          <el-input
            v-model="connectionForm.url"
            placeholder="例如: http://localhost:9899"
            clearable
          >
            <template #prepend>URL</template>
          </el-input>
        </el-form-item>

        <el-form-item label="传输类型">
          <el-radio-group v-model="connectionForm.transportType">
            <el-radio label="streamable-http">Streamable HTTP</el-radio>
            <el-radio label="sse">SSE (Server-Sent Events)</el-radio>
          </el-radio-group>
          <div class="form-hint">
            Streamable HTTP 使用 /mcp 端点，SSE 使用 /sse 端点
          </div>
        </el-form-item>

        <el-form-item label="请求头 (可选)">
          <el-input
            v-model="connectionForm.headersJson"
            type="textarea"
            :rows="3"
            placeholder='请输入 JSON 格式的请求头，例如: {"Authorization": "Bearer token"}'
          />
          <div class="form-hint">留空则不需要额外的请求头</div>
        </el-form-item>
      </el-form>

      <!-- 连接测试结果 -->
      <el-divider v-if="connectionResult">连接测试结果</el-divider>
      <div v-if="connectionResult" class="test-result">
        <el-alert
          :type="connectionResult.success ? 'success' : 'error'"
          :title="connectionResult.success ? '连接成功' : '连接失败'"
          :closable="false"
          style="margin-bottom: 10px"
        >
          <template #default>
            <div>{{ connectionResult.message || connectionResult.error }}</div>
          </template>
        </el-alert>
      </div>

      <!-- 工具列表 -->
      <el-divider>工具列表</el-divider>
      <div class="tools-section">
        <div class="tools-header">
          <el-button
            type="primary"
            @click="handleLoadTools"
            :loading="loadingTools"
            :disabled="!connectionForm.url"
          >
            <el-icon><Refresh /></el-icon>
            加载工具列表
          </el-button>
          <span v-if="tools.length > 0" class="tools-count">
            共 {{ tools.length }} 个工具
          </span>
        </div>

        <el-table
          v-if="tools.length > 0"
          :data="tools"
          border
          style="margin-top: 15px"
          max-height="400"
        >
          <el-table-column prop="name" label="工具名称" width="200" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleTestTool(row)">测试</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-else-if="!loadingTools" description="点击【加载工具列表】按钮获取工具" />
      </div>

      <!-- 工具调用测试对话框 -->
      <el-dialog
        v-model="toolTestDialogVisible"
        :title="`测试工具: ${currentTool?.name}`"
        width="800px"
        :close-on-click-modal="false"
      >
        <div v-if="currentTool" class="tool-info">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="工具名称">{{ currentTool.name }}</el-descriptions-item>
            <el-descriptions-item label="传输类型">
              <el-tag>{{ connectionForm.transportType }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">
              {{ currentTool.description || '无' }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 参数输入 -->
          <el-form :model="toolTestForm" label-width="120px" style="margin-top: 20px">
            <el-form-item label="工具参数 (JSON)">
              <el-input
                v-model="toolTestForm.argumentsJson"
                type="textarea"
                :rows="6"
                placeholder='请输入 JSON 格式的参数，例如: {"a": 1, "b": "test"}'
              />
              <div class="form-hint">
                根据工具的 inputSchema 定义输入参数。如果工具不需要参数，可以留空或输入 {}
              </div>
            </el-form-item>
          </el-form>
        </div>

        <template #footer>
          <el-button @click="toolTestDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleRunToolTest" :loading="testingTool">
            执行测试
          </el-button>
        </template>

        <!-- 工具调用结果 -->
        <el-divider v-if="toolTestResult">测试结果</el-divider>
        <div v-if="toolTestResult" class="test-result">
          <el-alert
            :type="toolTestResult.success ? 'success' : 'error'"
            :title="toolTestResult.success ? '测试成功' : '测试失败'"
            :closable="false"
            style="margin-bottom: 10px"
          />
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="工具名称">{{ toolTestResult.toolName }}</el-descriptions-item>
            <el-descriptions-item label="执行时间">{{ toolTestResult.duration || '-' }}</el-descriptions-item>
            <el-descriptions-item label="请求参数">
              <pre>{{ JSON.stringify(toolTestResult.request || {}, null, 2) }}</pre>
            </el-descriptions-item>
            <el-descriptions-item v-if="toolTestResult.success" label="响应结果">
              <pre>{{ typeof toolTestResult.response === 'string' ? toolTestResult.response : JSON.stringify(toolTestResult.response, null, 2) }}</pre>
            </el-descriptions-item>
            <el-descriptions-item v-if="!toolTestResult.success" label="错误信息">
              <pre style="color: red">{{ toolTestResult.error || toolTestResult.message }}</pre>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Refresh } from '@element-plus/icons-vue'
import { testMcpConnection, listMcpTools, testInvokeMcpTool, type McpTool } from '@/api/mcp-test'

const connectionForm = ref({
  url: 'http://localhost:9899',
  transportType: 'streamable-http' as 'sse' | 'streamable-http',
  headersJson: ''
})

const testingConnection = ref(false)
const connectionResult = ref<any>(null)

const loadingTools = ref(false)
const tools = ref<McpTool[]>([])

const toolTestDialogVisible = ref(false)
const currentTool = ref<McpTool | null>(null)
const testingTool = ref(false)
const toolTestResult = ref<any>(null)

const toolTestForm = ref({
  argumentsJson: '{}'
})

// 测试连接
const handleTestConnection = async () => {
  if (!connectionForm.value.url) {
    ElMessage.warning('请输入服务器地址')
    return
  }

  testingConnection.value = true
  connectionResult.value = null

  try {
    const headers = parseJson(connectionForm.value.headersJson)
    const res = await testMcpConnection({
      url: connectionForm.value.url,
      transportType: connectionForm.value.transportType,
      headers: headers || undefined
    })

    const result = (res as any).data || res
    connectionResult.value = result

    if (result.success) {
      ElMessage.success('连接成功')
    } else {
      ElMessage.error(result.error || '连接失败')
    }
  } catch (error: any) {
    connectionResult.value = {
      success: false,
      error: error?.response?.data?.error || error?.message || '连接失败'
    }
    ElMessage.error('连接测试失败')
  } finally {
    testingConnection.value = false
  }
}

// 加载工具列表
const handleLoadTools = async () => {
  if (!connectionForm.value.url) {
    ElMessage.warning('请输入服务器地址')
    return
  }

  loadingTools.value = true
  tools.value = []

  try {
    const headers = parseJson(connectionForm.value.headersJson)
    const res = await listMcpTools({
      url: connectionForm.value.url,
      transportType: connectionForm.value.transportType,
      headers: headers || undefined
    })

    const result = (res as any).data || res
    if (result.success && result.tools) {
      tools.value = result.tools
      ElMessage.success(`成功加载 ${result.count || 0} 个工具`)
    } else {
      ElMessage.error(result.error || '加载工具列表失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.error || error?.message || '加载工具列表失败')
  } finally {
    loadingTools.value = false
  }
}

// 测试工具
const handleTestTool = (tool: McpTool) => {
  currentTool.value = tool
  toolTestForm.value.argumentsJson = '{}'
  toolTestResult.value = null
  toolTestDialogVisible.value = true
}

// 执行工具测试
const handleRunToolTest = async () => {
  if (!currentTool.value || !connectionForm.value.url) {
    return
  }

  let argumentsObj: Record<string, any> = {}
  try {
    if (toolTestForm.value.argumentsJson.trim()) {
      argumentsObj = JSON.parse(toolTestForm.value.argumentsJson)
    }
  } catch (error) {
    ElMessage.error('参数格式错误，请输入有效的 JSON 格式')
    return
  }

  testingTool.value = true
  toolTestResult.value = null

  try {
    const headers = parseJson(connectionForm.value.headersJson)
    const res = await testInvokeMcpTool({
      url: connectionForm.value.url,
      transportType: connectionForm.value.transportType,
      toolName: currentTool.value.name,
      arguments: argumentsObj,
      headers: headers || undefined
    })

    const result = (res as any).data || res
    toolTestResult.value = result

    if (result.success) {
      ElMessage.success('工具调用成功')
    } else {
      ElMessage.error(result.error || '工具调用失败')
    }
  } catch (error: any) {
    toolTestResult.value = {
      success: false,
      error: error?.response?.data?.error || error?.message || '工具调用失败'
    }
    ElMessage.error('工具调用失败')
  } finally {
    testingTool.value = false
  }
}

// 解析 JSON
const parseJson = (jsonStr: string): Record<string, any> | null => {
  if (!jsonStr || !jsonStr.trim()) {
    return null
  }
  try {
    return JSON.parse(jsonStr)
  } catch (error) {
    return null
  }
}

onMounted(() => {
  // 可以在这里加载一些默认配置
})
</script>

<style scoped>
.mcp-test-view {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.full-height-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  margin: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.connection-form {
  margin-bottom: 20px;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.test-result {
  margin-top: 15px;
}

.test-result pre {
  margin: 0;
  padding: 10px;
  background: #f5f5f5;
  border-radius: 4px;
  overflow-x: auto;
  max-height: 300px;
  overflow-y: auto;
  font-size: 12px;
  line-height: 1.5;
}

.tools-section {
  margin-top: 20px;
}

.tools-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.tools-count {
  color: #909399;
  font-size: 14px;
}

.tool-info {
  margin-bottom: 20px;
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>

