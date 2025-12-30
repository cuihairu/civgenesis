package io.github.cuihairu.civgenesis.codec.tlv;

import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.protocol.ProtocolTags;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public final class TlvFrameCodec {
    private TlvFrameCodec() {}

    public static Frame decode(ByteBuf in) {
        FrameType type = null;
        int msgId = 0;
        long seq = 0;
        long pushId = 0;
        long flags = 0;
        long ts = 0;
        long ackPushId = 0;
        ByteBuf payload = null;

        while (in.isReadable()) {
            long tagLong = Varints.readUvarint(in);
            if (tagLong > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("TLV tag too large: " + tagLong);
            }
            int tag = (int) tagLong;
            long lengthLong = Varints.readUvarint(in);
            if (lengthLong > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("TLV length too large: " + lengthLong);
            }
            int length = (int) lengthLong;
            if (length < 0 || length > in.readableBytes()) {
                throw new IllegalArgumentException("Invalid TLV length: " + length);
            }

            switch (tag) {
                case ProtocolTags.TYPE -> {
                    long v = Varints.readUvarint(in, length);
                    if (v > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("FrameType id too large: " + v);
                    }
                    type = FrameType.fromId((int) v);
                }
                case ProtocolTags.MSG_ID -> {
                    long v = Varints.readUvarint(in, length);
                    if (v > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("msgId too large: " + v);
                    }
                    msgId = (int) v;
                }
                case ProtocolTags.SEQ -> seq = Varints.readUvarint(in, length);
                case ProtocolTags.PUSH_ID -> pushId = Varints.readUvarint(in, length);
                case ProtocolTags.FLAGS -> flags = Varints.readUvarint(in, length);
                case ProtocolTags.TS -> ts = Varints.readUvarint(in, length);
                case ProtocolTags.ACK_PUSH_ID -> ackPushId = Varints.readUvarint(in, length);
                case ProtocolTags.PAYLOAD -> {
                    if (payload != null) {
                        payload.release();
                    }
                    payload = in.readRetainedSlice(length);
                }
                default -> in.skipBytes(length);
            }
        }

        if (type == null) {
            if (payload != null) {
                payload.release();
            }
            throw new IllegalArgumentException("Missing required TLV tag: type");
        }
        return new Frame(type, msgId, seq, pushId, flags, ts, ackPushId, payload);
    }

    public static ByteBuf encode(ByteBufAllocator alloc, Frame frame) {
        ByteBuf out = alloc.buffer();
        try {
            writeUvarintField(out, ProtocolTags.TYPE, frame.type().id());
            if (frame.flags() != 0) {
                writeUvarintField(out, ProtocolTags.FLAGS, frame.flags());
            }
            if (frame.msgId() != 0) {
                writeUvarintField(out, ProtocolTags.MSG_ID, frame.msgId());
            }
            if (frame.seq() != 0) {
                writeUvarintField(out, ProtocolTags.SEQ, frame.seq());
            }
            if (frame.pushId() != 0) {
                writeUvarintField(out, ProtocolTags.PUSH_ID, frame.pushId());
            }
            if (frame.ts() != 0) {
                writeUvarintField(out, ProtocolTags.TS, frame.ts());
            }
            if (frame.payload() != null && frame.payload().isReadable()) {
                ByteBuf payload = frame.payload();
                int len = payload.readableBytes();
                Varints.writeUvarint(out, ProtocolTags.PAYLOAD);
                Varints.writeUvarint(out, len);
                out.writeBytes(payload, payload.readerIndex(), len);
            }
            if (frame.ackPushId() != 0) {
                writeUvarintField(out, ProtocolTags.ACK_PUSH_ID, frame.ackPushId());
            }
            return out;
        } catch (RuntimeException e) {
            out.release();
            throw e;
        }
    }

    private static void writeUvarintField(ByteBuf out, int tag, long value) {
        Varints.writeUvarint(out, tag);
        int valueSize = Varints.uvarintSize(value);
        Varints.writeUvarint(out, valueSize);
        Varints.writeUvarint(out, value);
    }
}

