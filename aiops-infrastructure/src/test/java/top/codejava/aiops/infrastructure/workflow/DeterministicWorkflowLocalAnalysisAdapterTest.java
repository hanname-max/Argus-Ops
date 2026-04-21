package top.codejava.aiops.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.infrastructure.workflow.inspection.ConfigPlaceholderInspectionHandler;
import top.codejava.aiops.infrastructure.workflow.inspection.KnownConfigInspectionHandler;
import top.codejava.aiops.infrastructure.workflow.inspection.PackagingDecisionInspectionHandler;
import top.codejava.aiops.infrastructure.workflow.inspection.ProjectTypeInspectionHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicWorkflowLocalAnalysisAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDetectRequiredSecretsFromJavaPlaceholders() throws IOException {
        Path project = tempDir.resolve("java-secret-app");
        Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(project.resolve("pom.xml"), """
                <project>
                  <properties>
                    <java.version>21</java.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        Files.writeString(project.resolve("src/main/resources/application.yml"), """
                ai:
                  provider:
                    api-key: ${OPENAI_API_KEY}
                    base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
                """);

        DeterministicWorkflowLocalAnalysisAdapter adapter = createAdapter();
        WorkflowModels.LocalAnalysisPayload payload = adapter.analyze(new WorkflowModels.AnalyzeLocalRequest(
                null,
                null,
                project.toString(),
                "test",
                true,
                true
        ));

        assertEquals("Java", payload.context().primaryLanguage());
        assertNotNull(payload.context().packagingPlan());
        assertEquals("JAVA_MAVEN_PACKAGE", payload.context().packagingPlan().strategyKey());
        assertTrue(payload.context().configurationInspection().requiresSecretInputs());
        assertTrue(payload.context().configurationInspection().requiredInputs().stream()
                .anyMatch(item -> "OPENAI_API_KEY".equals(item.key())));
    }

    @Test
    void shouldDetectConfigCandidatesWhenJavaConfigIsExplicit() throws IOException {
        Path project = tempDir.resolve("java-config-app");
        Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(project.resolve("pom.xml"), """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        Files.writeString(project.resolve("src/main/resources/application.yml"), """
                spring:
                  datasource:
                    url: jdbc:mysql://127.0.0.1:3306/demo
                    username: root
                    password: root123
                  redis:
                    host: 127.0.0.1
                    port: 6379
                """);

        DeterministicWorkflowLocalAnalysisAdapter adapter = createAdapter();
        WorkflowModels.LocalAnalysisPayload payload = adapter.analyze(new WorkflowModels.AnalyzeLocalRequest(
                null,
                null,
                project.toString(),
                "test",
                true,
                true
        ));

        assertEquals("Java", payload.context().primaryLanguage());
        assertFalse(payload.context().configurationInspection().requiresSecretInputs());
        assertTrue(payload.context().configurationInspection().requiresUserConfirmation());
        assertTrue(payload.context().configurationInspection().detectedCandidates().stream()
                .anyMatch(item -> "MYSQL".equals(item.category())));
        assertTrue(payload.context().configurationInspection().detectedCandidates().stream()
                .anyMatch(item -> "REDIS".equals(item.category())));
    }

    private DeterministicWorkflowLocalAnalysisAdapter createAdapter() {
        return new DeterministicWorkflowLocalAnalysisAdapter(List.of(
                new ProjectTypeInspectionHandler(),
                new ConfigPlaceholderInspectionHandler(),
                new KnownConfigInspectionHandler(),
                new PackagingDecisionInspectionHandler()
        ));
    }
}
