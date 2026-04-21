package top.codejava.aiops.infrastructure.projectscan;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ProjectScanSnapshot(
        Path projectRoot,
        Set<String> rootEntries,
        List<String> fileNames,
        String dockerfileText,
        String packageJsonText,
        String pomText,
        String gradleText,
        String gradleKtsText,
        String appPyText,
        String mainPyText,
        String applicationConfigText
) {

    public boolean hasDockerfile() {
        return rootEntries.contains("dockerfile");
    }

    public boolean hasPom() {
        return rootEntries.contains("pom.xml");
    }

    public boolean hasGradle() {
        return rootEntries.contains("build.gradle") || rootEntries.contains("build.gradle.kts");
    }

    public boolean hasPackageJson() {
        return rootEntries.contains("package.json");
    }

    public boolean hasPackageLock() {
        return rootEntries.contains("package-lock.json");
    }

    public boolean hasPnpmLock() {
        return rootEntries.contains("pnpm-lock.yaml");
    }

    public boolean hasYarnLock() {
        return rootEntries.contains("yarn.lock");
    }

    public boolean hasRequirementsTxt() {
        return rootEntries.contains("requirements.txt");
    }

    public boolean hasPyproject() {
        return rootEntries.contains("pyproject.toml");
    }

    public boolean hasManagePy() {
        return rootEntries.contains("manage.py");
    }

    public boolean hasAppPy() {
        return rootEntries.contains("app.py");
    }

    public boolean hasMainPy() {
        return rootEntries.contains("main.py");
    }

    public boolean hasGoMod() {
        return rootEntries.contains("go.mod");
    }

    public boolean hasCargoToml() {
        return rootEntries.contains("cargo.toml");
    }

    public boolean hasIndexHtml() {
        return rootEntries.contains("index.html");
    }

    public boolean hasPythonProject() {
        return hasPyproject() || hasRequirementsTxt() || hasManagePy() || hasAppPy() || hasMainPy();
    }

    public String detectJavaFramework() {
        String combined = (pomText + gradleText + gradleKtsText).toLowerCase(Locale.ROOT);
        return combined.contains("spring-boot") ? "Spring Boot" : "Java Service";
    }

    public String detectNodeFramework() {
        String packageJson = packageJsonText.toLowerCase(Locale.ROOT);
        if (packageJson.contains("\"next\"")) {
            return "Next.js";
        }
        if (packageJson.contains("\"express\"")
                || packageJson.contains("\"koa\"")
                || packageJson.contains("\"fastify\"")
                || packageJson.contains("\"@nestjs/core\"")) {
            return "Node Service";
        }
        if (packageJson.contains("\"react\"")) {
            return "React";
        }
        if (packageJson.contains("\"vue\"")) {
            return "Vue";
        }
        if (packageJson.contains("\"@angular/core\"")) {
            return "Angular";
        }
        return "Node Application";
    }

    public String detectPythonFramework() {
        if (fileNames.stream().anyMatch(name -> name.equals("manage.py"))) {
            return "Django";
        }
        if (fileNames.stream().anyMatch(name -> name.equals("app.py") || name.equals("main.py"))) {
            return "Python Service";
        }
        return "Python Application";
    }

    public String detectNodeBuildTool() {
        if (hasPnpmLock()) {
            return "pnpm";
        }
        if (hasYarnLock()) {
            return "yarn";
        }
        return "npm";
    }

    public Integer detectDefaultPort(int fallback) {
        Matcher matcher = Pattern.compile("(?m)^\\s*port\\s*:\\s*(\\d+)\\s*$").matcher(applicationConfigText);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    public String detectJavaRelease() {
        Matcher releaseMatcher = Pattern.compile("<java.version>([^<]+)</java.version>").matcher(pomText);
        if (releaseMatcher.find()) {
            return releaseMatcher.group(1).trim();
        }
        String gradleCombined = gradleText + gradleKtsText;
        Matcher toolchainMatcher = Pattern.compile("languageVersion\\s*=\\s*JavaLanguageVersion\\.of\\((\\d+)\\)").matcher(gradleCombined);
        return toolchainMatcher.find() ? toolchainMatcher.group(1).trim() : null;
    }

    public boolean nodeHasStartScript() {
        return packageJsonText.toLowerCase(Locale.ROOT).contains("\"start\"");
    }

    public boolean nodeHasBuildScript() {
        return packageJsonText.toLowerCase(Locale.ROOT).contains("\"build\"");
    }

    public boolean nodeLooksNext() {
        String packageJson = packageJsonText.toLowerCase(Locale.ROOT);
        return packageJson.contains("\"next\"") || packageJson.contains("next dev");
    }

    public boolean nodeLooksFrontend() {
        String packageJson = packageJsonText.toLowerCase(Locale.ROOT);
        return (packageJson.contains("\"react\"")
                || packageJson.contains("\"vue\"")
                || packageJson.contains("\"@angular/core\"")
                || packageJson.contains("\"vite\""))
                && nodeHasBuildScript();
    }

    public boolean nodeLooksRuntime() {
        String packageJson = packageJsonText.toLowerCase(Locale.ROOT);
        return packageJson.contains("\"express\"")
                || packageJson.contains("\"koa\"")
                || packageJson.contains("\"fastify\"")
                || packageJson.contains("\"@nestjs/core\"")
                || packageJson.contains("\"egg\"")
                || ((rootEntries.contains("server.js") || rootEntries.contains("app.js") || rootEntries.contains("main.js")) && !hasIndexHtml());
    }

    public String packageInstallCommand() {
        if (hasPnpmLock()) {
            return "corepack enable && pnpm install --frozen-lockfile";
        }
        if (hasYarnLock()) {
            return "corepack enable && yarn install --frozen-lockfile";
        }
        if (hasPackageLock()) {
            return "npm ci";
        }
        return "npm install";
    }

    public String nodeStartCommand() {
        if (nodeHasStartScript()) {
            return "npm run start";
        }
        if (rootEntries.contains("server.js")) {
            return "node server.js";
        }
        if (rootEntries.contains("app.js")) {
            return "node app.js";
        }
        if (rootEntries.contains("main.js")) {
            return "node main.js";
        }
        return "npm run start";
    }

    public String pythonStartupCommand() {
        String mainPy = mainPyText.toLowerCase(Locale.ROOT);
        String appPy = appPyText.toLowerCase(Locale.ROOT);
        if (mainPy.contains("fastapi(")) {
            return "python -m uvicorn main:app --host 0.0.0.0 --port ${APP_PORT:-8080}";
        }
        if (appPy.contains("fastapi(")) {
            return "python -m uvicorn app:app --host 0.0.0.0 --port ${APP_PORT:-8080}";
        }
        if (hasMainPy()) {
            return "python main.py";
        }
        if (hasAppPy()) {
            return "python app.py";
        }
        return "python main.py";
    }

    public int customDockerfilePortOrDefault(int fallback) {
        Matcher matcher = Pattern.compile("(?im)^\\s*expose\\s+(\\d+)\\s*$").matcher(dockerfileText);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }
}
