package io.github.cuihairu.civgenesis.ipc.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import io.github.cuihairu.civgenesis.ipc.IpcLink;
import io.github.cuihairu.civgenesis.registry.Endpoint;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AeronIpcTest {

    @Test
    void singleStreamChannelDeliversAndOffers() throws Exception {
        Aeron aeron = mock(Aeron.class);
        io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
        Subscription sub = mock(Subscription.class);
        when(aeron.addPublication("aeron:ipc", 1101)).thenReturn(pub);
        when(aeron.addSubscription("aeron:ipc", 1101)).thenReturn(sub);
        when(pub.sessionId()).thenReturn(42);
        when(pub.offer(any(UnsafeBuffer.class), anyInt(), anyInt())).thenReturn(1L);
        deliverOnPoll(sub, 7, "hello"); // header.sessionId 7 != self 42 -> delivered

        CountDownLatch got = new CountDownLatch(1);
        AtomicReference<byte[]> received = new AtomicReference<>();
        try (AeronIpcChannel channel = new AeronIpcChannel(aeron, "aeron:ipc", 1101, (link, msg) -> {
            received.set(msg);
            got.countDown();
        }, 10)) {
            assertTrue(channel.offer("hello".getBytes()));
            assertTrue(got.await(5, TimeUnit.SECONDS), "handler should receive the message");
            assertEquals("hello", new String(received.get()));
        }
        // close() stops the poll loop and closes the publication/subscription
        Mockito.verify(pub).close();
        Mockito.verify(sub).close();
    }

    @Test
    void singleStreamChannelFiltersOwnSessionAndToleratesNullHandler() throws Exception {
        Aeron aeron = mock(Aeron.class);
        io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
        Subscription sub = mock(Subscription.class);
        when(aeron.addPublication("aeron:ipc", 1102)).thenReturn(pub);
        when(aeron.addSubscription("aeron:ipc", 1102)).thenReturn(sub);
        when(pub.sessionId()).thenReturn(42);
        when(pub.offer(any(UnsafeBuffer.class), anyInt(), anyInt())).thenReturn(1L);
        // header.sessionId == self session -> the channel drops its own publication
        deliverOnPoll(sub, 42, "own");

        CountDownLatch got = new CountDownLatch(1);
        try (AeronIpcChannel channel = new AeronIpcChannel(aeron, "aeron:ipc", 1102, (link, msg) -> got.countDown(), 10)) {
            channel.offer("own".getBytes());
            assertFalse(got.await(500, TimeUnit.MILLISECONDS), "self messages are filtered");
        }
    }

    @Test
    void channelOfferAfterCloseReturnsFalseAndRejectsNull() {
        Aeron aeron = mock(Aeron.class);
        io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
        Subscription sub = mock(Subscription.class);
        when(aeron.addPublication("aeron:ipc", 1103)).thenReturn(pub);
        when(aeron.addSubscription("aeron:ipc", 1103)).thenReturn(sub);
        AeronIpcChannel channel = new AeronIpcChannel(aeron, "aeron:ipc", 1103, (link, msg) -> {
        }, 10);
        assertThrows(NullPointerException.class, () -> channel.offer(null));
        channel.close();
        assertFalse(channel.offer("late".getBytes()));
    }

    @Test
    void channelRequiresNonNullAeronAndChannel() {
        assertThrows(NullPointerException.class, () -> new AeronIpcChannel(null, 1104, (link, msg) -> {
        }));
        Aeron aeron = mock(Aeron.class);
        assertThrows(NullPointerException.class, () -> new AeronIpcChannel(aeron, null, 1104, (link, msg) -> {
        }, 10));
    }

    @Test
    void pollFailuresAreSwallowedAndHandlerFailuresDoNotBreakLoop() throws Exception {
        Aeron aeron = mock(Aeron.class);
        io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
        Subscription sub = mock(Subscription.class);
        when(aeron.addPublication("aeron:ipc", 1105)).thenReturn(pub);
        when(aeron.addSubscription("aeron:ipc", 1105)).thenReturn(sub);
        when(pub.sessionId()).thenReturn(0);
        when(pub.offer(any(UnsafeBuffer.class), anyInt(), anyInt())).thenReturn(1L);
        // first poll throws (the poll loop must swallow it), subsequent polls deliver to a throwing handler
        when(sub.poll(any(FragmentHandler.class), anyInt()))
                .thenThrow(new IllegalStateException("poll boom"))
                .thenAnswer(inv -> {
                    deliver(inv.getArgument(0), 0, "x");
                    return 1;
                });

        CountDownLatch handled = new CountDownLatch(1);
        AtomicBoolean handlerThrew = new AtomicBoolean(false);
        try (AeronIpcChannel channel = new AeronIpcChannel(aeron, "aeron:ipc", 1105, (link, msg) -> {
            if (!handlerThrew.getAndSet(true)) {
                handled.countDown();
            }
            throw new IllegalStateException("handler failed");
        }, 10)) {
            channel.offer("x".getBytes());
            assertTrue(handled.await(5, TimeUnit.SECONDS), "the throwing handler should still run");
        }
    }

    @Test
    void duplexChannelDeliversAndOffers() throws Exception {
        Aeron aeron = mock(Aeron.class);
        io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
        Subscription sub = mock(Subscription.class);
        when(aeron.addPublication("aeron:ipc", 1202)).thenReturn(pub);
        when(aeron.addSubscription("aeron:ipc", 1201)).thenReturn(sub);
        when(pub.offer(any(UnsafeBuffer.class), anyInt(), anyInt())).thenReturn(1L);
        deliverOnPoll(sub, 1, "ping");

        CountDownLatch got = new CountDownLatch(1);
        AtomicReference<byte[]> received = new AtomicReference<>();
        try (AeronDuplexIpcChannel channel = new AeronDuplexIpcChannel(aeron, "aeron:ipc", 1201, 1202, (link, msg) -> {
            received.set(msg);
            got.countDown();
        }, 10)) {
            assertTrue(channel.offer("ping".getBytes()));
            assertTrue(got.await(5, TimeUnit.SECONDS));
            assertEquals("ping", new String(received.get()));
        }
        Mockito.verify(pub).close();
        Mockito.verify(sub).close();
    }

    @Test
    void duplexRejectsNonPositiveStreamId() {
        Aeron aeron = mock(Aeron.class);
        assertThrows(IllegalArgumentException.class,
                () -> new AeronDuplexIpcChannel(aeron, 0, 1203, (link, msg) -> {
                }));
        assertThrows(IllegalArgumentException.class,
                () -> new AeronDuplexIpcChannel(aeron, 1204, -1, (link, msg) -> {
                }));
    }

    @Test
    void dialerConnectsSingleAndDuplexAndCloses() throws Exception {
        try (MockedStatic<Aeron> aeronStatic = Mockito.mockStatic(Aeron.class)) {
            Aeron aeron = mock(Aeron.class);
            when(Aeron.connect(any(Aeron.Context.class))).thenReturn(aeron);
            io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
            Subscription sub = mock(Subscription.class);
            when(aeron.addPublication(any(String.class), anyInt())).thenReturn(pub);
            when(aeron.addSubscription(any(String.class), anyInt())).thenReturn(sub);
            when(pub.offer(any(UnsafeBuffer.class), anyInt(), anyInt())).thenReturn(1L);
            deliverOnPoll(sub, 0, "s");

            try (AeronIpcDialer dialer = new AeronIpcDialer(java.nio.file.Path.of("/tmp/aeron"))) {
                CountDownLatch singleGot = new CountDownLatch(1);
                IpcLink single = dialer.connect(new Endpoint("aeron:ipc?streamId=1301"), (link, msg) -> singleGot.countDown());
                assertTrue(single.offer("s".getBytes()));

                CountDownLatch duplexGot = new CountDownLatch(1);
                IpcLink duplex = dialer.connect(new Endpoint("aeron:ipc?inStreamId=1302&outStreamId=1303"), (link, msg) -> duplexGot.countDown());
                assertTrue(duplex.offer("d".getBytes()));
                assertTrue(singleGot.await(5, TimeUnit.SECONDS));
                assertTrue(duplexGot.await(5, TimeUnit.SECONDS));

                single.close();
                duplex.close();
            }
            Mockito.verify(aeron).close();
        }
    }

    @Test
    void dialerWithoutDefaultDirUsesEndpointDir() throws Exception {
        try (MockedStatic<Aeron> aeronStatic = Mockito.mockStatic(Aeron.class)) {
            Aeron aeron = mock(Aeron.class);
            when(Aeron.connect(any(Aeron.Context.class))).thenReturn(aeron);
            io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
            Subscription sub = mock(Subscription.class);
            when(aeron.addPublication(any(String.class), anyInt())).thenReturn(pub);
            when(aeron.addSubscription(any(String.class), anyInt())).thenReturn(sub);
            when(pub.offer(any(UnsafeBuffer.class), anyInt(), anyInt())).thenReturn(1L);
            deliverOnPoll(sub, 0, "z");

            try (AeronIpcDialer dialer = new AeronIpcDialer()) {
                CountDownLatch got = new CountDownLatch(1);
                IpcLink link = dialer.connect(new Endpoint("aeron:ipc?dir=/tmp/aeron&streamId=1304"), (l, msg) -> got.countDown());
                assertTrue(link.offer("z".getBytes()));
                assertTrue(got.await(5, TimeUnit.SECONDS));
                link.close();
            }
        }
    }

    @Test
    void nullHandlerAndNegativeOfferAreTolerated() throws Exception {
        Aeron aeron = mock(Aeron.class);
        io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
        Subscription sub = mock(Subscription.class);
        when(aeron.addPublication(any(String.class), anyInt())).thenReturn(pub);
        when(aeron.addSubscription(any(String.class), anyInt())).thenReturn(sub);
        when(pub.sessionId()).thenReturn(0);
        when(pub.offer(any(UnsafeBuffer.class), anyInt(), anyInt())).thenReturn(-1L); // not connected
        deliverOnPoll(sub, 5, "dropped"); // null handler -> the fragment is discarded

        try (AeronIpcChannel channel = new AeronIpcChannel(aeron, "aeron:ipc", 1106, null, 10)) {
            assertFalse(channel.offer("nope".getBytes())); // pub.offer returned -1
            Thread.sleep(100); // give the poll loop a chance to deliver into the null handler
        }
    }

    @Test
    void duplexHandlesNullHandlerThrowingHandlerAndPollFailure() throws Exception {
        Aeron aeron = mock(Aeron.class);
        io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
        Subscription sub = mock(Subscription.class);
        when(aeron.addPublication(any(String.class), anyInt())).thenReturn(pub);
        when(aeron.addSubscription(any(String.class), anyInt())).thenReturn(sub);
        when(pub.offer(any(UnsafeBuffer.class), anyInt(), anyInt())).thenReturn(1L);
        when(sub.poll(any(FragmentHandler.class), anyInt()))
                .thenThrow(new IllegalStateException("poll boom"))
                .thenAnswer(inv -> {
                    deliver(inv.getArgument(0), 0, "y");
                    return 1;
                });

        CountDownLatch handled = new CountDownLatch(1);
        AtomicBoolean threw = new AtomicBoolean(false);
        try (AeronDuplexIpcChannel channel = new AeronDuplexIpcChannel(aeron, "aeron:ipc", 1301, 1302, (link, msg) -> {
            if (!threw.getAndSet(true)) {
                handled.countDown();
            }
            throw new IllegalStateException("handler failed");
        }, 10)) {
            assertTrue(channel.offer("y".getBytes()));
            assertTrue(handled.await(5, TimeUnit.SECONDS));
            channel.close();
            assertFalse(channel.offer("late".getBytes())); // closed
        }
    }

    @Test
    void duplexNegativeOfferAndNullHandler() throws Exception {
        Aeron aeron = mock(Aeron.class);
        io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
        Subscription sub = mock(Subscription.class);
        when(aeron.addPublication(any(String.class), anyInt())).thenReturn(pub);
        when(aeron.addSubscription(any(String.class), anyInt())).thenReturn(sub);
        when(pub.offer(any(UnsafeBuffer.class), anyInt(), anyInt())).thenReturn(-1L);
        deliverOnPoll(sub, 5, "dropped"); // null handler -> the fragment is discarded

        try (AeronDuplexIpcChannel channel = new AeronDuplexIpcChannel(aeron, "aeron:ipc", 1303, 1304, null, 10)) {
            assertFalse(channel.offer("nope".getBytes()));
            Thread.sleep(100); // give the poll loop a chance to deliver into the null handler
        }
    }

    @Test
    void closeToleratesPublicationAndSubscriptionFailures() {
        Aeron aeron = mock(Aeron.class);
        io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
        Subscription sub = mock(Subscription.class);
        when(aeron.addPublication(any(String.class), anyInt())).thenReturn(pub);
        when(aeron.addSubscription(any(String.class), anyInt())).thenReturn(sub);
        Mockito.doThrow(new IllegalStateException("pub close boom")).when(pub).close();
        Mockito.doThrow(new IllegalStateException("sub close boom")).when(sub).close();

        AeronIpcChannel channel = new AeronIpcChannel(aeron, "aeron:ipc", 1107, (link, msg) -> {
        }, 10);
        channel.close(); // both close() calls throw but are swallowed
    }

    @Test
    void duplexCloseToleratesPublicationAndSubscriptionFailures() {
        Aeron aeron = mock(Aeron.class);
        io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
        Subscription sub = mock(Subscription.class);
        when(aeron.addPublication(any(String.class), anyInt())).thenReturn(pub);
        when(aeron.addSubscription(any(String.class), anyInt())).thenReturn(sub);
        Mockito.doThrow(new IllegalStateException("pub close boom")).when(pub).close();
        Mockito.doThrow(new IllegalStateException("sub close boom")).when(sub).close();

        AeronDuplexIpcChannel channel = new AeronDuplexIpcChannel(aeron, "aeron:ipc", 1305, 1306, (link, msg) -> {
        }, 10);
        channel.close();
    }

    @Test
    void dialerCloseToleratesClientFailure() {
        try (MockedStatic<Aeron> aeronStatic = Mockito.mockStatic(Aeron.class)) {
            Aeron aeron = mock(Aeron.class);
            when(Aeron.connect(any(Aeron.Context.class))).thenReturn(aeron);
            io.aeron.ConcurrentPublication pub = mock(io.aeron.ConcurrentPublication.class);
            Subscription sub = mock(Subscription.class);
            when(aeron.addPublication(any(String.class), anyInt())).thenReturn(pub);
            when(aeron.addSubscription(any(String.class), anyInt())).thenReturn(sub);
            when(pub.offer(any(UnsafeBuffer.class), anyInt(), anyInt())).thenReturn(1L);
            Mockito.doThrow(new IllegalStateException("client close boom")).when(aeron).close();

            try (AeronIpcDialer dialer = new AeronIpcDialer(java.nio.file.Path.of("/tmp/aeron"))) {
                dialer.connect(new Endpoint("aeron:ipc?streamId=1307"), (link, msg) -> {
                });
            } // dialer.close() swallows the client failure
        }
    }

    private static void deliverOnPoll(Subscription sub, int sessionId, String message) {
        when(sub.poll(any(FragmentHandler.class), anyInt())).thenAnswer(inv -> {
            deliver(inv.getArgument(0), sessionId, message);
            return 1;
        });
    }

    private static void deliver(FragmentHandler handler, int sessionId, String message) {
        byte[] bytes = message.getBytes();
        Header header = mock(Header.class);
        when(header.sessionId()).thenReturn(sessionId);
        handler.onFragment(new UnsafeBuffer(bytes), 0, bytes.length, header);
    }
}
