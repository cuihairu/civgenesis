package io.github.cuihairu.civgenesis.dispatcher.route;

import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameRoute;
import io.github.cuihairu.civgenesis.dispatcher.runtime.RequestContext;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteScannerTest {

    @Test
    void routeDefinitionExposesAccessors() {
        RouteDefinition definition = new RouteDefinition(7, true, ShardBy.PLAYER);
        assertEquals(7, definition.msgId());
        assertTrue(definition.open());
        assertEquals(ShardBy.PLAYER, definition.shardBy());
    }

    @Test
    void scansControllersAndBuildsRoutes() {
        RouteTable table = new RouteScanner().scan(List.of(new OpenController()));
        assertNotNull(table.get(1));
        assertTrue(table.get(1).definition().open());
        assertEquals(1, table.get(1).definition().msgId());
    }

    @Test
    void rejectsPublicMethodInPackagePrivateController() {
        // a public @GameRoute method inside a non-public class is not accessible to the
        // public lookup, so MethodHandles.publicLookup().unreflect throws IllegalAccessException
        assertThrows(IllegalArgumentException.class,
                () -> new RouteScanner().scan(List.of(new PackagePrivateController())));
    }

    @Test
    void resolvesProxyControllerSuperclass() {
        // the controller's class name contains "$$", which the scanner unwraps to the real
        // controller superclass before checking the @GameController annotation
        RouteTable table = new RouteScanner().scan(List.of(new Proxy$$Controller()));
        assertNotNull(table.get(1));
    }

    @Test
    void skipsNullControllersAndPlainClasses() {
        java.util.List<Object> controllers = new java.util.ArrayList<>();
        controllers.add(null);
        controllers.add(new Object());
        controllers.add(new OpenController());
        assertDoesNotThrow(() -> new RouteScanner().scan(controllers));
    }

    @Test
    void rejectsDuplicateMsgId() {
        assertThrows(IllegalArgumentException.class,
                () -> new RouteScanner().scan(List.of(new DuplicateController())));
    }

    @Test
    void rejectsInvalidMethodSignature() {
        assertThrows(IllegalArgumentException.class,
                () -> new RouteScanner().scan(List.of(new BadSignatureController())));
    }

    @Test
    void rejectsNonVoidRawHandler() {
        assertThrows(IllegalArgumentException.class,
                () -> new RouteScanner().scan(List.of(new NonVoidRawController())));
    }

    @Test
    void routeTableExposesRoutesAsMap() {
        RouteTable table = new RouteScanner().scan(List.of(new OpenController()));
        assertEquals(1, table.asMap().size());
        assertNotNull(table.get(1));
    }

    @GameController
    public static final class OpenController {
        @GameRoute(id = 1, open = true)
        public void route(RequestContext ctx, ByteBuf payload) {
        }
    }

    @GameController
    public static final class DuplicateController {
        @GameRoute(id = 5, open = true)
        public void first(RequestContext ctx, ByteBuf payload) {
        }

        @GameRoute(id = 5, open = true)
        public void second(RequestContext ctx, ByteBuf payload) {
        }
    }

    @GameController
    public static final class BadSignatureController {
        @GameRoute(id = 6, open = true)
        public void wrong(RequestContext ctx) {
        }
    }

    @GameController
    public static final class NonVoidRawController {
        @GameRoute(id = 7, open = true)
        public String raw(RequestContext ctx, ByteBuf payload) {
            return "unexpected";
        }
    }

    @GameController
    static class PackagePrivateController {
        @GameRoute(id = 3, open = true)
        public void route(RequestContext ctx, ByteBuf payload) {
        }
    }

    @GameController
    public static class BaseController {
        @GameRoute(id = 1, open = true)
        public void route(RequestContext ctx, ByteBuf payload) {
        }
    }

    // name deliberately contains "$$" to exercise the proxy-superclass unwrap path
    public static final class Proxy$$Controller extends BaseController {
    }
}
