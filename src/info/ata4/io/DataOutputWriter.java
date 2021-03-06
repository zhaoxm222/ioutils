/*
 ** 2013 June 15
 **
 ** The author disclaims copyright to this source code.  In place of
 ** a legal notice, here is a blessing:
 **    May you do good and not evil.
 **    May you find forgiveness for yourself and forgive others.
 **    May you share freely, never taking more than you give.
 */
package info.ata4.io;

import info.ata4.io.util.HalfFloat;
import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.*;
import org.apache.commons.io.EndianUtils;

/**
 * DataOutput extension for more data access methods.
 * 
 * @author Nico Bergemann <barracuda415 at yahoo.de>
 */
public class DataOutputWriter extends DataOutputWrapper implements DataOutputExtended {
    
    private static final String DEFAULT_CHARSET = "ASCII";
    
    public static DataOutputWriter newWriter(OutputStream os) {
        DataOutput out = new DataOutputStream(os);
        WritableByteChannel channel = Channels.newChannel(os);
        return new DataOutputWriter(out, channel, os, null);
    }
    
    public static DataOutputWriter newWriter(ByteBuffer bb) {
        ByteBufferWrapper out = new ByteBufferWrapper(bb);
        OutputStream os = out.getOutputStream();
        WritableByteChannel channel = out.getWriteChannel();
        return new DataOutputWriter(out, channel, os, bb);
    }
    
    public static DataOutputWriter newWriter(WritableByteChannel fc) {
        OutputStream os = new BufferedOutputStream(Channels.newOutputStream(fc));
        DataOutput out = new DataOutputStream(os);
        return new DataOutputWriter(out, fc, os, null);
    }
    
    public static DataOutputWriter newWriter(Path file) throws IOException {
        return newWriter(FileChannel.open(file, CREATE, WRITE));
    }
    
    private WritableByteChannel channel;
    private OutputStream stream;
    private ByteBuffer buffer;
    private boolean swap;
    
    public DataOutputWriter(DataOutput out) {
        super(out);
    }
 
    private DataOutputWriter(DataOutput out, WritableByteChannel channel, OutputStream stream, ByteBuffer buffer) {
        this(out);
        this.channel = channel;
        this.stream = stream;
        this.buffer = buffer;
    }
    
    public OutputStream getOutputStream() {
        return stream;
    }
    
    @Override
    public boolean isSwap() {
        if (super.isSwappable()) {
            return super.isSwap();
        } else {
            return swap;
        }
    }

    @Override
    public void setSwap(boolean swap) {
        if (super.isSwappable()) {
            super.setSwap(swap);
        } else {
            this.swap = swap;
        }
    }

    @Override
    public boolean isSwappable() {
        // supports manual swapping using EndianUtils if required
        return true;
    }
    
    @Override
    public void writeShort(int v) throws IOException {
        if (swap) {
            v = EndianUtils.swapShort((short) v);
        }
        super.writeShort(v);
    }
    
    @Override
    public void writeInt(int v) throws IOException {
        if (swap) {
            v = EndianUtils.swapInteger(v);
        }
        super.writeInt(v);
    }
    
    @Override
    public void writeFloat(float v) throws IOException {
        if (swap) {
            // NOTE: don't use writeFloat() plus EndianUtils.swapFloat() here!
            writeInt(Float.floatToRawIntBits(v));
        } else {
            super.writeFloat(v);
        }
    }
    
    @Override
    public void writeDouble(double v) throws IOException {
        if (swap) {
            // NOTE: don't use writeDouble() plus EndianUtils.swapDouble() here!
            writeLong(Double.doubleToRawLongBits(v));
        } else {
            super.writeDouble(v);
        }
    }

    @Override
    public void writeLong(long v) throws IOException {
        if (swap) {
            v = EndianUtils.swapLong(v);
        }
        super.writeLong(v);
    }
    
    @Override
    public void writeHalf(float f) throws IOException {
        int sval = HalfFloat.floatToIntBits(f);
        writeShort(sval);
    }

    @Override
    public void writeStringNull(String str, String charset) throws IOException {
        writeStringFixed(str, charset);
        writeByte(0);
    }
    
    @Override
    public void writeStringNull(String str) throws IOException {
        writeStringNull(str, DEFAULT_CHARSET);
    }
    
    @Override
    public void writeStringPadded(String str, int padding, String charset) throws IOException {
        int nullBytes = padding - str.length();
        if (nullBytes < 0) {
            throw new IllegalArgumentException("Invalid padding");
        }
        
        writeStringFixed(str, charset);
        skipBytes(nullBytes);
    }

    @Override
    public void writeStringPadded(String str, int padding) throws IOException {
        writeStringPadded(str, padding, DEFAULT_CHARSET);
    }
    
    @Override
    public void writeStringFixed(String str, String charset) throws IOException {
        write(str.getBytes(charset));
    }
    
    @Override
    public void writeStringFixed(String str) throws IOException {
        writeStringFixed(str, DEFAULT_CHARSET);
    }
    
    @Override
    public void writeStringInt(String str, String charset) throws IOException {
        writeInt(str.length());
        writeStringFixed(str, charset);
    }

    @Override
    public void writeStringInt(String str) throws IOException {
        writeStringInt(str, DEFAULT_CHARSET);
    }

    @Override
    public void writeStringShort(String str, String charset) throws IOException {
        if (str.length() > 0xffff) {
            throw new IllegalArgumentException("String is too long");
        }
        
        writeShort(str.length());
        writeStringFixed(str, charset);
    }

    @Override
    public void writeStringShort(String str) throws IOException {
        writeStringShort(str, DEFAULT_CHARSET);
    }

    @Override
    public void writeStringByte(String str, String charset) throws IOException {
        if (str.length() > 0xff) {
            throw new IllegalArgumentException("String is too long");
        }
        
        writeByte(str.length());
        writeStringFixed(str, charset);
    }

    @Override
    public void writeStringByte(String str) throws IOException {
        writeStringByte(str, DEFAULT_CHARSET);
    }
    
    @Override
    public void writeBuffer(ByteBuffer src) throws IOException {
        if (buffer != null) {
            buffer.put(src);
        } else {
            channel.write(src);
        }
    }
    
    @Override
    public void skipBytes(int n) throws IOException {
        for (int i = 0; i < n; i++) {
            writeByte(0);
        }
    }

    @Override
    public void align(int length, int align) throws IOException {
        if (align > 0) {
            int rem = length % align;
            if (rem != 0) {
                skipBytes(align - rem);
            }
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (stream != null) {
            stream.close();
        }
        if (channel != null) {
            channel.close();
        }
    }
}
