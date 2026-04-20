package top.codejava.aiops.infrastructure.workflow.probe;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;

@Component
@Order(30)
public class TargetProfileAssemblyNode implements ProbeNode {

    @Override
    public void apply(ProbeContext context) {
        context.targetProfile(new WorkflowModels.TargetSystemProfile(
                context.request().credential().host(),
                valueOrDefault(context.profileValues().get("AIOPS_HOSTNAME"), context.request().credential().host()),
                valueOrDefault(context.profileValues().get("AIOPS_OS_FAMILY"), "Linux"),
                valueOrDefault(context.profileValues().get("AIOPS_OS_VERSION"), "unknown"),
                valueOrDefault(context.profileValues().get("AIOPS_ARCH"), "unknown"),
                parseLong(context.profileValues().get("AIOPS_TOTAL_MEM_MB"), 0L),
                parseLong(context.profileValues().get("AIOPS_FREE_MEM_MB"), 0L),
                resolveJavaVersion(context.profileValues().get("AIOPS_JAVA_VERSION"), context.localContext()),
                valueOrDefault(context.profileValues().get("AIOPS_SHELL"), "bash")
        ));
    }

    private String resolveJavaVersion(String javaVersion, WorkflowModels.LocalProjectContext localContext) {
        if (javaVersion != null && !javaVersion.isBlank()) {
            return javaVersion;
        }
        return localContext == null ? null : localContext.detectedJdkVersion();
    }

    private long parseLong(String rawValue, long defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
