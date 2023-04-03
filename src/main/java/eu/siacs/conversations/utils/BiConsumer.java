package eu.siacs.conversations.utils;

// Based on java.util.function.BiConsumer to avoid Android 24 dependency
public interface BiConsumer<T,U> {
    void accept(T t, U u);
}
