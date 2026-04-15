// aiops-cli/src/main/java/top/codejava/aiops/cli/command/PlanCommand.java
package top.codejava.aiops.cli.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import top.codejava.aiops.application.usecase.GeneratePlanUseCase;
import top.codejava.aiops.domain.model.PlanDraft;

import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * Generate deployment plan command
 * Scan the current directory and generate a Docker deployment plan
 */
@CommandLine.Command(
        name = "plan",
        description = "Scan project directory and generate deployment plan",
        mixinStandardHelpOptions = true
)
@Component
@RequiredArgsConstructor
public class PlanCommand implements Callable<Integer> {

    private final GeneratePlanUseCase generatePlanUseCase;

    @CommandLine.Option(
            names = {"-p", "--path"},
            description = "Project root directory path, defaults to current working directory"
    )
    private Path path;

    @Override
    public Integer call() {
        // Interactive prompt - local主导
        if (path == null) {
            System.out.println();
            System.out.print("Enter the absolute path of project to diagnose/deploy (default: current directory): ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                path = Path.of("").toAbsolutePath();
            } else {
                // Handle both Windows ( and / separators correctly
                input = input.replace('\\', '/');
                path = Path.of(input).toAbsolutePath();
            }
        }

        System.out.println();
        System.out.println("Scanning project at: " + path);
        System.out.println("========================================");

        try {
            PlanDraft draft = generatePlanUseCase.execute(path);

            System.out.println();
            System.out.println("\u001B[1mResult:\u001B[0m");
            System.out.println("========================================");
            System.out.println();

            if (draft.getArchitectureSummary() != null) {
                System.out.println("\u001B[1mArchitecture Summary:\u001B[0m");
                System.out.println(draft.getArchitectureSummary());
                System.out.println();
            }

            if (draft.getDockerfileContent() != null && !draft.getDockerfileContent().isEmpty()) {
                System.out.println("\u001B[1mGenerated Dockerfile:\u001B[0m");
                System.out.println("```dockerfile");
                System.out.println(draft.getDockerfileContent());
                System.out.println("```");
                System.out.println();
            }

            System.out.println("\u001B[1mSecurity Audit:\u001B[0m " +
                    (draft.isAuditPassed() ? "\u001B[32mPASSED\u001B[0m" : "\u001B[31mFAILED\u001B[0m"));

            if (!draft.isAuditPassed() && draft.getAuditFindings() != null && !draft.getAuditFindings().isEmpty()) {
                System.out.println("Findings:");
                for (String finding : draft.getAuditFindings()) {
                    System.out.println("  - " + finding);
                }
            }

            System.out.println();
            System.out.println("Done!");
            return 0;
        } catch (Exception e) {
            System.err.println("\u001B[31mError generating plan: " + e.getMessage() + "\u001B[0m");
            e.printStackTrace();
            return 1;
        }
    }
}
