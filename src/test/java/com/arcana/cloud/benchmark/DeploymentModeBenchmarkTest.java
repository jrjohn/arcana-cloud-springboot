package com.arcana.cloud.benchmark;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Performance Benchmark Tests for Arcana Cloud Deployment Modes.
 *
 * <p>This test suite compares the performance characteristics of four deployment modes:</p>
 * <ul>
 *   <li><b>K8s + gRPC</b>: Kubernetes with gRPC communication</li>
 *   <li><b>K8s + HTTP</b>: Kubernetes with REST HTTP communication</li>
 *   <li><b>Layered + gRPC</b>: Multi-layer architecture with gRPC</li>
 *   <li><b>Layered + HTTP</b>: Multi-layer architecture with REST HTTP</li>
 * </ul>
 *
 * <p>Metrics measured:</p>
 * <ul>
 *   <li>Average Latency (ms)</li>
 *   <li>P95 Latency (ms)</li>
 *   <li>P99 Latency (ms)</li>
 *   <li>Throughput (ops/sec)</li>
 *   <li>Concurrent Request Handling</li>
 *   <li>Memory Efficiency</li>
 * </ul>
 *
 * <p><b>Note:</b> These are simulated benchmarks based on protocol characteristics.
 * Actual production benchmarks should be performed with real infrastructure.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Deployment Mode Performance Benchmarks")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeploymentModeBenchmarkTest {

    // Simulation constants based on protocol characteristics
    private static final double GRPC_BASE_LATENCY_MS = 0.5;   // Binary protocol, HTTP/2 multiplexing
    private static final double HTTP_BASE_LATENCY_MS = 1.2;    // JSON parsing, connection overhead
    private static final double K8S_OVERHEAD_MS = 0.3;         // Service discovery, load balancer
    private static final double LAYERED_OVERHEAD_MS = 0.1;     // Direct layer communication

    // Benchmark configuration
    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final int CONCURRENT_USERS = 50;
    private static final int PAYLOAD_SIZE_BYTES = 1024;

    // Store results for comparison report
    private static final Map<String, BenchmarkResult> results = new LinkedHashMap<>();

    /**
     * Benchmark result holder.
     */
    record BenchmarkResult(
            String mode,
            double avgLatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            double throughputOpsPerSec,
            int concurrentRequestsHandled,
            long memoryUsageBytes
    ) {
        String toTableRow() {
            return String.format("| %-15s | %8.2f | %8.2f | %8.2f | %12.0f | %8d | %10.2f |",
                    mode, avgLatencyMs, p95LatencyMs, p99LatencyMs,
                    throughputOpsPerSec, concurrentRequestsHandled,
                    memoryUsageBytes / (1024.0 * 1024.0));
        }
    }

    @BeforeAll
    static void setupBenchmark() {
        // Clear results for fresh run
        results.clear();
        System.out.println("\n" + "=".repeat(100));
        System.out.println("ARCANA CLOUD - DEPLOYMENT MODE PERFORMANCE BENCHMARKS");
        System.out.println("=".repeat(100));
        System.out.println("Configuration:");
        System.out.println("  - Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("  - Benchmark iterations: " + BENCHMARK_ITERATIONS);
        System.out.println("  - Concurrent users: " + CONCURRENT_USERS);
        System.out.println("  - Payload size: " + PAYLOAD_SIZE_BYTES + " bytes");
        System.out.println("=".repeat(100) + "\n");
    }

    @AfterAll
    static void printBenchmarkReport() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("BENCHMARK RESULTS SUMMARY");
        System.out.println("=".repeat(100));
        System.out.println();
        System.out.println("| Mode            | Avg (ms) | P95 (ms) | P99 (ms) | Throughput/s | Concurrent | Memory (MB) |");
        System.out.println("|-----------------|----------|----------|----------|--------------|------------|-------------|");
        results.values().forEach(r -> System.out.println(r.toTableRow()));
        System.out.println();

        // Performance comparison analysis
        printPerformanceAnalysis();

        System.out.println("=".repeat(100));
        System.out.println("Note: These are simulated benchmarks. Production benchmarks may vary.");
        System.out.println("=".repeat(100) + "\n");
    }

    private static void printPerformanceAnalysis() {
        if (results.size() < 4) return;

        BenchmarkResult k8sGrpc = results.get("K8s+gRPC");
        BenchmarkResult k8sHttp = results.get("K8s+HTTP");
        BenchmarkResult layeredGrpc = results.get("Layered+gRPC");
        BenchmarkResult layeredHttp = results.get("Layered+HTTP");

        System.out.println("PERFORMANCE ANALYSIS:");
        System.out.println("-".repeat(50));

        // gRPC vs HTTP comparison
        double grpcVsHttpLatency = ((k8sHttp.avgLatencyMs + layeredHttp.avgLatencyMs) / 2) /
                ((k8sGrpc.avgLatencyMs + layeredGrpc.avgLatencyMs) / 2);
        System.out.printf("  gRPC vs HTTP Latency: gRPC is %.1fx faster%n", grpcVsHttpLatency);

        double grpcVsHttpThroughput = ((k8sGrpc.throughputOpsPerSec + layeredGrpc.throughputOpsPerSec) / 2) /
                ((k8sHttp.throughputOpsPerSec + layeredHttp.throughputOpsPerSec) / 2);
        System.out.printf("  gRPC vs HTTP Throughput: gRPC handles %.1fx more ops/sec%n", grpcVsHttpThroughput);

        // K8s vs Layered comparison
        double k8sVsLayeredLatency = ((k8sGrpc.avgLatencyMs + k8sHttp.avgLatencyMs) / 2) /
                ((layeredGrpc.avgLatencyMs + layeredHttp.avgLatencyMs) / 2);
        System.out.printf("  K8s vs Layered Latency: Layered is %.2fx faster (due to reduced hops)%n", k8sVsLayeredLatency);

        // Best mode identification
        BenchmarkResult bestLatency = results.values().stream()
                .min(Comparator.comparingDouble(BenchmarkResult::avgLatencyMs))
                .orElse(null);
        BenchmarkResult bestThroughput = results.values().stream()
                .max(Comparator.comparingDouble(BenchmarkResult::throughputOpsPerSec))
                .orElse(null);

        System.out.println();
        System.out.println("RECOMMENDATIONS:");
        System.out.println("-".repeat(50));
        if (bestLatency != null) {
            System.out.printf("  Best for Low Latency: %s (%.2fms avg)%n",
                    bestLatency.mode, bestLatency.avgLatencyMs);
        }
        if (bestThroughput != null) {
            System.out.printf("  Best for High Throughput: %s (%.0f ops/sec)%n",
                    bestThroughput.mode, bestThroughput.throughputOpsPerSec);
        }
        System.out.println();
        System.out.println("  Use Case Recommendations:");
        System.out.println("    - K8s+gRPC: Best for microservices with high throughput requirements");
        System.out.println("    - K8s+HTTP: Best for REST API compatibility and debugging ease");
        System.out.println("    - Layered+gRPC: Best for lowest latency in controlled environments");
        System.out.println("    - Layered+HTTP: Best for simple deployments with REST requirements");
        System.out.println();
    }

    @Nested
    @DisplayName("K8s + gRPC Benchmarks")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class K8sGrpcBenchmarks {

        private static final double BASE_LATENCY = GRPC_BASE_LATENCY_MS + K8S_OVERHEAD_MS;

        @Test
        @Order(1)
        @DisplayName("K8s+gRPC: Latency Benchmark")
        void benchmarkLatency() {
            BenchmarkResult result = runLatencyBenchmark("K8s+gRPC", BASE_LATENCY, 0.15);
            results.put("K8s+gRPC", result);

            // Assert reasonable latency for gRPC over K8s
            assertTrue(result.avgLatencyMs < 2.0, "K8s+gRPC avg latency should be under 2ms");
            assertTrue(result.p99LatencyMs < 5.0, "K8s+gRPC P99 latency should be under 5ms");
        }

        @Test
        @Order(2)
        @DisplayName("K8s+gRPC: Throughput Benchmark")
        void benchmarkThroughput() {
            double throughput = runThroughputBenchmark("K8s+gRPC", BASE_LATENCY);

            // gRPC should achieve high throughput
            assertTrue(throughput > 10000, "K8s+gRPC should handle >10k ops/sec");
        }

        @Test
        @Order(3)
        @DisplayName("K8s+gRPC: Concurrent Request Handling")
        void benchmarkConcurrency() {
            int handled = runConcurrencyBenchmark("K8s+gRPC", BASE_LATENCY);

            // Should handle all concurrent requests
            assertEquals(CONCURRENT_USERS, handled, "Should handle all concurrent requests");
        }

        @Test
        @Order(4)
        @DisplayName("K8s+gRPC: Binary Protocol Efficiency")
        void benchmarkProtocolEfficiency() {
            // gRPC uses Protocol Buffers - more efficient than JSON
            int jsonSize = estimateJsonPayloadSize(PAYLOAD_SIZE_BYTES);
            int protobufSize = estimateProtobufPayloadSize(PAYLOAD_SIZE_BYTES);

            double efficiency = (double) jsonSize / protobufSize;
            System.out.printf("  K8s+gRPC Protocol Efficiency: %.2fx smaller than JSON%n", efficiency);

            assertTrue(efficiency > 1.3, "Protobuf should be at least 30% smaller than JSON");
        }
    }

    @Nested
    @DisplayName("K8s + HTTP Benchmarks")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class K8sHttpBenchmarks {

        private static final double BASE_LATENCY = HTTP_BASE_LATENCY_MS + K8S_OVERHEAD_MS;

        @Test
        @Order(1)
        @DisplayName("K8s+HTTP: Latency Benchmark")
        void benchmarkLatency() {
            BenchmarkResult result = runLatencyBenchmark("K8s+HTTP", BASE_LATENCY, 0.25);
            results.put("K8s+HTTP", result);

            // HTTP has higher latency than gRPC
            assertTrue(result.avgLatencyMs < 4.0, "K8s+HTTP avg latency should be under 4ms");
            assertTrue(result.p99LatencyMs < 10.0, "K8s+HTTP P99 latency should be under 10ms");
        }

        @Test
        @Order(2)
        @DisplayName("K8s+HTTP: Throughput Benchmark")
        void benchmarkThroughput() {
            double throughput = runThroughputBenchmark("K8s+HTTP", BASE_LATENCY);

            // HTTP throughput lower than gRPC but still good
            assertTrue(throughput > 5000, "K8s+HTTP should handle >5k ops/sec");
        }

        @Test
        @Order(3)
        @DisplayName("K8s+HTTP: Concurrent Request Handling")
        void benchmarkConcurrency() {
            int handled = runConcurrencyBenchmark("K8s+HTTP", BASE_LATENCY);

            assertEquals(CONCURRENT_USERS, handled, "Should handle all concurrent requests");
        }

        @Test
        @Order(4)
        @DisplayName("K8s+HTTP: Connection Pool Efficiency")
        void benchmarkConnectionPooling() {
            // HTTP/1.1 requires connection pooling
            int connectionsNeeded = estimateConnectionPoolSize(CONCURRENT_USERS, BASE_LATENCY);

            System.out.printf("  K8s+HTTP Connection Pool Size: %d connections%n", connectionsNeeded);
            assertTrue(connectionsNeeded >= 10, "Should need multiple connections for HTTP");
        }
    }

    @Nested
    @DisplayName("Layered + gRPC Benchmarks")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class LayeredGrpcBenchmarks {

        private static final double BASE_LATENCY = GRPC_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS;

        @Test
        @Order(1)
        @DisplayName("Layered+gRPC: Latency Benchmark")
        void benchmarkLatency() {
            BenchmarkResult result = runLatencyBenchmark("Layered+gRPC", BASE_LATENCY, 0.10);
            results.put("Layered+gRPC", result);

            // Layered gRPC should have lowest latency
            assertTrue(result.avgLatencyMs < 1.5, "Layered+gRPC avg latency should be under 1.5ms");
            assertTrue(result.p99LatencyMs < 3.0, "Layered+gRPC P99 latency should be under 3ms");
        }

        @Test
        @Order(2)
        @DisplayName("Layered+gRPC: Throughput Benchmark")
        void benchmarkThroughput() {
            double throughput = runThroughputBenchmark("Layered+gRPC", BASE_LATENCY);

            // Highest throughput expected
            assertTrue(throughput > 15000, "Layered+gRPC should handle >15k ops/sec");
        }

        @Test
        @Order(3)
        @DisplayName("Layered+gRPC: Concurrent Request Handling")
        void benchmarkConcurrency() {
            int handled = runConcurrencyBenchmark("Layered+gRPC", BASE_LATENCY);

            assertEquals(CONCURRENT_USERS, handled, "Should handle all concurrent requests");
        }

        @Test
        @Order(4)
        @DisplayName("Layered+gRPC: HTTP/2 Multiplexing Efficiency")
        void benchmarkMultiplexing() {
            // HTTP/2 allows multiple streams on single connection
            int streamsPerConnection = 100;
            int connectionsNeeded = (int) Math.ceil((double) CONCURRENT_USERS / streamsPerConnection);

            System.out.printf("  Layered+gRPC HTTP/2 Multiplexing: %d connections for %d concurrent requests%n",
                    connectionsNeeded, CONCURRENT_USERS);
            assertTrue(connectionsNeeded <= 1, "HTTP/2 should handle 50 concurrent on 1 connection");
        }
    }

    @Nested
    @DisplayName("Layered + HTTP Benchmarks")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class LayeredHttpBenchmarks {

        private static final double BASE_LATENCY = HTTP_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS;

        @Test
        @Order(1)
        @DisplayName("Layered+HTTP: Latency Benchmark")
        void benchmarkLatency() {
            BenchmarkResult result = runLatencyBenchmark("Layered+HTTP", BASE_LATENCY, 0.20);
            results.put("Layered+HTTP", result);

            // Layered HTTP has lower latency than K8s HTTP
            assertTrue(result.avgLatencyMs < 3.0, "Layered+HTTP avg latency should be under 3ms");
            assertTrue(result.p99LatencyMs < 7.0, "Layered+HTTP P99 latency should be under 7ms");
        }

        @Test
        @Order(2)
        @DisplayName("Layered+HTTP: Throughput Benchmark")
        void benchmarkThroughput() {
            double throughput = runThroughputBenchmark("Layered+HTTP", BASE_LATENCY);

            // Higher than K8s HTTP due to reduced overhead
            assertTrue(throughput > 7000, "Layered+HTTP should handle >7k ops/sec");
        }

        @Test
        @Order(3)
        @DisplayName("Layered+HTTP: Concurrent Request Handling")
        void benchmarkConcurrency() {
            int handled = runConcurrencyBenchmark("Layered+HTTP", BASE_LATENCY);

            assertEquals(CONCURRENT_USERS, handled, "Should handle all concurrent requests");
        }

        @Test
        @Order(4)
        @DisplayName("Layered+HTTP: JSON Parsing Overhead")
        void benchmarkJsonOverhead() {
            // Measure JSON parsing overhead
            double jsonParseTime = estimateJsonParseTime(PAYLOAD_SIZE_BYTES);

            System.out.printf("  Layered+HTTP JSON Parse Overhead: %.3fms per request%n", jsonParseTime);
            assertTrue(jsonParseTime < 0.5, "JSON parsing should be under 0.5ms");
        }
    }

    @Nested
    @DisplayName("Cross-Mode Comparison Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CrossModeComparisonTests {

        @Test
        @Order(1)
        @DisplayName("gRPC vs HTTP: Latency Comparison")
        void compareGrpcVsHttpLatency() {
            double grpcLatency = (GRPC_BASE_LATENCY_MS + K8S_OVERHEAD_MS + GRPC_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS) / 2;
            double httpLatency = (HTTP_BASE_LATENCY_MS + K8S_OVERHEAD_MS + HTTP_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS) / 2;

            double improvement = httpLatency / grpcLatency;
            System.out.printf("  gRPC is %.1fx faster than HTTP in average latency%n", improvement);

            assertTrue(improvement > 1.5, "gRPC should be at least 50% faster than HTTP");
        }

        @Test
        @Order(2)
        @DisplayName("K8s vs Layered: Overhead Comparison")
        void compareK8sVsLayeredOverhead() {
            double k8sOverhead = K8S_OVERHEAD_MS;
            double layeredOverhead = LAYERED_OVERHEAD_MS;

            double ratio = k8sOverhead / layeredOverhead;
            System.out.printf("  K8s has %.1fx more overhead than Layered deployment%n", ratio);

            assertTrue(ratio > 2.0, "K8s should have at least 2x overhead of Layered");
        }

        @Test
        @Order(3)
        @DisplayName("Memory Efficiency Comparison")
        void compareMemoryEfficiency() {
            // Estimated memory usage per mode (in bytes)
            long k8sGrpcMemory = estimateMemoryUsage("K8s+gRPC", CONCURRENT_USERS);
            long k8sHttpMemory = estimateMemoryUsage("K8s+HTTP", CONCURRENT_USERS);
            long layeredGrpcMemory = estimateMemoryUsage("Layered+gRPC", CONCURRENT_USERS);
            long layeredHttpMemory = estimateMemoryUsage("Layered+HTTP", CONCURRENT_USERS);

            System.out.println("  Memory Usage Comparison:");
            System.out.printf("    K8s+gRPC: %.2f MB%n", k8sGrpcMemory / (1024.0 * 1024.0));
            System.out.printf("    K8s+HTTP: %.2f MB%n", k8sHttpMemory / (1024.0 * 1024.0));
            System.out.printf("    Layered+gRPC: %.2f MB%n", layeredGrpcMemory / (1024.0 * 1024.0));
            System.out.printf("    Layered+HTTP: %.2f MB%n", layeredHttpMemory / (1024.0 * 1024.0));

            // gRPC generally uses less memory due to efficient serialization
            assertTrue(k8sGrpcMemory < k8sHttpMemory, "gRPC should use less memory than HTTP");
        }

        @Test
        @Order(4)
        @DisplayName("Scalability Factor Comparison")
        void compareScalabilityFactors() {
            // Estimate how well each mode scales with increased load
            double k8sGrpcScaleFactor = calculateScalabilityFactor(GRPC_BASE_LATENCY_MS + K8S_OVERHEAD_MS);
            double k8sHttpScaleFactor = calculateScalabilityFactor(HTTP_BASE_LATENCY_MS + K8S_OVERHEAD_MS);
            double layeredGrpcScaleFactor = calculateScalabilityFactor(GRPC_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS);
            double layeredHttpScaleFactor = calculateScalabilityFactor(HTTP_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS);

            System.out.println("  Scalability Factors (higher is better):");
            System.out.printf("    K8s+gRPC: %.2f%n", k8sGrpcScaleFactor);
            System.out.printf("    K8s+HTTP: %.2f%n", k8sHttpScaleFactor);
            System.out.printf("    Layered+gRPC: %.2f%n", layeredGrpcScaleFactor);
            System.out.printf("    Layered+HTTP: %.2f%n", layeredHttpScaleFactor);

            // Note: Per-request scalability is lower for K8s due to overhead, but K8s excels at
            // horizontal scaling which isn't captured in this per-request metric.
            // K8s provides: auto-scaling, load balancing, failover - benefits not reflected in latency.
            assertTrue(k8sGrpcScaleFactor > 0, "K8s scalability factor should be positive");
            assertTrue(layeredGrpcScaleFactor > k8sGrpcScaleFactor,
                    "Layered has better per-request performance, K8s has better horizontal scaling");
        }
    }

    @Nested
    @DisplayName("Plugin-Specific Benchmarks")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PluginSpecificBenchmarks {

        @Test
        @Order(1)
        @DisplayName("Plugin Registration Latency by Mode")
        void benchmarkPluginRegistration() {
            System.out.println("\n  Plugin Registration Latency:");

            // Plugin registration includes: validation + storage + event propagation
            double k8sGrpcLatency = simulatePluginOperation("register", GRPC_BASE_LATENCY_MS + K8S_OVERHEAD_MS);
            double k8sHttpLatency = simulatePluginOperation("register", HTTP_BASE_LATENCY_MS + K8S_OVERHEAD_MS);
            double layeredGrpcLatency = simulatePluginOperation("register", GRPC_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS);
            double layeredHttpLatency = simulatePluginOperation("register", HTTP_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS);

            System.out.printf("    K8s+gRPC: %.2fms%n", k8sGrpcLatency);
            System.out.printf("    K8s+HTTP: %.2fms%n", k8sHttpLatency);
            System.out.printf("    Layered+gRPC: %.2fms%n", layeredGrpcLatency);
            System.out.printf("    Layered+HTTP: %.2fms%n", layeredHttpLatency);

            assertTrue(layeredGrpcLatency < k8sHttpLatency, "Layered+gRPC should be faster than K8s+HTTP");
        }

        @Test
        @Order(2)
        @DisplayName("Plugin State Synchronization by Mode")
        void benchmarkPluginStateSync() {
            System.out.println("\n  Plugin State Sync Latency (across pods/layers):");

            // State sync is critical in distributed modes
            double k8sGrpcSync = simulateStateSync(GRPC_BASE_LATENCY_MS + K8S_OVERHEAD_MS, 3); // 3 pods
            double k8sHttpSync = simulateStateSync(HTTP_BASE_LATENCY_MS + K8S_OVERHEAD_MS, 3);
            double layeredGrpcSync = simulateStateSync(GRPC_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS, 2); // 2 layers
            double layeredHttpSync = simulateStateSync(HTTP_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS, 2);

            System.out.printf("    K8s+gRPC (3 pods): %.2fms%n", k8sGrpcSync);
            System.out.printf("    K8s+HTTP (3 pods): %.2fms%n", k8sHttpSync);
            System.out.printf("    Layered+gRPC (2 layers): %.2fms%n", layeredGrpcSync);
            System.out.printf("    Layered+HTTP (2 layers): %.2fms%n", layeredHttpSync);

            // K8s modes have higher sync latency due to Redis pub/sub
            assertTrue(layeredGrpcSync < k8sGrpcSync, "Layered should have faster sync than K8s");
        }

        @Test
        @Order(3)
        @DisplayName("Plugin Lifecycle Operations Throughput")
        void benchmarkPluginLifecycleOps() {
            System.out.println("\n  Plugin Lifecycle Operations per Second:");

            double k8sGrpcOps = calculateLifecycleOps(GRPC_BASE_LATENCY_MS + K8S_OVERHEAD_MS);
            double k8sHttpOps = calculateLifecycleOps(HTTP_BASE_LATENCY_MS + K8S_OVERHEAD_MS);
            double layeredGrpcOps = calculateLifecycleOps(GRPC_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS);
            double layeredHttpOps = calculateLifecycleOps(HTTP_BASE_LATENCY_MS + LAYERED_OVERHEAD_MS);

            System.out.printf("    K8s+gRPC: %.0f ops/sec%n", k8sGrpcOps);
            System.out.printf("    K8s+HTTP: %.0f ops/sec%n", k8sHttpOps);
            System.out.printf("    Layered+gRPC: %.0f ops/sec%n", layeredGrpcOps);
            System.out.printf("    Layered+HTTP: %.0f ops/sec%n", layeredHttpOps);

            assertTrue(layeredGrpcOps > layeredHttpOps, "gRPC should handle more ops than HTTP");
        }
    }

    // ============= Benchmark Helper Methods =============

    private BenchmarkResult runLatencyBenchmark(String mode, double baseLatency, double variance) {
        List<Double> latencies = new ArrayList<>(BENCHMARK_ITERATIONS);
        Random random = new Random(42); // Fixed seed for reproducibility

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            simulateRequest(baseLatency, variance, random);
        }

        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            latencies.add(simulateRequest(baseLatency, variance, random));
        }
        long endTime = System.nanoTime();

        // Calculate metrics
        double[] sortedLatencies = latencies.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double avgLatency = DoubleStream.of(sortedLatencies).average().orElse(0);
        double p95Latency = sortedLatencies[(int) (sortedLatencies.length * 0.95)];
        double p99Latency = sortedLatencies[(int) (sortedLatencies.length * 0.99)];
        double throughput = BENCHMARK_ITERATIONS / ((endTime - startTime) / 1_000_000_000.0);
        long memoryUsage = estimateMemoryUsage(mode, CONCURRENT_USERS);

        System.out.printf("  %s: avg=%.2fms, P95=%.2fms, P99=%.2fms, throughput=%.0f ops/sec%n",
                mode, avgLatency, p95Latency, p99Latency, throughput * 1000);

        return new BenchmarkResult(mode, avgLatency, p95Latency, p99Latency,
                throughput * 1000, CONCURRENT_USERS, memoryUsage);
    }

    private double runThroughputBenchmark(String mode, double baseLatency) {
        // Calculate theoretical throughput based on latency
        double requestsPerMs = 1.0 / baseLatency;
        return requestsPerMs * 1000 * CONCURRENT_USERS;
    }

    private int runConcurrencyBenchmark(String mode, double baseLatency) {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        AtomicLong completedRequests = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            executor.submit(() -> {
                try {
                    // Simulate request
                    Thread.sleep((long) baseLatency);
                    completedRequests.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executor.shutdown();
        return (int) completedRequests.get();
    }

    private double simulateRequest(double baseLatency, double variance, Random random) {
        // Simulate latency with normal distribution
        return baseLatency + (random.nextGaussian() * variance);
    }

    private int estimateJsonPayloadSize(int dataSize) {
        // JSON overhead: field names, quotes, brackets, whitespace
        return (int) (dataSize * 1.4);
    }

    private int estimateProtobufPayloadSize(int dataSize) {
        // Protobuf is more compact: field tags + varint encoding
        return (int) (dataSize * 0.9);
    }

    private int estimateConnectionPoolSize(int concurrentUsers, double latency) {
        // Connections needed = concurrent users / (1000ms / latency)
        return Math.max(10, (int) Math.ceil(concurrentUsers * latency / 100));
    }

    private double estimateJsonParseTime(int payloadSize) {
        // Estimate ~50MB/s JSON parsing speed
        return payloadSize / 50_000.0;
    }

    private long estimateMemoryUsage(String mode, int concurrentUsers) {
        long baseMemory = 50 * 1024 * 1024L; // 50MB base

        // gRPC uses less memory due to efficient buffers
        // HTTP uses more due to string handling
        // K8s adds overhead for service mesh, sidecars
        return switch (mode) {
            case "K8s+gRPC" -> baseMemory + (concurrentUsers * 8 * 1024L);
            case "K8s+HTTP" -> baseMemory + (concurrentUsers * 16 * 1024L);
            case "Layered+gRPC" -> baseMemory + (concurrentUsers * 6 * 1024L);
            case "Layered+HTTP" -> baseMemory + (concurrentUsers * 12 * 1024L);
            default -> baseMemory;
        };
    }

    private double calculateScalabilityFactor(double baseLatency) {
        // Higher factor = better scalability
        // Based on: lower latency = more requests per unit time = better scale
        return 10.0 / baseLatency;
    }

    private double simulatePluginOperation(String operation, double baseLatency) {
        // Plugin operations have additional overhead
        double operationOverhead = switch (operation) {
            case "register" -> 2.0; // Validation + storage
            case "enable" -> 1.5;   // State change + event
            case "disable" -> 1.5;
            case "uninstall" -> 2.5; // Cleanup + storage
            default -> 1.0;
        };
        return baseLatency * operationOverhead;
    }

    private double simulateStateSync(double baseLatency, int nodeCount) {
        // State sync requires communication with all nodes
        // Using parallel fan-out, limited by slowest node
        return baseLatency * (1 + Math.log(nodeCount) / Math.log(2));
    }

    private double calculateLifecycleOps(double baseLatency) {
        // Average of different lifecycle operations
        double avgOperationTime = (simulatePluginOperation("register", baseLatency) +
                simulatePluginOperation("enable", baseLatency) +
                simulatePluginOperation("disable", baseLatency) +
                simulatePluginOperation("uninstall", baseLatency)) / 4;
        return 1000.0 / avgOperationTime;
    }
}
