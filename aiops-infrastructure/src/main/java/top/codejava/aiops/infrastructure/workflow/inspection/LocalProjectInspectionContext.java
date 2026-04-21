package top.codejava.aiops.infrastructure.workflow.inspection;

import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.type.chain.ChainContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LocalProjectInspectionContext implements ChainContext {

    private static final Set<String> CONFIG_FILE_NAMES = Set.of(
            "application.yml",
            "application.yaml",
            "application.properties",
            "bootstrap.yml",
            "bootstrap.yaml",
            "bootstrap.properties",
            ".env",
            "settings.py",
            "config.py"
    );

    private static final Pattern CONFIG_FILE_PATTERN = Pattern.compile("(?i).+\\.(ya?ml|properties|env|toml|json)$");

    private final Path projectPath;
    private final Set<String> rootEntries;
    private final List<String> fileNames;
    private final List<ConfigFileSnapshot> configFiles;
    private final String projectName;

    private String language = "unknown";
    private String framework = "generic";
    private String buildTool = "unknown";
    private String packaging = "generic";
    private Integer defaultPort = 8080;
    private String jdkVersion;
    private WorkflowModels.PackagingPlan packagingPlan;
    private String aiSummary;

    private final List<WorkflowModels.StackComponent> components = new ArrayList<>();
    private final List<WorkflowModels.ConfigEvidence> evidences = new ArrayList<>();
    private final List<WorkflowModels.WorkflowWarning> warnings = new ArrayList<>();
    private final List<WorkflowModels.RequiredInputPrompt> requiredInputs = new ArrayList<>();
    private final List<WorkflowModels.DetectedConfigCandidate> detectedCandidates = new ArrayList<>();

    public LocalProjectInspectionContext(Path projectPath) {
        this.projectPath = projectPath.toAbsolutePath().normalize();
        this.rootEntries = listRootEntries(this.projectPath);
        this.fileNames = listFileNames(this.projectPath, 4);
        this.configFiles = listConfigFiles(this.projectPath, 4);
        this.projectName = this.projectPath.getFileName() == null ? this.projectPath.toString() : this.projectPath.getFileName().toString();
    }

    public Path projectPath() {
        return projectPath;
    }

    public Set<String> rootEntries() {
        return rootEntries;
    }

    public List<String> fileNames() {
        return fileNames;
    }

    public List<ConfigFileSnapshot> configFiles() {
        return configFiles;
    }

    public String projectName() {
        return projectName;
    }

    public String language() {
        return language;
    }

    public void language(String language) {
        this.language = language;
    }

    public String framework() {
        return framework;
    }

    public void framework(String framework) {
        this.framework = framework;
    }

    public String buildTool() {
        return buildTool;
    }

    public void buildTool(String buildTool) {
        this.buildTool = buildTool;
    }

    public String packaging() {
        return packaging;
    }

    public void packaging(String packaging) {
        this.packaging = packaging;
    }

    public Integer defaultPort() {
        return defaultPort;
    }

    public void defaultPort(Integer defaultPort) {
        this.defaultPort = defaultPort;
    }

    public String jdkVersion() {
        return jdkVersion;
    }

    public void jdkVersion(String jdkVersion) {
        this.jdkVersion = jdkVersion;
    }

    public WorkflowModels.PackagingPlan packagingPlan() {
        return packagingPlan;
    }

    public void packagingPlan(WorkflowModels.PackagingPlan packagingPlan) {
        this.packagingPlan = packagingPlan;
    }

    public String aiSummary() {
        return aiSummary;
    }

    public void aiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public List<WorkflowModels.StackComponent> components() {
        return components;
    }

    public List<WorkflowModels.ConfigEvidence> evidences() {
        return evidences;
    }

    public List<WorkflowModels.WorkflowWarning> warnings() {
        return warnings;
    }

    public List<WorkflowModels.RequiredInputPrompt> requiredInputs() {
        return requiredInputs;
    }

    public List<WorkflowModels.DetectedConfigCandidate> detectedCandidates() {
        return detectedCandidates;
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

    public boolean hasPythonProject() {
        return rootEntries.contains("pyproject.toml")
                || rootEntries.contains("requirements.txt")
                || rootEntries.contains("manage.py")
                || rootEntries.contains("app.py")
                || rootEntries.contains("main.py");
    }

    public boolean hasGoMod() {
        return rootEntries.contains("go.mod");
    }

    public boolean hasCargo() {
        return rootEntries.contains("cargo.toml");
    }

    public boolean hasIndexHtml() {
        return rootEntries.contains("index.html");
    }

    public boolean isBackendProject() {
        return "Java".equalsIgnoreCase(language)
                || "Python".equalsIgnoreCase(language)
                || ("JavaScript".equalsIgnoreCase(language) && framework.toLowerCase(Locale.ROOT).contains("service"));
    }

    public void addComponent(String name, String version, String role) {
        components.add(new WorkflowModels.StackComponent(name, version, role));
    }

    public void addEvidence(String source, String key, String valuePreview, boolean inferred) {
        evidences.add(new WorkflowModels.ConfigEvidence(source, key, valuePreview, inferred));
    }

    public void addWarning(String code, WorkflowModels.Severity severity, String message, String operatorAction) {
        warnings.add(new WorkflowModels.WorkflowWarning(code, severity, message, operatorAction));
    }

    public void addRequiredInput(String key, String label, String description, String placeholder) {
        if (requiredInputs.stream().noneMatch(item -> item.key().equalsIgnoreCase(key))) {
            requiredInputs.add(new WorkflowModels.RequiredInputPrompt(key, label, description, placeholder, true));
        }
    }

    public void addDetectedCandidate(String category,
                                     String location,
                                     String key,
                                     String valuePreview,
                                     String summary,
                                     boolean inferred,
                                     boolean requiresConfirmation) {
        String id = location + "|" + key;
        if (detectedCandidates.stream().noneMatch(item -> item.id().equalsIgnoreCase(id))) {
            detectedCandidates.add(new WorkflowModels.DetectedConfigCandidate(
                    id,
                    category,
                    location,
                    key,
                    valuePreview,
                    summary,
                    inferred,
                    requiresConfirmation
            ));
        }
    }

    public WorkflowModels.LocalProjectContext toLocalProjectContext() {
        WorkflowModels.ConfigurationInspection configurationInspection = new WorkflowModels.ConfigurationInspection(
                !requiredInputs.isEmpty(),
                !requiredInputs.isEmpty(),
                !detectedCandidates.isEmpty(),
                aiSummary,
                List.copyOf(requiredInputs),
                List.copyOf(detectedCandidates)
        );

        return new WorkflowModels.LocalProjectContext(
                projectName,
                language,
                framework,
                buildTool,
                packaging,
                jdkVersion,
                defaultPort,
                packagingPlan,
                configurationInspection,
                List.copyOf(components),
                List.copyOf(evidences)
        );
    }

    private Set<String> listRootEntries(Path projectPath) {
        try (Stream<Path> stream = Files.list(projectPath)) {
            return stream.map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        } catch (IOException ignored) {
            return Set.of();
        }
    }

    private List<String> listFileNames(Path projectPath, int depth) {
        try (Stream<Path> stream = Files.walk(projectPath, depth)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private List<ConfigFileSnapshot> listConfigFiles(Path projectPath, int depth) {
        try (Stream<Path> stream = Files.walk(projectPath, depth)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> isConfigFile(path.getFileName().toString()))
                    .map(path -> new ConfigFileSnapshot(projectPath.relativize(path), readIfExists(path)))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private boolean isConfigFile(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return CONFIG_FILE_NAMES.contains(normalized) || CONFIG_FILE_PATTERN.matcher(normalized).matches();
    }

    private String readIfExists(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (IOException ignored) {
            return "";
        }
    }
}
