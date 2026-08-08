package com.nyarutoru.nekoplugin.features.graves;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class GravePersistenceQueue {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "NekoPlugin-GravePersistence");
        thread.setDaemon(true);
        return thread;
    });

    CompletableFuture<Boolean> submit(BooleanSupplier operation) {
        return CompletableFuture.supplyAsync(operation::getAsBoolean, executor);
    }

    void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
