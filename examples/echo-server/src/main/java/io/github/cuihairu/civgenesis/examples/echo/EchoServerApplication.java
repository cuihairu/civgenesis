package io.github.cuihairu.civgenesis.examples.echo;

import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameRoute;
import io.github.cuihairu.civgenesis.dispatcher.runtime.RequestContext;
import io.github.cuihairu.civgenesis.examples.echo.spi.DemoSnapshotProvider;
import io.github.cuihairu.civgenesis.examples.echo.spi.DemoTokenAuthenticator;
import io.github.cuihairu.civgenesis.system.auth.TokenAuthenticator;
import io.github.cuihairu.civgenesis.system.snapshot.SnapshotProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class EchoServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EchoServerApplication.class, args);
    }

    @Bean
    public TokenAuthenticator tokenAuthenticator() {
        return new DemoTokenAuthenticator();
    }

    @Bean
    public SnapshotProvider snapshotProvider() {
        return new DemoSnapshotProvider();
    }

    @GameController
    public static final class EchoController {
        @GameRoute(id = 1000, open = true)
        public io.github.cuihairu.civgenesis.examples.echo.proto.EchoResp echo(RequestContext ctx, io.github.cuihairu.civgenesis.examples.echo.proto.EchoReq req) {
            return io.github.cuihairu.civgenesis.examples.echo.proto.EchoResp.newBuilder()
                    .setText(req.getText())
                    .build();
        }
    }
}
