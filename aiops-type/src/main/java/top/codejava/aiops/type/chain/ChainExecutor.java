package top.codejava.aiops.type.chain;

import java.util.List;

public class ChainExecutor<T extends ChainContext> {

    private final List<ChainHandler<T>> handlers;

    public ChainExecutor(List<ChainHandler<T>> handlers) {
        this.handlers = handlers;
    }

    public void execute(T context) {
        for (ChainHandler<T> handler : handlers) {
            if (handler.supports(context)) {
                handler.handle(context);
            }
        }
    }
}
