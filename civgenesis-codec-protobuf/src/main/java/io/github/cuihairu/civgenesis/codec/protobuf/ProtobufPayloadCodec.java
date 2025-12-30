package io.github.cuihairu.civgenesis.codec.protobuf;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.github.cuihairu.civgenesis.core.codec.PayloadCodec;
import io.github.cuihairu.civgenesis.core.error.CivError;
import io.github.cuihairu.civgenesis.protocol.system.Error;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtobufPayloadCodec implements PayloadCodec {
    private final Map<Class<?>, Parser<?>> parserCache = new ConcurrentHashMap<>();

    @Override
    public <T> T decode(int msgId, Class<T> targetType, ByteBuf payload) throws Exception {
        Objects.requireNonNull(targetType, "targetType");
        if (!MessageLite.class.isAssignableFrom(targetType)) {
            throw new IllegalArgumentException("ProtobufPayloadCodec only supports MessageLite targetType, got: " + targetType.getName());
        }
        if (payload == null || !payload.isReadable()) {
            throw new IllegalArgumentException("payload is empty for msgId=" + msgId);
        }
        byte[] bytes = ByteBufUtil.getBytes(payload, payload.readerIndex(), payload.readableBytes(), false);
        Parser<?> parser = parserCache.computeIfAbsent(targetType, ProtobufPayloadCodec::resolveParser);
        @SuppressWarnings("unchecked")
        T parsed = (T) parser.parseFrom(bytes);
        return parsed;
    }

    @Override
    public ByteBuf encode(ByteBufAllocator alloc, Object message) {
        Objects.requireNonNull(alloc, "alloc");
        Objects.requireNonNull(message, "message");
        if (!(message instanceof MessageLite m)) {
            throw new IllegalArgumentException("ProtobufPayloadCodec only supports MessageLite, got: " + message.getClass().getName());
        }
        byte[] bytes = m.toByteArray();
        ByteBuf out = alloc.buffer(bytes.length);
        out.writeBytes(bytes);
        return out;
    }

    @Override
    public ByteBuf encodeError(ByteBufAllocator alloc, CivError error) {
        Objects.requireNonNull(alloc, "alloc");
        Objects.requireNonNull(error, "error");
        Error.Builder builder = Error.newBuilder()
                .setCode(error.code())
                .setMessage(error.message())
                .setRetryable(error.retryable());
        builder.putAllDetail(error.detail());
        return encode(alloc, builder.build());
    }

    private static Parser<?> resolveParser(Class<?> messageClass) {
        try {
            Method getDefaultInstance = messageClass.getMethod("getDefaultInstance");
            Object defaultInstance = getDefaultInstance.invoke(null);
            if (defaultInstance instanceof MessageLite msg) {
                return msg.getParserForType();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        throw new IllegalArgumentException("Unable to resolve protobuf parser for: " + messageClass.getName());
    }
}

