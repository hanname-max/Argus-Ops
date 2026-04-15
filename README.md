# 🚀 Argus-Ops
# 一双AI眼睛，一双勤快的手，部署从此变简单

> 💡 **让部署普及普通人** - 本地掌控一切，远程只需要执行

---

## 🎯 项目初衷

很多普通人想给自己的小项目做个自动化部署，但是：

- ❌ **GitHub Actions** 对个人项目来说太重了，还要配置密钥，麻烦
- ❌ **传统方案** 把 AI 能力都扔给远程服务器，配置麻烦还不安全
- ❌ **出了错** 千里之外调试全靠瞎猜，沟通成本极高

**Argus-Ops 改变这一切** ✨

- ✅ **本地最高指挥官** - **你**的电脑掌握**所有** AI 能力，所有思考分析都在本地完成
- ✅ **远程只当工具人** - 远程服务器不需要懂 AI，不需要配置 API Key，只需要乖乖执行命令回传日志
- ✅ **错了立刻分析** - 远程报错日志立马传回到**你的本地**，本地 AI 当场帮你分析问题给修复建议
- ✅ **零复杂配置** - 填好 AI API Key 就能用，不需要搞 CI/CD 流水线，不需要云服务商

**一句话**：让普通人也能轻松享受到 AI 辅助自动化部署 🎉

---

## 🏗️ 核心架构

**严格遵循「本地主导」设计哲学**：
```
  你的本地电脑 🖥️                          远程服务器 📡
┌───────────────────────────────┐             ┌──────────────────────────────┐
│  🧠 所有 AI 能力都在这里         │             │  💻 只做执行，不需要懂 AI        │
│  🎯 环境扫描 → AI 生成计划 → 安全审计 │   SSH/RPC  │  ──────────────────────  │
│  🔍 报错回传 → 本地 AI 分析修复       │  ─────────▶  │  接收命令 → 执行 → 回传日志 │
│  👆 一切尽在掌握                 │             │                         │
└───────────────────────────────┘             └──────────────────────────────┘
```

### 📦 模块分层（严格六边形架构）

| 模块 | 职责 | 依赖规则 |
|------|------|----------|
| `aiops-domain` | 📐 领域模型 | ⚪ 只允许 Lombok，**禁止** Spring/LangChain4j |
| `aiops-application` | 🎯 用例 & 端口接口 | ⚪ 仅依赖 `aiops-domain` |
| `aiops-infrastructure` | 🔌 基础设施实现 | ⚪ 依赖 `aiops-application` + Spring + LangChain4j |
| `aiops-cli` | 🖥️ 命令行交互 | ⚪ 仅依赖 `aiops-application` |
| `aiops-bootstrap` | 🚀 启动打包 | ⚪ 依赖 `aiops-cli` + `aiops-infrastructure` |

---

## ✨ 核心特性

### 🧠 **双AI协同架构**
- 🌍 **远程AI** (Claude/OpenAI/火山方舟): 负责高难度的**架构规划和脚本生成**
- 🏠 **本地AI** (Ollama/LMStudio): 负责**扫描项目上下文** + 对远程生成内容做**安全审计**

### ⚡ **双轨执行模式**
- 🚀 优先 RPC 调用：如果目标服务器已安装 AIOps Daemon，使用长连接 RPC 执行，更快更稳定
- 🛡️ 自动降级 SSH 直连：未安装 Daemon 时**自动降级**到原生 SSH 直接执行，零配置可用

### 🔌 **多提供商热插拔**
通过配置 `aiops.remote.active-provider` 和 `aiops.local.active-provider` **动态选择模型**，无需修改代码。支持：
- ☁️ 云端: Claude (Anthropic), OpenAI GPT, Google Gemini, **字节火山方舟**, 阿里云通义千问, 百度文心一言, Mistral, DeepSeek
- 🏠 本地: Ollama, LMStudio/LocalAI

### 📡 **流式日志本地分析**
远程执行命令每一行日志**实时回传到本地**，一旦出错，**本地 AI 立刻帮忙分析**，不用你翻日志找问题 🎯

---

## 📋 前置要求

### 👉 本地运行需要：
- ☕ **JDK 21+** 项目使用 Java 21 编译
- 🔨 **Maven 3.8+** 构建工具
- 🔑 **至少一个远程 AI API Key** 推荐火山方舟 / Anthropic / OpenAI
- 🤖 **推荐一个本地 AI** Ollama 用于安全审计，也可以使用云端 AI 做审计

### 👉 目标服务器需要 (`deploy` 命令):
- 🐧 **Linux x86_64** 推荐生产环境
- 🔌 **开放 SSH 端口** 默认 `22`，允许本地连接
- 📡 **如果使用 Daemon 模式**，需要开放 **RPC 端口 `8765`** 入站
- ☕ **安装 Java 21+** Daemon 运行需要
- 🔑 **root 权限** 推荐，用于系统级安装和部署

