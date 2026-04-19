package top.codejava.aiops.application.dto;

public record RemoteDeployResult(boolean success, String output, String error) {
}
