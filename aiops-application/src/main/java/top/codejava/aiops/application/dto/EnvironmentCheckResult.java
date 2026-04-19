package top.codejava.aiops.application.dto;

import java.util.List;

public record EnvironmentCheckResult(List<String> checks, List<String> risks, String conclusion) {
}
