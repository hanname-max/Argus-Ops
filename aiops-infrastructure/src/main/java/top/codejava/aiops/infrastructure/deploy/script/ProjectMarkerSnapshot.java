package top.codejava.aiops.infrastructure.deploy.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class ProjectMarkerSnapshot {

    private final Path projectRoot;
    private final boolean hasDockerfile;
    private final boolean hasPom;
    private final boolean hasGradle;
    private final boolean hasPackageJson;
    private final boolean hasPackageLock;
    private final boolean hasPnpmLock;
    private final boolean hasYarnLock;
    private final boolean hasRequirementsTxt;
    private final boolean hasPyproject;
    private final boolean hasManagePy;
    private final boolean hasAppPy;
    private final boolean hasMainPy;
    private final boolean hasServerJs;
    private final boolean hasAppJs;
    private final boolean hasMainJs;
    private final boolean hasIndexHtml;
    private final boolean hasNginxConf;
    private final boolean hasNestedStaticRoot;
    private final String dockerfileText;
    private final String packageJsonText;
    private final String appPyText;
    private final String mainPyText;

    private ProjectMarkerSnapshot(Path projectRoot,
                                  boolean hasDockerfile,
                                  boolean hasPom,
                                  boolean hasGradle,
                                  boolean hasPackageJson,
                                  boolean hasPackageLock,
                                  boolean hasPnpmLock,
                                  boolean hasYarnLock,
                                  boolean hasRequirementsTxt,
                                  boolean hasPyproject,
                                  boolean hasManagePy,
                                  boolean hasAppPy,
                                  boolean hasMainPy,
                                  boolean hasServerJs,
                                  boolean hasAppJs,
                                  boolean hasMainJs,
                                  boolean hasIndexHtml,
                                  boolean hasNginxConf,
                                  boolean hasNestedStaticRoot,
                                  String dockerfileText,
                                  String packageJsonText,
                                  String appPyText,
                                  String mainPyText) {
        this.projectRoot = projectRoot;
        this.hasDockerfile = hasDockerfile;
        this.hasPom = hasPom;
        this.hasGradle = hasGradle;
        this.hasPackageJson = hasPackageJson;
        this.hasPackageLock = hasPackageLock;
        this.hasPnpmLock = hasPnpmLock;
        this.hasYarnLock = hasYarnLock;
        this.hasRequirementsTxt = hasRequirementsTxt;
        this.hasPyproject = hasPyproject;
        this.hasManagePy = hasManagePy;
        this.hasAppPy = hasAppPy;
        this.hasMainPy = hasMainPy;
        this.hasServerJs = hasServerJs;
        this.hasAppJs = hasAppJs;
        this.hasMainJs = hasMainJs;
        this.hasIndexHtml = hasIndexHtml;
        this.hasNginxConf = hasNginxConf;
        this.hasNestedStaticRoot = hasNestedStaticRoot;
        this.dockerfileText = dockerfileText;
        this.packageJsonText = packageJsonText;
        this.appPyText = appPyText;
        this.mainPyText = mainPyText;
    }

    static ProjectMarkerSnapshot from(Path projectRoot) {
        return new ProjectMarkerSnapshot(
                projectRoot,
                Files.isRegularFile(projectRoot.resolve("Dockerfile")),
                Files.isRegularFile(projectRoot.resolve("pom.xml")),
                Files.isRegularFile(projectRoot.resolve("build.gradle")) || Files.isRegularFile(projectRoot.resolve("build.gradle.kts")),
                Files.isRegularFile(projectRoot.resolve("package.json")),
                Files.isRegularFile(projectRoot.resolve("package-lock.json")),
                Files.isRegularFile(projectRoot.resolve("pnpm-lock.yaml")),
                Files.isRegularFile(projectRoot.resolve("yarn.lock")),
                Files.isRegularFile(projectRoot.resolve("requirements.txt")),
                Files.isRegularFile(projectRoot.resolve("pyproject.toml")),
                Files.isRegularFile(projectRoot.resolve("manage.py")),
                Files.isRegularFile(projectRoot.resolve("app.py")),
                Files.isRegularFile(projectRoot.resolve("main.py")),
                Files.isRegularFile(projectRoot.resolve("server.js")),
                Files.isRegularFile(projectRoot.resolve("app.js")),
                Files.isRegularFile(projectRoot.resolve("main.js")),
                Files.isRegularFile(projectRoot.resolve("index.html")),
                Files.isRegularFile(projectRoot.resolve("nginx.conf")) || Files.isRegularFile(projectRoot.resolve("conf/nginx.conf")),
                Files.isRegularFile(projectRoot.resolve("html/sky/index.html")) || Files.isRegularFile(projectRoot.resolve("html/index.html")),
                readLower(projectRoot.resolve("Dockerfile")),
                readLower(projectRoot.resolve("package.json")),
                readLower(projectRoot.resolve("app.py")),
                readLower(projectRoot.resolve("main.py"))
        );
    }

    boolean hasDockerfile() {
        return hasDockerfile;
    }

    boolean hasPom() {
        return hasPom;
    }

    boolean hasGradle() {
        return hasGradle;
    }

    boolean hasPackageJson() {
        return hasPackageJson;
    }

    boolean hasRequirementsTxt() {
        return hasRequirementsTxt;
    }

    boolean hasPyproject() {
        return hasPyproject;
    }

    boolean hasManagePy() {
        return hasManagePy;
    }

    boolean hasAppPy() {
        return hasAppPy;
    }

    boolean hasMainPy() {
        return hasMainPy;
    }

    boolean hasIndexHtml() {
        return hasIndexHtml;
    }

    boolean hasNginxConf() {
        return hasNginxConf;
    }

    boolean hasNestedStaticRoot() {
        return hasNestedStaticRoot;
    }

    boolean hasNodeLockfile() {
        return hasPackageLock || hasPnpmLock || hasYarnLock;
    }

    String packageInstallCommand() {
        if (hasPnpmLock) {
            return "corepack enable && pnpm install --frozen-lockfile";
        }
        if (hasYarnLock) {
            return "corepack enable && yarn install --frozen-lockfile";
        }
        if (hasPackageLock) {
            return "npm ci";
        }
        return "npm install";
    }

    boolean nodeHasStartScript() {
        return packageJsonText.contains("\"start\"");
    }

    boolean nodeHasBuildScript() {
        return packageJsonText.contains("\"build\"");
    }

    boolean nodeLooksNext() {
        return packageJsonText.contains("\"next\"") || packageJsonText.contains("next dev");
    }

    boolean nodeLooksFrontend() {
        return (packageJsonText.contains("\"react\"")
                || packageJsonText.contains("\"vue\"")
                || packageJsonText.contains("\"@angular/core\"")
                || packageJsonText.contains("\"vite\""))
                && nodeHasBuildScript();
    }

    boolean nodeLooksRuntime() {
        return packageJsonText.contains("\"express\"")
                || packageJsonText.contains("\"koa\"")
                || packageJsonText.contains("\"fastify\"")
                || packageJsonText.contains("\"@nestjs/core\"")
                || packageJsonText.contains("\"egg\"")
                || ((hasServerJs || hasAppJs || hasMainJs) && !hasIndexHtml);
    }

    String nodeStartCommand() {
        if (nodeHasStartScript()) {
            return "npm run start";
        }
        if (hasServerJs) {
            return "node server.js";
        }
        if (hasAppJs) {
            return "node app.js";
        }
        if (hasMainJs) {
            return "node main.js";
        }
        return "npm run start";
    }

    boolean pythonLooksFastApi() {
        return mainPyText.contains("fastapi(") || appPyText.contains("fastapi(");
    }

    boolean pythonLooksFlask() {
        return mainPyText.contains("flask(") || appPyText.contains("flask(");
    }

    String pythonStartupCommand() {
        if (mainPyText.contains("fastapi(")) {
            return "python -m uvicorn main:app --host 0.0.0.0 --port ${APP_PORT:-8080}";
        }
        if (appPyText.contains("fastapi(")) {
            return "python -m uvicorn app:app --host 0.0.0.0 --port ${APP_PORT:-8080}";
        }
        if (hasMainPy) {
            return "python main.py";
        }
        if (hasAppPy) {
            return "python app.py";
        }
        return "python main.py";
    }

    int customDockerfilePortOrDefault(int defaultPort) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?im)^\\s*expose\\s+(\\d+)\\s*$").matcher(dockerfileText);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : defaultPort;
    }

    String detectStaticSourceRoot() {
        if (hasIndexHtml) {
            return ".";
        }
        if (Files.isRegularFile(projectRoot.resolve("html/sky/index.html"))) {
            return "html/sky";
        }
        if (Files.isRegularFile(projectRoot.resolve("html/index.html"))) {
            return "html";
        }
        return ".";
    }

    String detectNginxConfigSource() {
        if (Files.isRegularFile(projectRoot.resolve("conf/nginx.conf"))) {
            return "conf/nginx.conf";
        }
        if (Files.isRegularFile(projectRoot.resolve("nginx.conf"))) {
            return "nginx.conf";
        }
        return null;
    }

    private static String readLower(Path file) {
        try {
            return Files.isRegularFile(file)
                    ? Files.readString(file).toLowerCase(Locale.ROOT)
                    : "";
        } catch (IOException ignored) {
            return "";
        }
    }
}