---

## 🛠️ 编译打包

```bash
git clone <your-repo-url>
cd Argus-Ops
mvn clean package -DskipTests
```

产物位置：`aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar`

直接 `java -jar` 就能跑 🎉

---

## ⚙️ 配置

编辑 `aiops-bootstrap/src/main/resources/application.yml`:

```yaml
aiops:
  # 🔮 远程 AI 配置（负责架构规划和脚本生成）
  remote:
    active-provider: claude
    api-key: ${ANTHROPIC_API_KEY:your-anthropic-api-key-here}
    base-url: https://ark.cn-beijing.volces.com/api/coding/v3
    model-name: ark-code-latest
    temperature: 0.7
    max-tokens: 4096

  # 🛡️ 本地 AI 配置（负责上下文扫描和安全审计）
  local:
    active-provider: ollama
    base-url: http://localhost:11434
    model-name: llama3
    temperature: 0.3
    max-tokens: 2048

# 🚫 禁止启动 Web 服务器（这是命令行应用，只需要RestTemplate客户端功能）
spring:
  main:
    web-application-type: none
```

> 💡 配置就是这么简单！更多提供商示例看文件内注释 📖

---

## 🚀 快速开始

### 📐 生成部署计划

```bash
# 查看帮助
java -jar aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar --help

# 交互式扫描（推荐）
java -jar aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar plan

# 直接指定项目路径
java -jar aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar plan --path /path/to/your/project
```

**输出示例**：
```
Scanning project at: /home/you/your-project
========================================================

  Result:
  ========================================

  📋 Architecture Summary:
  这是一个 Spring Boot + React 全栈应用...

  🐳 Generated Dockerfile:
  ```dockerfile
  FROM openjdk:21-jdk-slim
  ...
  ```

  🛡️ Security Audit: **PASSED**

Done!
```

### 🚀 自动化远程部署

```bash
# 🔐 使用密码认证
java -jar aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar deploy \
  --host 1.2.3.4 \
  --username root \
  --password your-ssh-password \
  --workdir /opt/your-app \
  --command "git pull && mvn clean package && systemctl restart your-app"

# 🔑 使用私钥认证（推荐）
java -jar aiops-bootstrap/target/aiops-bootstrap-1.0.0-SNAPSHOT.jar deploy \
  --host 1.2.3.4 \
  --username root \
  --private-key ~/.ssh/id_rsa \
  --workdir /opt/your-app \
  --command "./deploy.sh"
```

**自动化魔法流程** ✨：
1. 🔍 探测目标服务器 `8765` 端口检测 AIOps Daemon 是否运行
2. ❓ 如果未运行，**交互式询问**你是否自动安装
3. ⚙️ 自动安装：创建目录 → SCP 上传 jar → 配置 systemd 服务 → 启动
4. ▶️ 执行你指定的部署命令
5. 📄 输出命令结果 → 出错自动触发本地 AI 分析 → 给你修复建议
6. 🏁 完事退出

---

## 🔌 端口说明

| 用途 | 默认端口 | 是否需要开放 | 说明 |
|------|---------|-------------|------|
| SSH 连接 | `22` | ✅ **必须开放** | 用于初始连接和命令执行 |
| AIOps Daemon RPC | `8765` | ⚠️ 需要开放（如果使用 Daemon）| 后续执行加速 |

---

## 🎳 支持的 AI 提供商

| 提供商 | 适用模式 | 配置说明 |
|--------|----------|----------|
| 🎉 **字节火山方舟** | 远程推荐 | `active-provider: claude` + `base-url: https://ark.cn-beijing.volces.com/api/coding/v3` |
| Anthropic Claude | 远程/本地 | `active-provider: claude` |
| OpenAI | 远程/本地 | `active-provider: openai` |
| Ollama | 本地推荐 | `active-provider: ollama` + `base-url: http://localhost:11434` |
| Google Gemini | 远程 | `active-provider: gemini` |
| 阿里云通义千问 | 远程 | `active-provider: dashscope` |
| 百度文心一言 | 远程 | `active-provider: wenxin` |
| LMStudio | 本地 | `active-provider: lmstudio` |

---

## 📚 技术栈

- ☕ Java 21
- 🚀 Spring Boot 3.2.x
- 🧠 LangChain4j
- 🖥️ Picocli (命令行框架)
- 🔐 JSch (SSH 连接)
- 🔨 Maven

---

## 🎉 最后

> **"给本地一双慧眼，给远程一双手，部署从此行云流水"**

Happy Deploying! 🚀
