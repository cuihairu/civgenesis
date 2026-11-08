package io.github.cuihairu.civgenesis.dispatcher.route;

import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameRoute;
import io.github.cuihairu.civgenesis.dispatcher.runtime.RequestContext;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.protocol.system.Error;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteScannerTest {
    @Test
    void ignoresUnannotatedAndInvokesRawAndReturningRoutes() throws Throwable {
        RouteTable routes = new RouteScanner().scan(java.util.Arrays.asList(null, new Plain(), new Controller()));
        assertEquals(2, routes.asMap().size());

        RouteInvoker raw = routes.get(1);
        assertEquals(ShardBy.CHANNEL, raw.definition().shardBy());
        assertTrue(raw.definition().open());
        raw.invoke(context(), new Frame(FrameType.REQ, 1, 1, 0, 0, 0, 0,
                Unpooled.wrappedBuffer(new byte[] {7}).retainedDuplicate()));

        RouteInvoker decoded = routes.get(2);
        assertFalse(decoded.definition().open());
        decoded.invoke(context(), new Frame(FrameType.REQ, 2, 2, 0, 0, 0, 0,
                Unpooled.EMPTY_BUFFER.retainedDuplicate()));
    }

    @Test
    void validatesDuplicateAndIllegalHandlers() {
        RouteScanner scanner = new RouteScanner();
        assertThrows(IllegalArgumentException.class, () -> scanner.scan(List.of(new Duplicate1(), new Duplicate2())));
        assertThrows(IllegalArgumentException.class, () -> scanner.scan(List.of(new BadContext())));
        assertThrows(IllegalArgumentException.class, () -> scanner.scan(List.of(new BadRaw())));
    }

    private static RequestContext context() {
        RequestContext context = Mockito.mock(RequestContext.class);
        try {
            Mockito.when(context.decode(Mockito.eq(Error.class), Mockito.any(Frame.class)))
                    .thenReturn(Error.getDefaultInstance());
            Mockito.when(context.rawPayload(Mockito.any(Frame.class))).thenAnswer(invocation -> {
                Frame frame = invocation.getArgument(0);
                return frame.payload() == null || !frame.payload().isReadable()
                        ? Unpooled.EMPTY_BUFFER
                        : Unpooled.wrappedBuffer(ByteBufUtil.getBytes(frame.payload()));
            });
            Mockito.doAnswer(invocation -> {
                invocation.getArgument(0);
                return null;
            }).when(context).reply(Mockito.any());
            return context;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class Plain {
        @GameRoute(id = 9)
        public void plain(RequestContext ctx, Error req) {}
    }

    @GameController
    public static final class Controller {
        @GameRoute(id = 1, open = true, shardBy = ShardBy.CHANNEL)
        public void raw(RequestContext ctx, ByteBuf payload) {
            assertEquals(7, payload.getByte(payload.readerIndex()));
        }

        @GameRoute(id = 2)
        public Error decoded(RequestContext ctx, Error req) {
            return req;
        }
    }

    @GameController
    public static final class Duplicate1 {
        @GameRoute(id = 10)
        public void one(RequestContext ctx, Error req) {}
    }

    @GameController
    public static final class Duplicate2 {
        @GameRoute(id = 10)
        public void two(RequestContext ctx, Error req) {}
    }

    @GameController
    public static final class BadContext {
        @GameRoute(id = 11)
        public void bad(String ctx, Error req) {}
    }

    @GameController
    public static final class BadRaw {
        @GameRoute(id = 12, open = true)
        public Error bad(RequestContext ctx, ByteBuf payload) {
            return null;
        }
    }
}
