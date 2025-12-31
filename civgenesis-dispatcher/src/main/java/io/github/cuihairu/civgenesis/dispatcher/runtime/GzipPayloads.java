package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class GzipPayloads {
    private GzipPayloads() {}

    static ByteBuf compress(ByteBufAllocator alloc, ByteBuf in) throws IOException {
        if (in == null || !in.isReadable()) {
            return alloc.buffer(0);
        }
        ByteBuf out = alloc.buffer(Math.min(in.readableBytes(), 512));
        try (var input = new ByteBufInputStream(in, false);
             var output = new ByteBufOutputStream(out);
             var gzip = new GZIPOutputStream(output, true)) {
            input.transferTo(gzip);
        } catch (IOException e) {
            out.release();
            throw e;
        }
        return out;
    }

    static ByteBuf decompress(ByteBufAllocator alloc, ByteBuf in) throws IOException {
        if (in == null || !in.isReadable()) {
            return alloc.buffer(0);
        }
        ByteBuf out = alloc.buffer(Math.min(in.readableBytes() * 2, 1024));
        try (var input = new ByteBufInputStream(in, false);
             var gzip = new GZIPInputStream(input);
             var output = new ByteBufOutputStream(out)) {
            gzip.transferTo(output);
        } catch (IOException e) {
            out.release();
            throw e;
        }
        return out;
    }
}

