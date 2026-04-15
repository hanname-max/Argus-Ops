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
- **远程AI** (Claude/OpenAI/火山方舟): 负责高难度的架构规划和脚本生成
- **本地AI** (Ollama/LMStudio): 负责扫描项目上下文并对远程生成内容做安全审计

**双轨执行模式**：
- 优先RPC调用：如果目标服务器已安装AIOps Daemon，使用长连接RPC执行，更快更稳定
- 自动降级SSH直连：未安装Daemon时自动降级到原生SSH执行

**多提供商动态装配**：
通过配置 `aiops.remote.active-provider` 和 `aiops.local.active-provider` 动态选择模型，无需修改代码。支持：
- 云端: Claude (Anthropic), OpenAI GPT, Google Gemini, 字节火山方舟, 阿里云通义千问, 百度文心一言, Mistral, DeepSeek
- 本地: Ollama, LMStudio/LocalAI

## 前置要求

### 本地运行需要：
- **JDK 21+（项目使用Java 21编译）
- **Maven 3.8+（构建工具）
- **至少一个远程AI API Key**（推荐火山方舟/Anthropic/OpenAI）
- **推荐一个本地AI**（Ollama，用于安全审计，也可以使用云端AI做审计）

### 目标服务器需要（`deploy` 命令）：
- **Linux x86_64**（推荐）
- **开放SSH端口**（默认 `22`），允许本地出网连接
- **如果使用Daemon模式**，需要开放 **RPC端口 `8765`** 入站
- **安装Java 21+**（Daemon运行需要）
- **root权限**（推荐，用于系统级安装和部署）

## 编译打包

```bash
git clone <repo-url>
cd Argus-Ops
mvn clean package -DskipTests
```

产物为 `aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar`，可直接通过 `java -jar` 运行。

## 配置

编辑 `aiops-bootstrap/src/main/resources/application.yml`：

```yaml
aiops:
  # 远程AI配置（负责架构规划和脚本生成）
  remote:
    active-provider: claude
    api-key: your-api-key-here
    base-url: https://ark.cn-beijing.volces.com/api/coding/v3
    model-name: ark-code-latest
    temperature: 0.7
    max-tokens: 4096

  # 本地AI配置（负责安全审计）
  local:
    active-provider: ollama
    base-url: http://localhost:11434
    model-name: llama3
    temperature: 0.3
    max-tokens: 2048

# 禁止启动Web服务器（命令行应用模式）
spring:
  main:
    web-application-type: none
```

> 💡 **支持的提供商配置详见文件内注释，可任意组合。

## 本地部署使用

```bash
# 查看帮助
java -jar aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar --help

# 扫描当前目录生成部署计划
java -jar aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar plan

# 指定项目路径扫描
java -jar aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar plan --path /path/to/your/project
```

输出示例：
- 检测项目语言和框架
- AI生成架构总结
- AI生成完整Dockerfile
- AI安全审计检查安全问题

## 远程部署（deploy 命令）

项目自带自动化部署功能，可以自动部署你的应用到远程服务器：

```bash
# 使用密码认证
java -jar aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar deploy \
  --host 1.2.3.4 \
  --username root \
  --password your-ssh-password \
  --workdir /opt/your-app \
  --command "git pull && mvn clean package && systemctl restart your-app

# 使用私钥认证（推荐）
java -jar aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar deploy \
  --host 1.2.3.4 \
  --username root \
  --private-key ~/.ssh/id_rsa \
  --workdir /opt/your-app \
  --command "./deploy.sh"
```

**自动化流程**：
1. 探测目标服务器 `8765` 端口检测AIOps Daemon是否运行
2. 如果未运行，提示用户是否自动安装
3. 自动安装：创建目录 → SCP上传jar → 配置systemd服务 → 启动
4. 执行用户指定的部署命令
5. 输出命令结果并退出

**目标服务器端口要求**：
| 用途 | 默认端口 | 是否需要开放 | 说明 |
|------|---------|-------------|------|
| SSH连接 | `22` | ✅ 必须开放 | 用于初始连接和命令执行 |
| AIOps Daemon RPC | `8765` | ⚠️ 需要开放（如果使用Daemon）| 后续加速执行 |

## 完整部署架构图

```
  Local Machine                          Target Server
┌─────────────────────────┐             ┌─────────────────────────┐
│  AIOps CLI             │             │                         │
│                         │  SSH/SCP    │  AIOps Daemon (option)  │
│  plan → generate plan  │  ─────────▶  listens on :8765     │
│  deploy → execute cmd   │  RPC/SSH    │  Your application     │
└─────────────────────────┘             └─────────────────────────┘
```

## 支持的AI提供商

| 提供商 | 模式 | 配置示例 |
|--------|------|----------|
| 字节跳动火山方舟 | 远程 | `active-provider: claude` + `base-url: https://ark.cn-beijing.volces.com/api/coding/v3` |
| Anthropic Claude | 远程/本地 | `active-provider: claude` |
| OpenAI | 远程/本地 | `active-provider: openai` |
| Ollama | 本地推荐 | `active-provider: ollama` + `base-url: http://localhost:11434` |
| Google Gemini | 远程 | `active-provider: gemini` |
| 阿里云通义千问 | 远程 | `active-provider: dashscope` |
| 百度文心一言 | 远程 | `active-provider: wenxin` |
| LMStudio | 本地 | `active-provider: lmstudio` |

## 技术栈

- Java 21
- Spring Boot 3.2.x
- LangChain4j
- Picocli (命令行框架)
- JSch (SSH连接)
- Maven
