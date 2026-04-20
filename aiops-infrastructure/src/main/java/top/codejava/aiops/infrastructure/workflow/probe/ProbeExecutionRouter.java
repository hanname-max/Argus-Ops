package top.codejava.aiops.infrastructure.workflow.probe;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProbeExecutionRouter {

    private final List<ProbeNode> probeNodes;

    public ProbeExecutionRouter(List<ProbeNode> probeNodes) {
        this.probeNodes = probeNodes;
    }

    public ProbeContext execute(ProbeContext context) {
        for (ProbeNode probeNode : probeNodes) {
            probeNode.apply(context);
        }
        return context;
    }
}
