package com.arcana.cloud.config;

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the client channel built by {@link GrpcConfig} spreads calls across every address
 * the name resolver returns (what a Kubernetes headless Service gives), and — as the control —
 * pins every call to one backend when the resolver returns a single address (what a ClusterIP
 * Service's single virtual IP gives).
 *
 * <p>Three real Netty gRPC servers listen on ephemeral localhost ports. A test
 * {@link NameResolverProvider} with its own scheme resolves one logical name to those
 * addresses, standing in for DNS. The channel itself comes from {@link GrpcConfig#createChannel},
 * i.e. the same builder, service config and {@code round_robin} policy production uses.</p>
 */
class GrpcConfigLoadBalancingTest {

    private static final String SCHEME = "arcanalbtest";
    private static final int SERVERS = 3;
    private static final int CALLS = 30;

    private static final MethodDescriptor.Marshaller<byte[]> BYTES = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };

    private static final MethodDescriptor<byte[], byte[]> ECHO = MethodDescriptor.<byte[], byte[]>newBuilder()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(MethodDescriptor.generateFullMethodName("lbtest.Echo", "Echo"))
        .setRequestMarshaller(BYTES)
        .setResponseMarshaller(BYTES)
        .build();

    /** Logical name -> addresses the fake "DNS" returns for it. */
    private static final Map<String, List<SocketAddress>> RECORDS = new ConcurrentHashMap<>();

    private static final NameResolverProvider PROVIDER = new NameResolverProvider() {
        @Override
        protected boolean isAvailable() {
            return true;
        }

        @Override
        protected int priority() {
            return 5;
        }

        @Override
        public String getDefaultScheme() {
            return SCHEME;
        }

        @Override
        public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
            if (!SCHEME.equals(targetUri.getScheme())) {
                return null;
            }
            String name = targetUri.getPath().substring(1);
            return new NameResolver() {
                @Override
                public String getServiceAuthority() {
                    return name;
                }

                @Override
                public void start(Listener2 listener) {
                    List<EquivalentAddressGroup> groups = new ArrayList<>();
                    for (SocketAddress address : RECORDS.get(name)) {
                        groups.add(new EquivalentAddressGroup(address));
                    }
                    listener.onResult(ResolutionResult.newBuilder()
                        .setAddresses(groups)
                        .setAttributes(Attributes.EMPTY)
                        .build());
                }

                @Override
                public void shutdown() {
                    // nothing to release
                }
            };
        }
    };

    private final List<Server> servers = new ArrayList<>();
    private final AtomicInteger[] callsPerServer = new AtomicInteger[SERVERS];
    private final AtomicInteger[] transportsPerServer = new AtomicInteger[SERVERS];
    private ManagedChannel channel;

    @BeforeEach
    void startServers() throws IOException {
        NameResolverRegistry.getDefaultRegistry().register(PROVIDER);
        for (int i = 0; i < SERVERS; i++) {
            AtomicInteger calls = new AtomicInteger();
            AtomicInteger transports = new AtomicInteger();
            callsPerServer[i] = calls;
            transportsPerServer[i] = transports;
            ServerServiceDefinition echo = ServerServiceDefinition.builder("lbtest.Echo")
                .addMethod(ECHO, ServerCalls.asyncUnaryCall((request, observer) -> {
                    calls.incrementAndGet();
                    observer.onNext(request);
                    observer.onCompleted();
                }))
                .build();
            servers.add(NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(echo)
                .addTransportFilter(new ServerTransportFilter() {
                    @Override
                    public Attributes transportReady(Attributes attributes) {
                        transports.incrementAndGet();
                        return attributes;
                    }
                })
                .build()
                .start());
        }
    }

    @AfterEach
    void stopEverything() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        for (Server server : servers) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        NameResolverRegistry.getDefaultRegistry().deregister(PROVIDER);
        RECORDS.clear();
    }

    @Test
    void roundRobinSpreadsCallsAcrossEveryResolvedReplica() throws InterruptedException {
        // Headless Service: the name resolves to every replica.
        int[] counts = sendCallsThroughGrpcConfigChannel("tier-headless", SERVERS);

        System.out.println("[lb] headless (3 addresses resolved): calls per server = " + Arrays.toString(counts));
        assertThat(Arrays.stream(counts).sum()).isEqualTo(CALLS);
        assertThat(counts).doesNotContain(0).containsOnly(CALLS / SERVERS);
    }

    @Test
    void singleResolvedAddressPinsEveryCallToOneReplica() throws InterruptedException {
        // ClusterIP Service: DNS returns one virtual IP, so the channel holds one connection
        // and every call lands on whichever pod that connection reached.
        int[] counts = sendCallsThroughGrpcConfigChannel("tier-clusterip", 1);

        System.out.println("[lb] clusterip (1 address resolved): calls per server = " + Arrays.toString(counts));
        assertThat(counts).containsExactly(CALLS, 0, 0);
    }

    private int[] sendCallsThroughGrpcConfigChannel(String name, int resolvedAddresses)
        throws InterruptedException {
        List<SocketAddress> addresses = new ArrayList<>();
        for (int i = 0; i < resolvedAddresses; i++) {
            addresses.add(new InetSocketAddress("127.0.0.1", servers.get(i).getPort()));
        }
        RECORDS.put(name, addresses);

        channel = productionConfig().createChannel(SCHEME + ":///" + name);

        // Let round_robin connect to every resolved backend before measuring, so the
        // distribution reflects steady state rather than connection-setup order.
        channel.getState(true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (connectedServers() < resolvedAddresses && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(connectedServers()).isEqualTo(resolvedAddresses);
        for (int i = 0; i < SERVERS * 2; i++) {
            ClientCalls.blockingUnaryCall(channel, ECHO, io.grpc.CallOptions.DEFAULT, new byte[] {1});
        }
        for (AtomicInteger calls : callsPerServer) {
            calls.set(0);
        }

        for (int i = 0; i < CALLS; i++) {
            ClientCalls.blockingUnaryCall(channel, ECHO, io.grpc.CallOptions.DEFAULT, new byte[] {(byte) i});
        }
        return Arrays.stream(callsPerServer).mapToInt(AtomicInteger::get).toArray();
    }

    private int connectedServers() {
        return (int) Arrays.stream(transportsPerServer).filter(t -> t.get() > 0).count();
    }

    /** A GrpcConfig carrying the defaults from application.properties (plaintext, as in-cluster). */
    private static GrpcConfig productionConfig() {
        GrpcConfig config = new GrpcConfig();
        ReflectionTestUtils.setField(config, "tlsEnabled", false);
        ReflectionTestUtils.setField(config, "keepAliveTimeSeconds", 30L);
        ReflectionTestUtils.setField(config, "keepAliveTimeoutSeconds", 10L);
        ReflectionTestUtils.setField(config, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(config, "initialBackoffMs", 100L);
        ReflectionTestUtils.setField(config, "maxBackoffMs", 2000L);
        ReflectionTestUtils.setField(config, "deadlineMs", 30000L);
        ReflectionTestUtils.setField(config, "maxInboundMessageSize", 16777216);
        ReflectionTestUtils.setField(config, "shutdownTimeoutSeconds", 5L);
        return config;
    }
}
