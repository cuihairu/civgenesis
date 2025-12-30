package io.github.cuihairu.civgenesis.system.controller;

import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameRoute;
import io.github.cuihairu.civgenesis.dispatcher.runtime.RequestContext;
import io.github.cuihairu.civgenesis.protocol.system.CipherAlgorithm;
import io.github.cuihairu.civgenesis.protocol.system.ClientHelloReq;
import io.github.cuihairu.civgenesis.protocol.system.ClientHelloResp;
import io.github.cuihairu.civgenesis.protocol.system.CompressionAlgorithm;
import io.github.cuihairu.civgenesis.protocol.system.SystemMsgIds;
import io.github.cuihairu.civgenesis.system.SystemServerConfig;

import java.util.Objects;

@GameController
public final class SystemClientHelloController {
    private final SystemServerConfig config;

    public SystemClientHelloController(SystemServerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @GameRoute(id = SystemMsgIds.CLIENT_HELLO, open = true)
    public ClientHelloResp hello(RequestContext ctx, ClientHelloReq req) {
        int maxFrame = Math.min(config.maxFrameBytes(), Math.max(0, req.getMaxFrameBytes()));
        if (maxFrame <= 0) {
            maxFrame = config.maxFrameBytes();
        }

        CompressionAlgorithm selectedCompression = CompressionAlgorithm.COMPRESSION_NONE;
        if (req.getSupportedCompressionsList().contains(CompressionAlgorithm.COMPRESSION_ZSTD)) {
            selectedCompression = CompressionAlgorithm.COMPRESSION_ZSTD;
        } else if (req.getSupportedCompressionsList().contains(CompressionAlgorithm.COMPRESSION_LZ4)) {
            selectedCompression = CompressionAlgorithm.COMPRESSION_LZ4;
        } else if (req.getSupportedCompressionsList().contains(CompressionAlgorithm.COMPRESSION_GZIP)) {
            selectedCompression = CompressionAlgorithm.COMPRESSION_GZIP;
        }

        CipherAlgorithm selectedCipher = CipherAlgorithm.CIPHER_TLS;
        if (req.getSupportedCiphersList().contains(CipherAlgorithm.CIPHER_TLS)) {
            selectedCipher = CipherAlgorithm.CIPHER_TLS;
        } else if (req.getSupportedCiphersList().contains(CipherAlgorithm.CIPHER_NONE)) {
            selectedCipher = CipherAlgorithm.CIPHER_NONE;
        }

        return ClientHelloResp.newBuilder()
                .setProtocolVersion(config.protocolVersion())
                .setSelectedCompression(selectedCompression)
                .setSelectedCipher(selectedCipher)
                .setMaxFrameBytes(maxFrame)
                .setMaxInFlightReq(config.maxInFlightReq())
                .setMaxBufferedPushCount(config.maxBufferedPushCount())
                .setMaxBufferedPushAgeMillis(config.maxBufferedPushAgeMillis())
                .setServerEpoch(config.serverEpoch())
                .build();
    }
}

