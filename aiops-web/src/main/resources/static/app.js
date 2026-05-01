(function () {
  const STORAGE_KEY = "argus_single_jar_ui_v1";
  const apiBase = "/api";
  let eventSource = null;

  const defaultState = {
    phase: 1,
    workflowId: "",
    stateVersion: null,
    stepStatus: { 1: "idle", 2: "idle", 3: "idle" },
    backendHealth: "checking",
    analysisDirty: false,
    configDirty: false,
    probeDirty: false,
    localContext: null,
    localWarnings: [],
    targetProfile: null,
    probeDecision: null,
    dependencyProbeResults: [],
    dependencyDecisions: [],
    probeWarnings: [],
    dependencyTesting: {},
    dependencyDeploying: {},
    dependencyConfigs: {},
    dependencyIgnored: {},
    needsPortConfirm: false,
    portResolutionMessage: "",
    scriptRaw: "",
    scriptMeta: null,
    streamStatus: "idle",
    streamMessage: "",
    terminalMode: "idle",
    terminalSummary: "",
    terminalLines: [],
    inferredExitCode: 0,
    form: {
      projectPath: "",
      targetHost: "",
      sshPort: 22,
      username: "root",
      password: "",
      connectTimeoutMillis: 5000,
      applicationPort: 8080,
      useSudo: true,
      sudoPassword: ""
    }
  };

  const state = loadState();

  const labels = {
    idle: "未开始",
    running: "处理中",
    success: "已完成",
    warning: "待确认",
    error: "失败"
  };

  const app = document.getElementById("app");

  function init() {
    app.innerHTML = shell();
    bindGlobalEvents();
    syncFormFields();
    renderAll();
    checkHealth();
    window.addEventListener("beforeunload", closeStream);
  }

  function shell() {
    return `
      <div class="app-shell">
        <header class="topbar">
          <div class="brand">
            <div class="brand-mark">A</div>
            <div class="brand-copy">
              <h1>Argus-Ops</h1>
              <p>Single-Jar Deployment Studio</p>
            </div>
          </div>
          <div class="topbar-badges">
            <span class="pill"><strong>模式</strong> 单进程同源</span>
            <span class="pill"><span class="health-dot" id="healthDot"></span><strong id="healthText">检查中</strong></span>
          </div>
        </header>

        <div class="layout">
          <aside class="sidebar panel">
            <div id="phaseList" class="phase-list"></div>
            <div class="sidebar-note">
              <h3>单 Jar 工作流</h3>
              <p>用户只需要运行一个 <code>jar</code>，页面和 API 同时可用。扫描、探测、部署在一个工作台里连续完成，不再需要单独启动前端服务。</p>
            </div>
          </aside>

          <section class="main-column">
            <section class="hero panel">
              <span class="hero-eyebrow">Light Workspace</span>
              <h2>把部署过程变得像对话一样顺滑，但保留工程师需要的控制感。</h2>
              <p>这里是面向真实项目的部署工作台。你输入目录、检查远端环境、生成部署预览，然后直接执行。整个体验只依赖这一个本地 Jar。</p>
              <div class="hero-grid">
                <div class="hero-stat">
                  <label>运行方式</label>
                  <strong>One Jar</strong>
                </div>
                <div class="hero-stat">
                  <label>主流程</label>
                  <strong>3 Phases</strong>
                </div>
                <div class="hero-stat">
                  <label>接口模式</label>
                  <strong>Same Origin</strong>
                </div>
              </div>
            </section>

            <section class="phase-panel panel" id="phasePanel"></section>
          </section>

          <aside class="monitor panel">
            <div class="monitor-head">
              <div>
                <h3 id="monitorTitle">运行监视</h3>
                <p id="monitorHint">当前阶段还未开始。</p>
              </div>
              <span class="pill"><strong id="monitorMode">Standby</strong></span>
            </div>
            <div class="monitor-body" id="monitorBody"></div>
          </aside>
        </div>
      </div>
    `;
  }

  function bindGlobalEvents() {
    app.addEventListener("click", handleClick);
    app.addEventListener("input", handleInput);
    app.addEventListener("change", handleInput);
  }

  function handleInput(event) {
    const configId = event.target.getAttribute("data-config-id");
    if (configId) {
      updateRuntimeConfigItem(configId, event.target.value);
      return;
    }

    const configChoice = event.target.getAttribute("data-config-choice");
    if (configChoice && state.localContext) {
      state.localContext.confirmedConfigChoice = event.target.value;
      state.configDirty = true;
      persist();
      return;
    }

    const dependencyKind = event.target.getAttribute("data-dependency-kind");
    const configKey = event.target.getAttribute("data-config-key");

    if (dependencyKind && configKey) {
      updateDependencyConfig(dependencyKind, configKey, event.target.value);
      return;
    }

    if (dependencyKind && !configKey) {
      updateDependencyDecision(dependencyKind, event.target.value);
      return;
    }

    const field = event.target.getAttribute("data-field");
    if (!field) {
      return;
    }
    const previousValue = state.form[field];
    state.form[field] = event.target.type === "checkbox" ? event.target.checked : event.target.value;
    if (field === "projectPath" && normalizeProjectPath(previousValue) !== normalizeProjectPath(state.form.projectPath)) {
      resetForProjectPathChange();
      persist();
      renderAll();
      return;
    }
    if (["targetHost", "sshPort", "username", "password", "connectTimeoutMillis", "applicationPort"].includes(field)) {
      state.probeDirty = true;
    }
    persist();
  }

  function updateDependencyConfig(kind, key, value) {
    state.dependencyConfigs = state.dependencyConfigs || {};
    state.dependencyConfigs[kind] = state.dependencyConfigs[kind] || {};

    if (key === "port") {
      state.dependencyConfigs[kind][key] = value ? Number(value) : null;
    } else {
      state.dependencyConfigs[kind][key] = value;
    }

    state.probeDirty = true;
    persist();
  }

  function handleClick(event) {
    const target = event.target.closest("[data-action], [data-phase]");
    if (!target) {
      return;
    }

    if (target.hasAttribute("data-phase")) {
      const nextPhase = Number(target.getAttribute("data-phase"));
      if (canGoTo(nextPhase)) {
        state.phase = nextPhase;
        renderAll();
      }
      return;
    }

    const action = target.getAttribute("data-action");
    const dependencyKind = target.getAttribute("data-dependency-kind");

    if (action === "scan") {
      void scanProject();
    } else if (action === "probe") {
      void probeTarget();
    } else if (action === "preview") {
      void generatePreview();
    } else if (action === "deploy") {
      void deployProject();
    } else if (action === "diagnose") {
      void diagnoseLastFailure();
    } else if (action === "test-dependency" && dependencyKind) {
      void testDependencyConnection(dependencyKind);
    } else if (action === "deploy-dependency" && dependencyKind) {
      void deployDependency(dependencyKind);
    } else if (action === "ignore-dependency" && dependencyKind) {
      toggleDependencyIgnore(dependencyKind);
    }
  }

  function toggleDependencyIgnore(kind) {
    state.dependencyIgnored = state.dependencyIgnored || {};
    state.dependencyIgnored[kind] = !state.dependencyIgnored[kind];

    if (state.dependencyIgnored[kind]) {
      pushLine(`[INFO] 已忽略 ${kind} 依赖检查`, "info");
    } else {
      pushLine(`[INFO] 已取消忽略 ${kind} 依赖检查`, "info");
    }

    persist();
    renderAll();
  }

  function renderProbeWarnings() {
    const warnings = state.probeWarnings || [];
    if (!warnings.length) {
      return "";
    }

    const importantWarnings = warnings.filter(w => w.severity !== "INFO");

    if (!importantWarnings.length) {
      return "";
    }

    return `
      <div class="warning-list" style="margin-top: 18px;">
        ${importantWarnings.map((warning) => {
          const severityClass = getWarningSeverityClass(warning.severity);
          return `
            <div class="warning-card ${severityClass}">
              <strong>${escapeHtml(warning.code)}</strong>
              <p>${escapeHtml(warning.message)}</p>
            </div>
          `;
        }).join("")}
      </div>
    `;
  }

  function getWarningSeverityClass(severity) {
    if (!severity) return "";
    switch (severity) {
      case "INFO": return "info";
      case "LOW": return "info";
      case "MEDIUM": return "";
      case "HIGH": return "";
      case "CRITICAL": return "critical";
      default: return "";
    }
  }

  async function testDependencyConnection(kind) {
    if (!state.form.targetHost) {
      pushLine("[ERROR] 请先配置目标服务器地址", "error");
      return;
    }

    state.dependencyTesting = state.dependencyTesting || {};
    state.dependencyTesting[kind] = true;
    renderAll();

    try {
      pushLine(`[INFO] 正在测试 ${kind} 连接...`, "info");

      await syncProbeTarget({ force: true });

      const result = findDependencyProbeResult(kind);
      if (result) {
        if (result.status === "READY") {
          pushLine(`[SUCCESS] ${kind} 连接成功: ${result.message}`, "success");
        } else if (result.status === "NOT_FOUND" || result.status === "UNREACHABLE") {
          pushLine(`[WARN] ${kind} 连接失败: ${result.message}`, "warning");
          pushLine(`[INFO] 准备探测可用端口并自动部署...`, "info");
        } else {
          pushLine(`[INFO] ${kind} 状态: ${result.status} - ${result.message}`, "info");
        }
      } else {
        pushLine(`[INFO] 未找到 ${kind} 的探测结果`, "info");
      }
    } catch (error) {
      pushLine(`[ERROR] 测试 ${kind} 连接失败: ${error.message}`, "error");
    } finally {
      state.dependencyTesting[kind] = false;
      renderAll();
    }
  }

  async function deployDependency(kind) {
    if (!state.form.targetHost) {
      pushLine("[ERROR] 请先配置目标服务器地址", "error");
      return;
    }

    state.dependencyDeploying = state.dependencyDeploying || {};
    state.dependencyDeploying[kind] = true;
    renderAll();

    try {
      pushLine(`[INFO] 正在部署 ${kind}...`, "info");

      updateDependencyDecision(kind, "DEPLOY_AUTOMATICALLY");

      await syncProbeTarget({ force: true });

      const result = findDependencyProbeResult(kind);
      if (result) {
        if (result.status === "READY") {
          pushLine(`[SUCCESS] ${kind} 已就绪: ${result.message}`, "success");
        } else {
          pushLine(`[INFO] ${kind} 状态: ${result.status} - ${result.message}`, "info");
          pushLine(`[INFO] 依赖将在主部署流程中自动配置`, "info");
        }
      }

      pushLine(`[SUCCESS] ${kind} 部署决策已记录为 DEPLOY_AUTOMATICALLY`, "success");
      pushLine(`[INFO] 继续主部署流程时将自动创建 ${kind} 实例`, "info");
    } catch (error) {
      pushLine(`[ERROR] 部署 ${kind} 失败: ${error.message}`, "error");
    } finally {
      state.dependencyDeploying[kind] = false;
      renderAll();
    }
  }

  function renderAll() {
    persist();
    renderPhaseList();
    renderPhasePanel();
    renderMonitor();
    syncFormFields();
  }

  function renderPhaseList() {
    const phaseList = document.getElementById("phaseList");
    const phases = [
      { id: 1, title: "本地准备", desc: "扫描项目目录，识别栈与默认端口。" },
      { id: 2, title: "远端探测", desc: "检查 SSH、系统画像、Docker 和端口情况。" },
      { id: 3, title: "部署执行", desc: "预览部署脚本并直接执行远程部署。" }
    ];

    phaseList.innerHTML = phases.map((phase) => `
      <button
        class="phase-card ${state.phase === phase.id ? "active" : ""}"
        data-phase="${phase.id}"
        ${canGoTo(phase.id) ? "" : "disabled"}>
        <div class="phase-card-head">
          <span class="phase-index">${phase.id}</span>
          <span class="phase-status ${state.stepStatus[phase.id]}">${labels[state.stepStatus[phase.id]] || labels.idle}</span>
        </div>
        <p class="phase-title">${phase.title}</p>
        <p class="phase-desc">${phase.desc}</p>
      </button>
    `).join("");
  }

  function renderPhasePanel() {
    const phasePanel = document.getElementById("phasePanel");
    phasePanel.innerHTML = state.phase === 1
      ? renderPreparePhase()
      : state.phase === 2
        ? renderProbePhase()
        : renderDeployPhase();
  }

  function renderPreparePhase() {
    return `
      <div class="section-head">
        <div>
          <h3>本地准备</h3>
          <p>先输入项目目录。系统会读取本地配置，判断项目类型和默认端口，再决定你后面需要部署什么。</p>
        </div>
      </div>

      <div class="form-grid">
        <div class="field full">
          <label for="projectPath">项目目录</label>
          <input id="projectPath" data-field="projectPath" type="text" placeholder="例如 D:/workspace/demo">
        </div>
      </div>

      ${state.localContext ? `
        <div class="card-grid" style="margin-top: 18px;">
          <div class="info-card">
            <small>Project</small>
            <strong>${escapeHtml(state.localContext.projectName || "Unknown")}</strong>
          </div>
          <div class="info-card">
            <small>Default Port</small>
            <strong>${escapeHtml(String(state.localContext.defaultApplicationPort || 8080))}</strong>
          </div>
          <div class="info-card">
            <small>Language</small>
            <strong>${escapeHtml(state.localContext.primaryLanguage || "Unknown")}</strong>
          </div>
          <div class="info-card">
            <small>Framework</small>
            <strong>${escapeHtml(state.localContext.primaryFramework || "Generic")}</strong>
          </div>
          <div class="info-card">
            <small>Build Tool</small>
            <strong>${escapeHtml(state.localContext.buildTool || "Unknown")}</strong>
          </div>
          <div class="info-card">
            <small>Packaging</small>
            <strong>${escapeHtml(state.localContext.packaging || "Generic")}</strong>
          </div>
        </div>

        ${(state.localContext.stackComponents || []).length ? `
          <div style="margin-top: 18px;">
            <div class="stack-tags">
              ${(state.localContext.stackComponents || []).map((item) => `<span>${escapeHtml(item.name)}${item.version ? " " + escapeHtml(item.version) : ""}</span>`).join("")}
            </div>
          </div>
        ` : ""}

        ${renderConfigChoiceSection()}
        ${renderRuntimeConfigSection()}
      ` : ""}

      ${(state.localWarnings || []).length ? `
        <div class="warning-list" style="margin-top: 18px;">
          ${(state.localWarnings || []).map((warning) => `
            <div class="warning-card">
              <strong>${escapeHtml(warning.code)}</strong>
              <p>${escapeHtml(warning.message)}</p>
            </div>
          `).join("")}
        </div>
      ` : ""}

      <div class="button-row">
        <button class="btn primary ${state.stepStatus[1] === "running" ? "loading" : ""}" data-action="scan">扫描项目</button>
        <button class="btn secondary" data-phase="2" ${canGoTo(2) ? "" : "disabled"}>继续</button>
        <div class="grow-note">先完成本地识别，再进入远端探测。这个阶段不依赖额外的前端服务。</div>
      </div>
    `;
  }

  function renderProbePhase() {
    return `
      <div class="section-head">
        <div>
          <h3>远端探测</h3>
          <p>这里做真实 SSH 探测，检查主机系统、Docker、可用端口和后续部署环境。</p>
        </div>
      </div>

      <div class="form-grid">
        <div class="field full">
          <label for="targetHost">服务器地址</label>
          <input id="targetHost" data-field="targetHost" type="text" placeholder="例如 39.106.139.246">
        </div>
        <div class="field">
          <label for="sshPort">SSH 端口</label>
          <input id="sshPort" data-field="sshPort" type="number" placeholder="22">
        </div>
        <div class="field">
          <label for="connectTimeoutMillis">连接超时 (ms)</label>
          <input id="connectTimeoutMillis" data-field="connectTimeoutMillis" type="number" placeholder="5000">
        </div>
        <div class="field">
          <label for="username">用户名</label>
          <input id="username" data-field="username" type="text" placeholder="root">
        </div>
        <div class="field">
          <label for="password">密码</label>
          <input id="password" data-field="password" type="password" placeholder="输入 SSH 密码">
        </div>
        <div class="field full">
          <label for="applicationPort">应用端口</label>
          <input id="applicationPort" data-field="applicationPort" type="number" placeholder="8080">
        </div>
      </div>

      ${state.needsPortConfirm && state.probeDecision?.recommendedAvailablePort ? `
        <div class="warning-card" style="margin-top: 18px;">
          <strong>端口冲突</strong>
          <p>请求端口已被占用。建议使用 ${escapeHtml(String(state.probeDecision.recommendedAvailablePort))}。</p>
          <div class="button-row" style="margin-top: 14px;">
            <button class="btn warn" data-action="use-port">采用推荐端口</button>
          </div>
        </div>
      ` : ""}

      ${state.portResolutionMessage ? `
        <div class="warning-card" style="margin-top: 18px;">
          <strong>Port Resolution</strong>
          <p>${escapeHtml(state.portResolutionMessage)}</p>
        </div>
      ` : ""}

      ${state.targetProfile ? `
        <div class="card-grid" style="margin-top: 18px;">
          <div class="info-card">
            <small>Host</small>
            <strong>${escapeHtml(state.targetProfile.hostname || state.targetProfile.host || "Unknown")}</strong>
          </div>
          <div class="info-card">
            <small>Operating System</small>
            <strong>${escapeHtml(state.targetProfile.osFamily || "Unknown")}</strong>
          </div>
          <div class="info-card">
            <small>Docker</small>
            <strong>${state.targetProfile.dockerInstalled ? escapeHtml(state.targetProfile.dockerVersion || "Installed") : "Not Installed"}</strong>
          </div>
          <div class="info-card">
            <small>Sudo</small>
            <strong>${state.targetProfile.canUseSudo ? "Available" : "Unavailable"}</strong>
          </div>
          <div class="info-card">
            <small>Recommended Port</small>
            <strong>${escapeHtml(String(state.probeDecision?.recommendedAvailablePort || state.form.applicationPort))}</strong>
          </div>
          <div class="info-card">
            <small>Shell</small>
            <strong>${escapeHtml(state.targetProfile.detectedShell || "bash")}</strong>
          </div>
        </div>
      ` : ""}

      ${renderDependencyProbeSection()}

      <div class="button-row">
        <button class="btn primary ${state.stepStatus[2] === "running" ? "loading" : ""}" data-action="probe">开始探测</button>
        <button class="btn secondary" data-phase="1">返回</button>
        <button class="btn secondary" data-phase="3" ${canGoTo(3) ? "" : "disabled"}>继续</button>
      </div>
    `;
  }

  function renderDeployPhase() {
    return `
      <div class="section-head">
        <div>
          <h3>部署执行</h3>
          <p>先生成部署预览，再直接执行。脚本预览和真实部署现在都由同一套规则驱动。</p>
        </div>
      </div>

      <div class="meta-grid">
        <div class="control-card">
          <small>Target Host</small>
          <strong>${escapeHtml(state.form.targetHost || "Not set")}:${escapeHtml(String(state.form.sshPort || 22))}</strong>
        </div>
        <div class="control-card">
          <small>App Port</small>
          <strong>${escapeHtml(String(state.form.applicationPort || 8080))}</strong>
        </div>
        <div class="control-card full">
          <small>Deployment Preview</small>
          <strong>${state.scriptMeta ? `${escapeHtml(state.scriptMeta.targetOs)} · ${escapeHtml(state.scriptMeta.shell)} · port ${escapeHtml(String(state.scriptMeta.recommendedPort))}` : "Generate preview to inspect the final deployment plan."}</strong>
        </div>
      </div>

      <div class="toggle" style="margin-top: 18px;">
        <input id="useSudo" data-field="useSudo" type="checkbox">
        <label for="useSudo">部署时启用 sudo -S</label>
      </div>

      <div class="form-grid" style="margin-top: 14px;">
        <div class="field full">
          <label for="sudoPassword">sudo 密码</label>
          <input id="sudoPassword" data-field="sudoPassword" type="password" placeholder="如与 SSH 密码一致可直接复用">
        </div>
      </div>

      <div class="helper-callout">
        这个阶段已经把“脚本预览”和“真实部署”收敛到同一个入口。你先看预览，再执行部署，避免预览和执行出现两套逻辑。
      </div>

      <div class="button-row">
        <button class="btn secondary" data-phase="2">返回</button>
        <button class="btn soft ${state.stepStatus[3] === "running" && state.terminalMode === "script" ? "loading" : ""}" data-action="preview">生成部署预览</button>
        <button class="btn primary ${state.stepStatus[3] === "running" && state.terminalMode === "logs" ? "loading" : ""}" data-action="deploy" ${state.scriptRaw.trim() ? "" : "disabled"}>执行部署</button>
        <button class="btn warn" data-action="diagnose" ${state.stepStatus[3] === "error" ? "" : "disabled"}>分析失败日志</button>
      </div>
    `;
  }

  function renderMonitor() {
    const monitorTitle = document.getElementById("monitorTitle");
    const monitorHint = document.getElementById("monitorHint");
    const monitorMode = document.getElementById("monitorMode");
    const monitorBody = document.getElementById("monitorBody");

    if (state.terminalMode === "script") {
      monitorTitle.textContent = "deployment-preview.sh";
      monitorHint.textContent = state.streamMessage || "正在生成部署预览。";
      monitorMode.textContent = "Preview";
      monitorBody.innerHTML = `
        <div class="code-card">
          <header>
            <strong>Deployment Preview</strong>
            <span class="pill"><strong>${state.streamStatus === "running" ? "Streaming" : "Ready"}</strong></span>
          </header>
          <pre>${escapeHtml(state.scriptRaw || "# Waiting for deployment preview...")}</pre>
        </div>
      `;
      scrollMonitor();
      return;
    }

    if (state.terminalMode === "logs") {
      monitorTitle.textContent = "deployment-log";
      monitorHint.textContent = state.terminalSummary || "正在等待部署输出。";
      monitorMode.textContent = "Logs";
      monitorBody.innerHTML = `
        <div class="log-list">
          ${(state.terminalLines || []).map((line) => `
            <div class="log-item ${line.level}">
              <span class="log-tag">${escapeHtml(line.level)}</span>
              <div class="log-text">${escapeHtml(line.text)}</div>
            </div>
          `).join("") || `<div class="monitor-empty"><h4>等待输出</h4><p>部署开始后，这里会实时显示日志。</p></div>`}
        </div>
      `;
      scrollMonitor();
      return;
    }

    monitorTitle.textContent = "运行监视";
    monitorHint.textContent = "当前阶段还未开始。";
    monitorMode.textContent = "Standby";
    monitorBody.innerHTML = `
      <div class="monitor-empty">
        <h4>单 Jar 工作台已就绪</h4>
        <p>输入项目目录后，系统会在本地完成栈识别。之后再做远端探测，最后生成部署预览并执行部署。右侧始终承担结果反馈，不需要切换到第二个服务界面。</p>
      </div>
    `;
  }

  function syncFormFields() {
    Object.entries(state.form).forEach(([key, value]) => {
      const input = document.querySelector(`[data-field="${key}"]`);
      if (!input) {
        return;
      }
      if (input.type === "checkbox") {
        input.checked = Boolean(value);
      } else if (document.activeElement !== input) {
        input.value = value ?? "";
      }
    });
  }

  function renderDependencyProbeSection() {
    const requirements = state.localContext?.dependencyRequirements || [];
    if (!requirements.length) {
      return "";
    }

    return `
      <div class="dependency-section">
        <div class="dependency-section-head">
          <div>
            <h4>中间件测试与部署</h4>
            <p>测试 MySQL / Redis 等中间件连接状态。可自定义配置，测试后如果连接失败，系统将自动探测可用端口并通过 Docker 部署。</p>
          </div>
          <span class="pill"><strong>${requirements.length}</strong> 项</span>
        </div>
        <div class="dependency-list">
          ${requirements.map((requirement) => {
            const result = findDependencyProbeResult(requirement.kind);
            const statusClass = getStatusClass(result?.status);
            const statusLabel = formatDependencyStatus(result);
            const isTesting = state.dependencyTesting?.[requirement.kind] === true;
            const isDeploying = state.dependencyDeploying?.[requirement.kind] === true;
            const isIgnored = state.dependencyIgnored?.[requirement.kind] === true;

            const customConfig = state.dependencyConfigs?.[requirement.kind] || {};
            const host = customConfig.host ?? requirement.host ?? "localhost";
            const port = customConfig.port ?? requirement.port ?? getDefaultPort(requirement.kind);
            const databaseName = customConfig.databaseName ?? requirement.databaseName;
            const username = customConfig.username ?? getDefaultUsername(requirement.kind);
            const password = customConfig.password ?? "";

            return `
              <div class="dependency-card ${isIgnored ? "ignored" : ""}">
                <div class="dependency-card-head">
                  <div class="dependency-card-title">
                    <strong>${escapeHtml(requirement.displayName || requirement.kind)}</strong>
                    <code>${escapeHtml(requirement.kind)}</code>
                  </div>
                  <div class="dependency-card-status">
                    <span class="status-pill ${statusClass}">
                      <span class="status-dot"></span>
                      ${escapeHtml(statusLabel)}
                    </span>
                    ${requirement.required ? `<span class="pill warn-pill"><strong>必需</strong></span>` : ""}
                    ${isIgnored ? `<span class="pill" style="background: #fef3c7; color: #92400e;"><strong>已忽略</strong></span>` : ""}
                  </div>
                </div>

                <div class="dependency-card-source">来源：${escapeHtml(formatDependencySource(requirement))} (AiOps 预设，可编辑)</div>

                <div class="dependency-config-table">
                  <div class="config-table-row">
                    <div class="config-table-cell">
                      <label>Host</label>
                      <input
                        type="text"
                        class="config-input"
                        data-dependency-kind="${escapeAttribute(requirement.kind)}"
                        data-config-key="host"
                        value="${escapeAttribute(host)}"
                        placeholder="localhost">
                    </div>
                    <div class="config-table-cell">
                      <label>Port</label>
                      <input
                        type="number"
                        class="config-input"
                        data-dependency-kind="${escapeAttribute(requirement.kind)}"
                        data-config-key="port"
                        value="${escapeAttribute(String(port))}"
                        placeholder="${getDefaultPort(requirement.kind)}">
                    </div>
                  </div>
                  ${requirement.kind === "MYSQL" ? `
                    <div class="config-table-row">
                      <div class="config-table-cell">
                        <label>Database</label>
                        <input
                          type="text"
                          class="config-input"
                          data-dependency-kind="${escapeAttribute(requirement.kind)}"
                          data-config-key="databaseName"
                          value="${escapeAttribute(databaseName || "")}"
                          placeholder="数据库名">
                      </div>
                      <div class="config-table-cell">
                        <label>Username</label>
                        <input
                          type="text"
                          class="config-input"
                          data-dependency-kind="${escapeAttribute(requirement.kind)}"
                          data-config-key="username"
                          value="${escapeAttribute(username)}"
                          placeholder="root">
                      </div>
                    </div>
                    <div class="config-table-row">
                      <div class="config-table-cell full">
                        <label>Password</label>
                        <input
                          type="password"
                          class="config-input"
                          data-dependency-kind="${escapeAttribute(requirement.kind)}"
                          data-config-key="password"
                          value="${escapeAttribute(password)}"
                          placeholder="密码（可选）">
                      </div>
                    </div>
                  ` : `
                    <div class="config-table-row">
                      <div class="config-table-cell full">
                        <label>Password</label>
                        <input
                          type="password"
                          class="config-input"
                          data-dependency-kind="${escapeAttribute(requirement.kind)}"
                          data-config-key="password"
                          value="${escapeAttribute(password)}"
                          placeholder="Redis 密码（可选）">
                      </div>
                    </div>
                  `}
                </div>

                ${result ? `
                  <div class="dependency-card-hint">
                    <strong>探测结果：</strong>${escapeHtml(result.message || "Dependency probe completed.")}
                  </div>
                ` : `
                  <div class="dependency-card-hint">
                    <strong>状态：</strong>尚未开始远端依赖探测。点击"测试连接"检查中间件状态。
                  </div>
                `}

                <div class="dependency-card-actions">
                  <button
                    class="btn btn-sm btn-outline ${isTesting ? "loading" : ""}"
                    data-action="test-dependency"
                    data-dependency-kind="${escapeAttribute(requirement.kind)}"
                    ${isTesting || isDeploying ? "disabled" : ""}>
                    ${isTesting ? "测试中..." : "测试连接"}
                  </button>
                  <button
                    class="btn btn-sm btn-outline ${isIgnored ? "btn-ignored" : ""}"
                    data-action="ignore-dependency"
                    data-dependency-kind="${escapeAttribute(requirement.kind)}"
                    ${isTesting || isDeploying ? "disabled" : ""}>
                    ${isIgnored ? "取消忽略" : "忽略"}
                  </button>
                  <button
                    class="btn btn-sm btn-outline-accent ${isDeploying ? "loading" : ""}"
                    data-action="deploy-dependency"
                    data-dependency-kind="${escapeAttribute(requirement.kind)}"
                    ${isDeploying ? "disabled" : ""}>
                    ${isDeploying ? "部署中..." : "部署"}
                  </button>
                </div>
              </div>
            `;
          }).join("")}
        </div>
      </div>
    `;
  }

  function getDefaultUsername(kind) {
    switch (kind) {
      case "MYSQL": return "root";
      case "REDIS": return "";
      default: return "";
    }
  }

  function getDefaultPort(kind) {
    switch (kind) {
      case "MYSQL": return 3306;
      case "REDIS": return 6379;
      default: return 0;
    }
  }

  function getStatusClass(status) {
    if (!status) return "unknown";
    switch (status) {
      case "READY": return "ready";
      case "NOT_FOUND": return "not-found";
      case "UNREACHABLE": return "unreachable";
      case "FOUND_INACTIVE": return "found-inactive";
      default: return "unknown";
    }
  }

  function renderConfigChoiceSection() {
    const choices = state.localContext?.deploymentHints?.configChoices || [];
    if (!choices.length) {
      return "";
    }
    const selected = state.localContext?.confirmedConfigChoice
      || state.localContext?.deploymentHints?.recommendedConfigChoice
      || choices[0]?.id;

    const localDockerChoices = choices.filter(c => c.id === "USE_LOCAL_DOCKERFILE");
    const presetChoices = choices.filter(c => c.id !== "USE_LOCAL_DOCKERFILE");

    return `
      <div class="config-section" style="margin-top: 18px;">
        <div class="section-subhead">
          <div>
            <h4>部署策略选择</h4>
            <p>选择部署方式：使用 AiOps 智能生成的预设方案，或使用项目本地的 Dockerfile。</p>
          </div>
        </div>

        ${presetChoices.length ? `
          <div style="margin-top: 14px;">
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 10px;">
              <div style="width: 20px; height: 20px; border-radius: 4px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); display: flex; align-items: center; justify-content: center;">
                <span style="color: white; font-size: 12px; font-weight: bold;">A</span>
              </div>
              <strong style="color: #374151;">预设方案（推荐）</strong>
              <span class="pill" style="background: #dbeafe; color: #1d4ed8;">AiOps 生成</span>
            </div>
            <p style="font-size: 13px; color: #6b7280; margin: 0 0 12px 28px;">AiOps 会根据项目类型智能生成优化的 Dockerfile 和部署脚本，确保最佳兼容性和性能。</p>
            <div class="config-choice-list" style="margin-left: 28px;">
              ${presetChoices.map((choice) => `
                <label class="config-choice ${selected === choice.id ? "selected" : ""}">
                  <input type="radio" name="configChoice" data-config-choice="true" value="${escapeAttribute(choice.id)}" ${selected === choice.id ? "checked" : ""}>
                  <div>
                    <strong>${escapeHtml(choice.label)}</strong>
                    <p>${escapeHtml(choice.description)}</p>
                  </div>
                </label>
              `).join("")}
            </div>
          </div>
        ` : ""}

        ${localDockerChoices.length ? `
          <div style="margin-top: 18px; padding-top: 14px; border-top: 1px solid #e5e7eb;">
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 10px;">
              <div style="width: 20px; height: 20px; border-radius: 4px; background: linear-gradient(135deg, #14b8a6 0%, #0d9488 100%); display: flex; align-items: center; justify-content: center;">
                <span style="color: white; font-size: 12px; font-weight: bold;">D</span>
              </div>
              <strong style="color: #374151;">本地 Docker</strong>
              <span class="pill" style="background: #d1fae5; color: #065f46;">项目自带</span>
            </div>
            <p style="font-size: 13px; color: #6b7280; margin: 0 0 12px 28px;">使用项目目录中已有的 Dockerfile，保留您的自定义配置。检测到的配置将显示在下方。</p>
            <div class="config-choice-list" style="margin-left: 28px;">
              ${localDockerChoices.map((choice) => `
                <label class="config-choice ${selected === choice.id ? "selected" : ""}">
                  <input type="radio" name="configChoice" data-config-choice="true" value="${escapeAttribute(choice.id)}" ${selected === choice.id ? "checked" : ""}>
                  <div>
                    <strong>${escapeHtml(choice.label)}</strong>
                    <p>${escapeHtml(choice.description)}</p>
                  </div>
                </label>
              `).join("")}
            </div>

            <div style="margin-top: 14px; margin-left: 28px;">
              <div class="info-card" style="background: #f8fafc; border: 1px solid #e2e8f0; margin-bottom: 8px;">
                <small>项目路径</small>
                <strong>${escapeHtml(state.form.projectPath || "未知")}</strong>
              </div>
              <div class="info-card" style="background: #f8fafc; border: 1px solid #e2e8f0;">
                <small>Dockerfile 位置</small>
                <strong>${escapeHtml(state.form.projectPath || "")}/Dockerfile</strong>
              </div>
            </div>
          </div>
        ` : ""}
      </div>
    `;
  }

  function renderRuntimeConfigSection() {
    const items = state.localContext?.runtimeConfigItems || [];
    if (!items.length) {
      return "";
    }

    return `
      <div class="config-section" style="margin-top: 18px;">
        <div class="section-subhead">
          <div>
            <h4>运行配置确认</h4>
            <p>优先显示 Java / Nginx 部署关键配置，并明确标注来源模块、文件与 profile。</p>
          </div>
          <span class="pill"><strong>${items.length}</strong> 项</span>
        </div>
        <div class="config-list">
          ${items.map((item) => `
            <div class="config-card ${item.requiresConfirm ? "needs-confirm" : ""}">
              <div class="config-card-head">
                <div>
                  <strong>${escapeHtml(item.label || item.key)}</strong>
                  <code>${escapeHtml(item.key)}</code>
                </div>
                <div class="config-tags">
                  <span class="pill"><strong>${escapeHtml(item.family || "CONFIG")}</strong></span>
                  ${item.placeholder ? `<span class="pill warn-pill"><strong>占位符</strong></span>` : ""}
                  ${item.sensitive ? `<span class="pill warn-pill"><strong>敏感</strong></span>` : ""}
                </div>
              </div>
              <div class="config-source">来源：${escapeHtml(formatConfigSource(item))}</div>
              <div class="config-input-row">
                <input
                  class="config-input"
                  data-config-id="${escapeAttribute(item.id)}"
                  type="${resolveConfigInputType(item)}"
                  value="${escapeAttribute(item.valuePreview || "")}"
                  placeholder="确认或修改该配置值">
              </div>
              <p class="config-hint">${escapeHtml(item.operatorHint || "部署前确认该配置是否需要覆盖。")}</p>
            </div>
          `).join("")}
        </div>
      </div>
    `;
  }

  async function scanProject() {
    state.portResolutionMessage = "";
    state.stepStatus[1] = "running";
    renderAll();
    try {
      await syncLocalAnalysis({ force: true, syncPort: true });
      state.stepStatus[1] = "success";
      renderAll();
    } catch (error) {
      handleTerminalError(1, error.message);
    }
  }

  async function probeTarget() {
    state.stepStatus[2] = "running";
    state.needsPortConfirm = false;
    state.portResolutionMessage = "";
    renderAll();

    try {
      await syncProbeTarget({ force: true });
      renderAll();
    } catch (error) {
      handleTerminalError(2, error.message);
    }
  }

  async function generatePreview() {
    if (!canGoTo(3)) {
      return;
    }
    try {
      await syncLocalAnalysis({ force: false, syncPort: false });
      await syncProbeTarget({ force: false });
    } catch (error) {
      handleTerminalError(error.step || 2, error.message);
      return;
    }
    closeStream();
    state.phase = 3;
    state.stepStatus[3] = "running";
    state.terminalMode = "script";
    state.scriptRaw = "";
    state.scriptMeta = null;
    state.streamStatus = "running";
    state.streamMessage = "正在生成部署预览。";
    renderAll();

    const params = new URLSearchParams({
      workflowId: state.workflowId,
      expectedStateVersion: String(state.stateVersion ?? ""),
      operator: "Argus-Ops",
      regenerate: "true"
    });

    eventSource = new EventSource(`${apiBase}/v1/workflow/stream-script?${params.toString()}`);

    eventSource.addEventListener("start", (event) => {
      const payload = JSON.parse(event.data);
      if (payload.state) {
        syncWorkflow(payload.state);
      }
      state.scriptMeta = payload.metadata || null;
      state.streamMessage = payload.message || "正在生成部署预览。";
      renderAll();
    });

    eventSource.addEventListener("token", (event) => {
      const payload = JSON.parse(event.data);
      state.scriptRaw += payload.chunk || "";
      renderMonitor();
    });

    eventSource.addEventListener("complete", (event) => {
      const payload = JSON.parse(event.data);
      if (payload.state) {
        syncWorkflow(payload.state);
      }
      state.scriptMeta = payload.metadata || state.scriptMeta;
      state.streamStatus = "complete";
      state.streamMessage = payload.message || "部署预览已生成。";
      state.stepStatus[3] = "success";
      closeStream();
      renderAll();
    });

    eventSource.addEventListener("error", () => {
      state.streamStatus = "error";
      state.streamMessage = "部署预览生成失败。";
      state.stepStatus[3] = "error";
      closeStream();
      renderAll();
    });
  }

  async function deployProject() {
    if (!state.scriptRaw.trim()) {
      return;
    }

    state.phase = 3;
    state.stepStatus[3] = "running";
    state.terminalMode = "logs";
    state.terminalSummary = "部署任务已提交，正在等待远端输出。";
    state.terminalLines = [];
    renderAll();

    pushLine(`[INFO] host=${state.form.targetHost}:${state.form.sshPort}`, "info");
    pushLine(`[INFO] app-port=${state.form.applicationPort}`, "info");

    try {
      const result = await requestJson("/v1/ops/deploy/exec", {
        method: "POST",
        body: JSON.stringify({
          host: state.form.targetHost,
          port: Number(state.form.sshPort),
          username: state.form.username,
          password: state.form.password,
          command: normalizeScript(state.scriptRaw),
          useSudo: Boolean(state.form.useSudo),
          sudoPassword: state.form.sudoPassword || null,
          applicationPort: Number(state.form.applicationPort),
          workflowId: state.workflowId || null,
          projectPath: state.form.projectPath
        })
      });

      normalizeOutputLines(result.output).forEach((item) => pushLine(item.text, item.level));
      (result.error || "").split(/\r?\n/).filter(Boolean).forEach((line) => {
        const level = /failed|exception|error|permission denied|refused|allocated/i.test(line) ? "error" : "warning";
        pushLine(line, level);
      });

      if (result.success && !(result.error || "").trim()) {
        state.stepStatus[3] = "success";
        state.terminalSummary = "部署完成，没有检测到真实错误。";
        pushLine("[SUCCESS] 部署完成。", "success");
      } else if (result.success) {
        state.stepStatus[3] = "warning";
        state.terminalSummary = "部署完成，但远端返回了告警输出。";
        pushLine("[WARN] 部署完成，但有告警输出。", "warning");
      } else {
        state.stepStatus[3] = "error";
        state.terminalSummary = "部署失败。";
        pushLine("[ERROR] 部署失败。", "error");
      }
      renderAll();
    } catch (error) {
      handleTerminalError(3, error.message);
    }
  }

  async function diagnoseLastFailure() {
    if (!state.workflowId || !state.stateVersion) {
      return;
    }
    pushLine("[INFO] 正在请求错误诊断。", "info");
    try {
      const result = await requestJson("/v1/workflow/analyze-log", {
        method: "POST",
        body: JSON.stringify({
          workflowId: state.workflowId,
          expectedStateVersion: state.stateVersion,
          logRecordId: Date.now(),
          exitCode: state.inferredExitCode || 1,
          tailLineCount: Math.min((state.terminalLines || []).length, 80)
        })
      });
      const findings = (result.findings || []).map((item) => `${item.category}: ${item.errorRootCause?.zhCn || item.errorRootCause?.enUs || "No summary"}`);
      if (findings.length) {
        findings.forEach((line) => pushLine(`[DIAG] ${line}`, "warning"));
      } else if (result.summary?.zhCn || result.summary?.enUs) {
        pushLine(`[DIAG] ${result.summary.zhCn || result.summary.enUs}`, "warning");
      }
      renderAll();
    } catch (error) {
      pushLine(`[ERROR] 诊断失败: ${error.message}`, "error");
    }
  }

  async function requestJson(path, options) {
    const response = await fetch(`${apiBase}${path}`, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...(options?.headers || {})
      }
    });
    const text = await response.text();
    let payload = null;
    if (text) {
      try {
        payload = JSON.parse(text);
      } catch {
        payload = text;
      }
    }
    if (!response.ok) {
      throw new Error(payload?.message || payload || `HTTP ${response.status}`);
    }
    return payload;
  }

  async function syncLocalAnalysis({ force, syncPort }) {
    if (!state.form.projectPath) {
      throw new Error("projectPath is required");
    }
    if (!force && state.workflowId && state.localContext && !state.configDirty && !state.analysisDirty) {
      return state.localContext;
    }

    const result = await requestJson("/v1/workflow/analyze-local", {
      method: "POST",
      body: JSON.stringify({
        workflowId: state.workflowId || null,
        expectedStateVersion: state.stateVersion,
        projectPath: state.form.projectPath,
        operator: "Argus-Ops",
        includeDependencyGraph: true,
        simulateCompile: true,
        confirmedConfigChoice: state.localContext?.confirmedConfigChoice || null,
        configOverrides: collectConfigOverrides()
      })
    });

    syncWorkflow(result.state);
    state.localContext = result.context || null;
    state.localWarnings = result.warnings || [];
    state.analysisDirty = false;
    state.configDirty = false;
    state.probeDirty = true;
    state.targetProfile = null;
    state.probeDecision = null;
    state.dependencyProbeResults = [];
    state.dependencyDecisions = [];
    state.probeWarnings = [];
    state.portResolutionMessage = "";
    if (syncPort && result.context?.defaultApplicationPort) {
      state.form.applicationPort = result.context.defaultApplicationPort;
    }
    return result.context;
  }

  async function syncProbeTarget({ force }) {
    try {
      await syncLocalAnalysis({ force: false, syncPort: true });
    } catch (error) {
      error.step = 1;
      throw error;
    }

    if (!state.workflowId) {
      throw Object.assign(new Error("workflowId is required"), { step: 2 });
    }
    if (!force && state.targetProfile && !state.probeDirty) {
      return state.targetProfile;
    }

    const result = await requestJson("/v1/workflow/probe-target", {
      method: "POST",
      body: JSON.stringify({
        workflowId: state.workflowId,
        expectedStateVersion: state.stateVersion,
        credential: {
          host: state.form.targetHost,
          sshPort: Number(state.form.sshPort),
          username: state.form.username,
          credentialType: "PASSWORD",
          password: state.form.password,
          privateKeyPem: "",
          connectTimeoutMillis: Number(state.form.connectTimeoutMillis)
        },
        defaultApplicationPort: Number(state.form.applicationPort),
        maxAutoIncrementProbeSpan: 10,
        dependencyDecisions: collectDependencyDecisions()
      })
    });

    syncWorkflow(result.state);
    state.targetProfile = result.targetProfile || null;
    state.probeDecision = result.portProbe || null;
    state.probeWarnings = result.warnings || [];
    state.dependencyProbeResults = result.dependencyProbeResults || [];
    state.dependencyDecisions = result.dependencyDecisions || collectDependencyDecisions();
    state.probeDirty = false;

    if (result.portProbe?.recommendedAvailablePort) {
      state.form.applicationPort = result.portProbe.recommendedAvailablePort;
    }

    if (result.portProbe?.requestedPortOccupied
      && result.portProbe?.recommendedAvailablePort
      && result.portProbe.recommendedAvailablePort !== result.portProbe.requestedPort) {
      state.portResolutionMessage = `Requested port ${result.portProbe.requestedPort} is occupied. Deployment will use ${result.portProbe.recommendedAvailablePort}.`;
      state.needsPortConfirm = false;
    } else if (result.portProbe?.requestedPortOccupied && !result.portProbe?.recommendedAvailablePort) {
      state.portResolutionMessage = "Requested port is occupied and no safe replacement port was found in the probe window.";
      state.needsPortConfirm = true;
    } else {
      state.portResolutionMessage = "";
      state.needsPortConfirm = false;
    }

    state.stepStatus[2] = hasBlockingProbeIssue() || hasPendingDependencyDecision() || Boolean(state.portResolutionMessage) ? "warning" : "success";
    return state.targetProfile;
  }

  function checkHealth() {
    fetch("/actuator/health")
      .then((response) => response.json())
      .then((payload) => {
        state.backendHealth = payload.status === "UP" ? "ready" : "down";
        updateHealth();
      })
      .catch(() => {
        state.backendHealth = "down";
        updateHealth();
      });
  }

  function updateHealth() {
    const dot = document.getElementById("healthDot");
    const text = document.getElementById("healthText");
    dot.className = `health-dot ${state.backendHealth}`;
    text.textContent = state.backendHealth === "ready" ? "Backend Ready" : state.backendHealth === "down" ? "Backend Down" : "Checking";
  }

  function pushLine(text, level) {
    state.terminalMode = "logs";
    state.terminalLines.push({ text, level });
    renderMonitor();
  }

  function normalizeOutputLines(output) {
    const lines = (output || "").split(/\r?\n/).filter(Boolean);
    const result = [];
    let lastBucket = -1;

    for (const line of lines) {
      const uploadMatch = line.match(/^UPLOAD_PROGRESS:\s*(\d+)\/(\d+)$/);
      if (uploadMatch) {
        const current = Number(uploadMatch[1]);
        const total = Number(uploadMatch[2]) || 1;
        const percent = Math.floor((current / total) * 100);
        const bucket = Math.floor(percent / 10);
        if (bucket !== lastBucket || percent === 100) {
          lastBucket = bucket;
          result.push({ text: `[UPLOAD] ${percent}%`, level: "info" });
        }
        continue;
      }
      result.push({
        text: line,
        level: /CONTAINER ID|0\.0\.0\.0:\d+->80\/tcp|SUCCESS/i.test(line) ? "success" : "info"
      });
    }

    return result;
  }

  function handleTerminalError(step, message) {
    state.stepStatus[step] = "error";
    state.terminalMode = "logs";
    state.terminalSummary = message;
    state.terminalLines = [{ text: message, level: "error" }];
    state.inferredExitCode = inferExitCode(message);
    renderAll();
  }

  function inferExitCode(message) {
    if (/outofmemory|heap space|killed/i.test(message)) return 137;
    if (/connection refused|refused/i.test(message)) return 111;
    if (/permission denied|access denied/i.test(message)) return 126;
    if (/port is already allocated|bind for/i.test(message)) return 98;
    return 1;
  }

  function normalizeScript(text) {
    return (text || "")
      .replace(/^```[a-zA-Z]*\n?/, "")
      .replace(/\n?```$/, "");
  }

  function syncWorkflow(snapshot) {
    if (!snapshot) return;
    state.workflowId = snapshot.workflowId || state.workflowId;
    state.stateVersion = snapshot.stateVersion ?? state.stateVersion;
  }

  function updateRuntimeConfigItem(configId, value) {
    const items = state.localContext?.runtimeConfigItems;
    if (!items?.length) {
      return;
    }
    const item = items.find((entry) => entry.id === configId);
    if (!item) {
      return;
    }
    item.valuePreview = value;
    state.configDirty = true;
    persist();
  }

  function collectConfigOverrides() {
    return (state.localContext?.runtimeConfigItems || []).map((item) => ({
      id: item.id,
      value: item.valuePreview ?? ""
    }));
  }

  function collectDependencyDecisions() {
    return (state.dependencyDecisions || [])
      .filter((item) => item?.kind && item?.mode)
      .map((item) => ({
        kind: item.kind,
        mode: item.mode,
        note: item.note || null
      }));
  }

  function formatConfigSource(item) {
    const parts = [];
    if (item.sourceModule) {
      parts.push(item.sourceModule === "." ? "root" : item.sourceModule);
    }
    if (item.sourceFile) {
      parts.push(item.sourceFile);
    }
    if (item.sourceProfile) {
      parts.push(`profile=${item.sourceProfile}`);
    }
    if (item.sourceLine) {
      parts.push(`line=${item.sourceLine}`);
    }
    return parts.join(" / ") || "unknown";
  }

  function resolveConfigInputType(item) {
    if (item?.sensitive) {
      return "password";
    }
    if (/(^|[._\]])port$/i.test(item?.key || "") || /\.listen$/i.test(item?.key || "")) {
      return "number";
    }
    return "text";
  }

  function canGoTo(phase) {
    if (phase <= state.phase) return true;
    if (phase === 2) return isLocalAnalysisReady();
    if (phase === 3) return Boolean(state.targetProfile) && hasResolvedApplicationPort() && !state.probeDirty && !hasPendingDependencyDecision();
    return false;
  }

  function isLocalAnalysisReady() {
    return Boolean(state.localContext) && !state.analysisDirty && state.stepStatus[1] !== "running";
  }

  function hasResolvedApplicationPort() {
    if (!state.probeDecision) {
      return false;
    }
    return !state.probeDecision.requestedPortOccupied || Boolean(state.probeDecision.recommendedAvailablePort);
  }

  function hasBlockingProbeIssue() {
    return Boolean(state.probeDecision?.requestedPortOccupied) && !state.probeDecision?.recommendedAvailablePort;
  }

  function findDependencyProbeResult(kind) {
    return (state.dependencyProbeResults || []).find((item) => item?.kind === kind) || null;
  }

  function findDependencyDecision(kind) {
    return (state.dependencyDecisions || []).find((item) => item?.kind === kind) || null;
  }

  function updateDependencyDecision(kind, mode) {
    const decisions = state.dependencyDecisions || [];
    const existing = decisions.find((item) => item.kind === kind);
    if (existing) {
      existing.mode = mode;
    } else {
      decisions.push({ kind, mode, note: null });
    }
    state.dependencyDecisions = decisions.filter((item) => item.mode);
    state.probeDirty = true;
    persist();
  }

  function hasPendingDependencyDecision() {
    return (state.dependencyProbeResults || []).some((item) => item?.requiresDecision && !findDependencyDecision(item.kind));
  }

  function formatDependencyStatus(result) {
    if (!result) return "Not Probed";
    return result.status || "UNKNOWN";
  }

  function formatDependencySource(requirement) {
    const parts = [];
    if (requirement?.sourceModule) {
      parts.push(requirement.sourceModule === "." ? "root" : requirement.sourceModule);
    }
    if (requirement?.sourceFile) {
      parts.push(requirement.sourceFile);
    }
    if (requirement?.sourceProfile) {
      parts.push(`profile=${requirement.sourceProfile}`);
    }
    return parts.join(" / ") || "unknown";
  }

  function formatDependencyDecisionPrompt(requirement, result) {
    if (!result?.requiresDecision) {
      return "Dependency is ready. You can continue to script generation.";
    }
    const label = requirement?.displayName || requirement?.kind || "Dependency";
    if (isLocalDependencyHost(requirement?.host)) {
      return `${label} is not confirmed as available. Choose whether to create a minimal local instance on the target host so the jar can start.`;
    }
    return `${label} is not reachable at the configured address. Choose whether to create a minimal local instance on the target host and temporarily route the jar to it.`;
  }

  function renderDependencyDecisionOptions(requirement, result, decision) {
    const selected = decision?.mode || "";
    const options = [
      { value: "", label: "请选择处理方式" },
      { value: "DEPLOY_AUTOMATICALLY", label: result?.status === "NOT_FOUND" ? "自动部署 (Recommended)" : "自动部署" },
      { value: "MANUAL_PREPARE", label: "手工准备" },
      { value: "CONTINUE_ANYWAY", label: "继续执行" },
      { value: "REUSE_EXISTING", label: "复用现有" }
    ];
    if (options.length === 5) {
      options[0].label = "Choose an action";
      options[1].label = "Create on target host (Recommended)";
      options[2].label = "Prepare it myself";
      options[3].label = "Continue with risk";
      options[4].label = "Reuse existing instance";
    }
    return options.map((option) => `
      <option value="${escapeAttribute(option.value)}" ${selected === option.value ? "selected" : ""}>${escapeHtml(option.label)}</option>
    `).join("");
  }

  function isLocalDependencyHost(host) {
    const normalized = String(host || "").trim().toLowerCase();
    return normalized === "localhost" || normalized === "127.0.0.1" || normalized === "::1";
  }

  function closeStream() {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }
  }

  function scrollMonitor() {
    const monitorBody = document.getElementById("monitorBody");
    if (monitorBody) {
      monitorBody.scrollTop = monitorBody.scrollHeight;
    }
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function escapeAttribute(value) {
    return escapeHtml(value).replace(/`/g, "&#96;");
  }

  function resetForProjectPathChange() {
    closeStream();
    state.phase = 1;
    state.workflowId = "";
    state.stateVersion = null;
    state.analysisDirty = true;
    state.configDirty = false;
    state.probeDirty = false;
    state.localContext = null;
    state.localWarnings = [];
    state.targetProfile = null;
    state.probeDecision = null;
    state.dependencyProbeResults = [];
    state.dependencyDecisions = [];
    state.probeWarnings = [];
    state.dependencyTesting = {};
    state.dependencyDeploying = {};
    state.dependencyConfigs = {};
    state.dependencyIgnored = {};
    state.needsPortConfirm = false;
    state.portResolutionMessage = "";
    state.scriptRaw = "";
    state.scriptMeta = null;
    state.streamStatus = "idle";
    state.streamMessage = "";
    state.terminalMode = "idle";
    state.terminalSummary = "";
    state.terminalLines = [];
    state.inferredExitCode = 0;
    state.stepStatus[1] = "idle";
    state.stepStatus[2] = "idle";
    state.stepStatus[3] = "idle";
    state.form.applicationPort = defaultState.form.applicationPort;
  }

  function normalizeProjectPath(value) {
    return String(value || "").trim().replace(/\//g, "\\").toLowerCase();
  }

  function loadState() {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return structuredClone(defaultState);
      const parsed = JSON.parse(raw);
      return {
        ...structuredClone(defaultState),
        ...parsed,
        form: {
          ...structuredClone(defaultState).form,
          ...(parsed.form || {})
        },
        stepStatus: {
          ...structuredClone(defaultState).stepStatus,
          ...(parsed.stepStatus || {})
        },
        phase: Math.min(Math.max(parsed.phase || 1, 1), 3)
      };
    } catch {
      return structuredClone(defaultState);
    }
  }

  function persist() {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }

  init();
})();
