package io.github.cuihairu.civgenesis.codec.tlv;

import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TlvFrameCodecTest {
    @Test
    void encodeDecodeRoundTrip() {
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4});
        Frame frame = new Frame(FrameType.REQ, 1001, 42, 0, 8, 123, 7, payload.retainedDuplicate());
        ByteBuf encoded = Unpooled.buffer();
        try (frame) {
            ByteBuf out = TlvFrameCodec.encode(encoded.alloc(), frame);
            encoded.writeBytes(out);
            out.release();

            Frame decoded = TlvFrameCodec.decode(encoded.duplicate());
            try (decoded) {
                assertEquals(FrameType.REQ, decoded.type());
                assertEquals(1001, decoded.msgId());
                assertEquals(42, decoded.seq());
                assertEquals(0, decoded.pushId());
                assertEquals(8, decoded.flags());
                assertEquals(123, decoded.ts());
                assertEquals(7, decoded.ackPushId());

                byte[] decodedPayload = new byte[decoded.payload().readableBytes()];
                decoded.payload().getBytes(decoded.payload().readerIndex(), decodedPayload);
                assertArrayEquals(new byte[]{1, 2, 3, 4}, decodedPayload);
            }
        } finally {
            payload.release();
            encoded.release();
        }
    }
}

