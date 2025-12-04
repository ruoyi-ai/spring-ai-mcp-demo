import request from '@/utils/request'

export interface McpTestConnectionRequest {
  url: string
  transportType?: 'sse' | 'streamable-http'
  headers?: Record<string, string>
}

export interface McpTestListToolsRequest extends McpTestConnectionRequest {}

export interface McpTestInvokeToolRequest extends McpTestConnectionRequest {
  toolName: string
  arguments?: Record<string, any>
}

export interface McpTool {
  name: string
  description?: string
  inputSchema?: any
}

export interface ApiResponse<T = any> {
  success: boolean
  message?: string
  error?: string
  errorType?: string
  data?: T
  [key: string]: any
}

// 测试 MCP 服务器连接
export function testMcpConnection(params: McpTestConnectionRequest) {
  return request.post<ApiResponse>('/mcp/test/connection', params)
}

// 获取 MCP 服务器工具列表
export function listMcpTools(params: McpTestListToolsRequest) {
  return request.post<ApiResponse<{ tools: McpTool[]; count: number }>>('/mcp/test/tools/list', params)
}

// 测试调用 MCP 工具
export function testInvokeMcpTool(params: McpTestInvokeToolRequest) {
  return request.post<ApiResponse>('/mcp/test/tools/invoke', params)
}

