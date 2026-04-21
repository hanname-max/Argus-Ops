package top.codejava.aiops.infrastructure.deploy.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.codejava.aiops.infrastructure.projectscan.ProjectScanService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentPlanningServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPlanStaticSiteWithNginx() throws IOException {
        Path project = tempDir.resolve("static-site");
        Files.createDirectories(project);
        Files.writeString(project.resolve("index.html"), "<html><body>ok</body></html>");

        DeploymentPlanningService service = createService();
        ResolvedDeploymentPlan resolved = service.planForProject(project, 8086, "/opt/app/workspace");

        assertEquals("NGINX_STATIC", resolved.deploymentPlan().strategyKey());
        assertTrue(resolved.previewScript().contains("strategy=nginx-static"));
        assertTrue(resolved.executionScript().contains("cd '/opt/app/workspace'"));
    }

    @Test
    void shouldPlanJavaMavenProject() throws IOException {
        Path project = tempDir.resolve("java-maven");
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
        Files.writeString(project.resolve("src/main/resources/application.yml"), "server:\n  port: 8080\n");

        DeploymentPlanningService service = createService();
        ResolvedDeploymentPlan resolved = service.planForProject(project, 8086, "/opt/app/workspace");

        assertEquals("JAVA_MAVEN", resolved.deploymentPlan().strategyKey());
        assertTrue(resolved.previewScript().contains("strategy=java-maven"));
        assertTrue(resolved.executionScript().contains("docker build -t"));
    }

    private DeploymentPlanningService createService() {
        ProjectScanService projectScanService = new ProjectScanService();
        ProjectDeploymentRuleEngine ruleEngine = new ProjectDeploymentRuleEngine();
        DeploymentPlanRenderer renderer = new DeploymentPlanRenderer();
        return new DeploymentPlanningService(projectScanService, ruleEngine, renderer);
    }
}
