package io.github.cuihairu.civgenesis.system.controller;

import io.github.cuihairu.civgenesis.core.error.CivErrorCodes;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameRoute;
import io.github.cuihairu.civgenesis.dispatcher.runtime.RequestContext;
import io.github.cuihairu.civgenesis.protocol.system.ResumeReq;
import io.github.cuihairu.civgenesis.protocol.system.ResumeResp;
import io.github.cuihairu.civgenesis.protocol.system.SystemMsgIds;
import io.github.cuihairu.civgenesis.system.SystemServerConfig;
import io.github.cuihairu.civgenesis.system.auth.AuthResult;
import io.github.cuihairu.civgenesis.system.auth.TokenAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@GameController
public final class SystemResumeController {
    private static final Logger log = LoggerFactory.getLogger(SystemResumeController.class);

    private final SystemServerConfig config;
    private final TokenAuthenticator authenticator;

    public SystemResumeController(SystemServerConfig config, TokenAuthenticator authenticator) {
        this.config = Objects.requireNonNull(config, "config");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
    }

    @GameRoute(id = SystemMsgIds.RESUME, open = true)
    public void resume(RequestContext ctx, ResumeReq req) {
        if (req.getToken().isBlank()) {
            ctx.error(io.github.cuihairu.civgenesis.core.error.CivError.of(CivErrorCodes.UNAUTHORIZED, "missing token", true));
            return;
        }
        if (!req.getServerEpoch().isBlank() && !req.getServerEpoch().equals(config.serverEpoch())) {
            ctx.reply(ResumeResp.newBuilder().setResult(ResumeResp.Result.RESUME_NEED_FULL_SYNC).build());
            return;
        }

        AuthResult auth;
        try {
            auth = authenticator.authenticate(req.getToken());
        } catch (Exception e) {
            log.warn("resume auth failed", e);
            ctx.error(io.github.cuihairu.civgenesis.core.error.CivError.of(CivErrorCodes.UNAUTHORIZED, "unauthorized", true));
            return;
        }

        if (auth.playerId() <= 0) {
            ctx.error(io.github.cuihairu.civgenesis.core.error.CivError.of(CivErrorCodes.UNAUTHORIZED, "unauthorized", true));
            return;
        }

        ctx.attachPlayer(auth.playerId(), auth.kickExistingSession());

        RequestContext.ResumeDecision decision = ctx.resume(req.getLastAppliedPushId());
        ResumeResp.Builder resp = ResumeResp.newBuilder()
                .setMinBufferedPushId(decision.minBufferedPushId())
                .setMaxBufferedPushId(decision.maxBufferedPushId());

        if (decision.resumeOk()) {
            resp.setResult(ResumeResp.Result.RESUME_OK);
            ctx.reply(resp.build());
            decision.replay().run();
            return;
        }

        resp.setResult(ResumeResp.Result.RESUME_NEED_FULL_SYNC);
        ctx.reply(resp.build());
    }
}
