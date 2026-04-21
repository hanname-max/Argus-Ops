package top.codejava.aiops.infrastructure.workflow.inspection;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;

@Component
@Order(40)
public class PackagingDecisionInspectionHandler implements LocalProjectInspectionHandler {

    @Override
    public boolean supports(LocalProjectInspectionContext context) {
        return true;
    }

    @Override
    public void handle(LocalProjectInspectionContext context) {
        WorkflowModels.PackagingPlan packagingPlan;

        if ("Java".equalsIgnoreCase(context.language())) {
            packagingPlan = new WorkflowModels.PackagingPlan(
                    "maven".equalsIgnoreCase(context.buildTool()) ? "JAVA_MAVEN_PACKAGE" : "JAVA_GRADLE_PACKAGE",
                    true,
                    false,
                    "maven".equalsIgnoreCase(context.buildTool()) ? "mvn -DskipTests clean package" : "./gradlew clean build -x test",
                    "target/*.jar or build/libs/*.jar",
                    "Java backends should package a runnable jar before remote deployment."
            );
        } else if ("JavaScript".equalsIgnoreCase(context.language()) && context.framework().toLowerCase().contains("service")) {
            packagingPlan = new WorkflowModels.PackagingPlan(
                    "NODE_RUNTIME_INSTALL",
                    false,
                    true,
                    "Install runtime dependencies before container startup.",
                    "node service runtime",
                    "Node.js backend services need dependency installation before probing remote deployment."
            );
        } else if ("JavaScript".equalsIgnoreCase(context.language())) {
            packagingPlan = new WorkflowModels.PackagingPlan(
                    "NODE_BUILD",
                    true,
                    true,
                    "Install dependencies and run the frontend build command.",
                    "dist/build/out",
                    "Node.js frontend projects should complete a local build decision before remote probing."
            );
        } else if ("Python".equalsIgnoreCase(context.language())) {
            packagingPlan = new WorkflowModels.PackagingPlan(
                    "PYTHON_RUNTIME_PREPARE",
                    false,
                    true,
                    "Install Python dependencies from requirements.txt or pyproject.toml.",
                    "python runtime environment",
                    "Python services need dependency preparation before remote deployment."
            );
        } else if ("Static".equalsIgnoreCase(context.language())) {
            packagingPlan = new WorkflowModels.PackagingPlan(
                    "STATIC_DIRECT",
                    false,
                    false,
                    "No build package required. Static assets can be served directly by Nginx.",
                    "static html/assets",
                    "Pure static pages can skip package/build steps."
            );
        } else {
            packagingPlan = new WorkflowModels.PackagingPlan(
                    "UNKNOWN_PREPARE",
                    false,
                    false,
                    "Review the repository structure manually.",
                    "unknown",
                    "No reliable packaging decision could be inferred."
            );
        }

        context.packagingPlan(packagingPlan);
    }
}
