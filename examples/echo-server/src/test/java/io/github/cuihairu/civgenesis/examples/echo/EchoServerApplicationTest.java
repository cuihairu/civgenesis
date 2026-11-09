package io.github.cuihairu.civgenesis.examples.echo;

import io.github.cuihairu.civgenesis.dispatcher.runtime.RequestContext;
import io.github.cuihairu.civgenesis.examples.echo.proto.EchoReq;
import io.github.cuihairu.civgenesis.examples.echo.proto.EchoResp;
import io.github.cuihairu.civgenesis.examples.echo.spi.DemoSnapshotProvider;
import io.github.cuihairu.civgenesis.examples.echo.spi.DemoTokenAuthenticator;
import io.github.cuihairu.civgenesis.system.auth.TokenAuthenticator;
import io.github.cuihairu.civgenesis.system.snapshot.SnapshotProvider;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class EchoServerApplicationTest {

    @Test
    void mainDelegatesToSpringApplicationRun() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            spring.when(() -> SpringApplication.run(EchoServerApplication.class, new String[0]))
                    .thenReturn(context);
            EchoServerApplication.main(new String[0]);
            spring.verify(() -> SpringApplication.run(EchoServerApplication.class, new String[0]));
        }
    }

    @Test
    void beanMethodsExposeDemoProviders() throws Exception {
        EchoServerApplication app = new EchoServerApplication();
        TokenAuthenticator authenticator = app.tokenAuthenticator();
        SnapshotProvider provider = app.snapshotProvider();
        assertTrue(authenticator instanceof DemoTokenAuthenticator);
        assertTrue(provider instanceof DemoSnapshotProvider);
        assertEquals(5, authenticator.authenticate(" 5 ").playerId());
        assertNotNull(provider.snapshot(1, 1).snapshotBytes());
    }

    @Test
    void echoRouteRepliesWithRequestText() {
        RequestContext ctx = mock(RequestContext.class);
        EchoReq req = EchoReq.newBuilder().setText("hello").build();
        EchoResp resp = new EchoServerApplication.EchoController().echo(ctx, req);
        assertEquals("hello", resp.getText());
    }
}
