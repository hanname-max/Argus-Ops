# AIOps-Engine

双AI协同自动化运维辅助工具 - Phase 1 (CLI版本)

## 架构设计

严格遵循六边形架构（端口与适配器模式），依赖关系不可违背：

| 模块 | 职责 | 允许依赖 |
|------|------|----------|
| `aiops-domain` | 领域模型 | 只有 Lombok，**禁止**Spring/LangChain4j |
| `aiops-application` | 用例与端口接口 | 仅 `aiops-domain` |
| `aiops-infrastructure` | 基础设施实现 | `aiops-application` + Spring + LangChain4j |
| `aiops-cli` | 命令行交互 | 仅 `aiops-application` |
| `aiops-bootstrap` | 启动与打包 | `aiops-cli` + `aiops-infrastructure` |

## 核心特性

**双AI协同架构**：
- **远程AI** (Claude/OpenAI): 负责高难度的架构规划和脚本生成
- **本地AI** (Ollama/LMStudio): 负责扫描项目上下文并对远程生成内容做安全审计

**多提供商动态装配**：
通过配置 `aiops.remote.active-provider` 和 `aiops.local.active-provider` 动态选择模型，无需修改代码。支持：
- 远程: Claude (Anthropic), OpenAI GPT
- 本地: Ollama, LMStudio/LocalAI

## 编译打包

```bash
mvn clean package
```

产物为 `aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar`，可直接通过 `java -jar` 运行。

## 配置

在 `application.yml` 中配置：

```yaml
aiops:
  remote:
    active-provider: claude
    api-key: ${ANTHROPIC_API_KEY}
    model-name: claude-3-5-sonnet-20241022
  local:
    active-provider: ollama
    base-url: http://localhost:11434
    model-name: llama3
```

## 使用方法

```bash
# 扫描当前目录生成部署计划
java -jar aiops-bootstrap-*.jar plan

# 指定项目路径
java -jar aiops-bootstrap-*.jar plan --path /path/to/your/project
```

## 技术栈

- Java 21
- Spring Boot 3.2.x
- LangChain4j
- Picocli
- Maven
