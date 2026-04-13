# Project Memory - AIOps-Engine

## 配置信息

### MCP IDEA 集成
- MCP 服务器地址: `http://localhost:64342/sse`
- 项目路径: `D:\codenew\GM\Argus-Ops`
- 配置位置: `C:\Users\admin\.claude\settings.json`

## 项目信息
- 项目名称: AIOps-Engine
- 描述: 双AI协同自动化运维辅助引擎
- 架构: 六边形架构，分层清晰
- 主要技术: Java 21, Spring Boot 3.2.8, LangChain4j 0.36.2, Picocli

## 支持的AI提供商

### 国际
- Anthropic Claude
- OpenAI GPT
- Ollama (本地)
- LMStudio/LocalAI (本地)

### 国内
- 阿里云 通义千问 (DashScope)
- 百度 文心一言 (QianFan)
- DeepSeek 深度求索
- 字节跳动 豆包 (Doubao)

## 修复历史
- 2026-04-13: 修复 LangChain4j 0.36.2 API 兼容性问题（构建器类名变化）
- 2026-04-13: LMStudio 改用 OpenAiChatModel（兼容OpenAI API）
- 2026-04-13: 添加四大国内大模型支持，全部支持自定义 baseUrl
- 2026-04-13: 配置 IDEA MCP 服务器项目路径

## 编译状态
- 最后编译: ✅ SUCCESS
- 无编译错误
