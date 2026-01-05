package com.arcana.cloud.ssr.renderer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GraalJS runtime wrapper for executing JavaScript in the JVM.
 *
 * <p>Provides a pooled approach to GraalJS contexts for efficient
 * server-side JavaScript execution.</p>
 */
public class GraalJSRuntime implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GraalJSRuntime.class);

    private final Engine engine;
    private final BlockingQueue<Context> contextPool;
    private final int poolSize;
    private final long timeout;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a new GraalJS runtime.
     *
     * @param poolSize number of contexts to pool
     * @param timeout execution timeout in milliseconds
     */
    public GraalJSRuntime(int poolSize, long timeout) {
        this.poolSize = poolSize;
        this.timeout = timeout;
        this.contextPool = new LinkedBlockingQueue<>(poolSize);

        // Create shared engine for all contexts
        this.engine = Engine.newBuilder()
            .option("engine.WarnInterpreterOnly", "false")
            .build();

        // Pre-create contexts
        for (int i = 0; i < poolSize; i++) {
            contextPool.offer(createContext());
        }

        log.info("GraalJS runtime initialized with {} contexts", poolSize);
    }

    private Context createContext() {
        return Context.newBuilder("js")
            .engine(engine)
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(_ -> true)
            .allowExperimentalOptions(true)
            .option("js.ecmascript-version", "2022")
            .option("js.console", "true")
            .option("js.intl-402", "true")
            .build();
    }

    /**
     * Executes JavaScript code and returns the result.
     *
     * @param script the JavaScript code
     * @return the result value
     */
    public Value execute(String script) {
        Context context = acquireContext();
        try {
            return context.eval("js", script);
        } finally {
            releaseContext(context);
        }
    }

    /**
     * Executes a JavaScript file.
     *
     * @param path path to the JavaScript file
     * @return the result value
     */
    public Value executeFile(Path path) throws IOException {
        String content = Files.readString(path);
        Source source = Source.newBuilder("js", content, path.getFileName().toString())
            .cached(true)
            .build();

        Context context = acquireContext();
        try {
            return context.eval(source);
        } finally {
            releaseContext(context);
        }
    }

    /**
     * Executes a function with arguments.
     *
     * @param functionScript script that defines and returns a function
     * @param args arguments to pass
     * @return the result
     */
    public Value executeFunction(String functionScript, Object... args) {
        Context context = acquireContext();
        try {
            Value function = context.eval("js", functionScript);
            if (function.canExecute()) {
                return function.execute(args);
            }
            throw new IllegalArgumentException("Script did not return an executable function");
        } finally {
            releaseContext(context);
        }
    }

    /**
     * Sets up the SSR environment in a context.
     *
     * @param bundlePath path to the server bundle
     */
    public void setupSSREnvironment(Path bundlePath) throws IOException {
        String setupScript = """
            // Polyfills for Node.js APIs in browser context
            if (typeof global === 'undefined') {
                var global = this;
            }
            if (typeof window === 'undefined') {
                var window = global;
            }
            if (typeof document === 'undefined') {
                var document = {
                    createElement: function() { return {}; },
                    createTextNode: function() { return {}; },
                    querySelector: function() { return null; },
                    querySelectorAll: function() { return []; },
                    body: {},
                    head: {}
                };
            }
            if (typeof navigator === 'undefined') {
                var navigator = { userAgent: 'ArcanaSSR/1.0' };
            }
            if (typeof location === 'undefined') {
                var location = { href: '/', pathname: '/', search: '', hash: '' };
            }
            if (typeof console === 'undefined') {
                var console = {
                    log: function() {},
                    warn: function() {},
                    error: function() {},
                    info: function() {}
                };
            }
            if (typeof setTimeout === 'undefined') {
                var setTimeout = function(fn, delay) { fn(); return 0; };
            }
            if (typeof clearTimeout === 'undefined') {
                var clearTimeout = function() {};
            }
            if (typeof setInterval === 'undefined') {
                var setInterval = function(fn, delay) { return 0; };
            }
            if (typeof clearInterval === 'undefined') {
                var clearInterval = function() {};
            }
            if (typeof requestAnimationFrame === 'undefined') {
                var requestAnimationFrame = function(fn) { fn(Date.now()); return 0; };
            }
            if (typeof cancelAnimationFrame === 'undefined') {
                var cancelAnimationFrame = function() {};
            }
            if (typeof fetch === 'undefined') {
                var fetch = function() {
                    return Promise.reject(new Error('fetch not available in SSR'));
                };
            }
            if (typeof URL === 'undefined') {
                var URL = function(url) { this.href = url; };
            }
            if (typeof URLSearchParams === 'undefined') {
                var URLSearchParams = function() {};
            }

            // TextEncoder/TextDecoder polyfills
            if (typeof TextEncoder === 'undefined') {
                var TextEncoder = function() {};
                TextEncoder.prototype.encode = function(str) {
                    return new Uint8Array(str.split('').map(c => c.charCodeAt(0)));
                };
            }
            if (typeof TextDecoder === 'undefined') {
                var TextDecoder = function() {};
                TextDecoder.prototype.decode = function(arr) {
                    return String.fromCharCode.apply(null, arr);
                };
            }

            true;
            """;

        Context context = acquireContext();
        try {
            context.eval("js", setupScript);

            // Load the server bundle
            if (Files.exists(bundlePath)) {
                String bundle = Files.readString(bundlePath);
                context.eval("js", bundle);
                log.debug("Loaded SSR bundle: {}", bundlePath);
            }
        } finally {
            releaseContext(context);
        }
    }

    private Context acquireContext() {
        if (closed.get()) {
            throw new IllegalStateException("Runtime is closed");
        }

        try {
            Context context = contextPool.poll(timeout, TimeUnit.MILLISECONDS);
            if (context == null) {
                // Create temporary context if pool is exhausted
                log.warn("Context pool exhausted, creating temporary context");
                return createContext();
            }
            return context;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while acquiring context", e);
        }
    }

    private void releaseContext(Context context) {
        if (!closed.get()) {
            // Only return to pool if not at capacity
            if (!contextPool.offer(context)) {
                context.close();
            }
        } else {
            context.close();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            log.info("Shutting down GraalJS runtime");

            Context context;
            while ((context = contextPool.poll()) != null) {
                try {
                    context.close();
                } catch (Exception e) {
                    log.warn("Error closing context", e);
                }
            }

            engine.close();
            log.info("GraalJS runtime shutdown complete");
        }
    }
}
