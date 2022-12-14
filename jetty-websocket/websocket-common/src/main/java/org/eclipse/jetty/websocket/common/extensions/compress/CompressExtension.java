//
//  ========================================================================
//  Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.common.extensions.compress;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import org.eclipse.jetty.io.ByteBufferAccumulator;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty.util.compression.DeflaterPool;
import org.eclipse.jetty.util.compression.InflaterPool;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.common.OpCode;
import org.eclipse.jetty.websocket.common.extensions.AbstractExtension;
import org.eclipse.jetty.websocket.common.frames.DataFrame;

public abstract class CompressExtension extends AbstractExtension
{
    protected static final byte[] TAIL_BYTES = new byte[]{0x00, 0x00, (byte)0xFF, (byte)0xFF};
    protected static final ByteBuffer TAIL_BYTES_BUF = ByteBuffer.wrap(TAIL_BYTES);
    private static final Logger LOG = Log.getLogger(CompressExtension.class);

    /**
     * Never drop tail bytes 0000FFFF, from any frame type
     */
    protected static final int TAIL_DROP_NEVER = 0;
    /**
     * Always drop tail bytes 0000FFFF, from all frame types
     */
    protected static final int TAIL_DROP_ALWAYS = 1;
    /**
     * Only drop tail bytes 0000FFFF, from fin==true frames
     */
    protected static final int TAIL_DROP_FIN_ONLY = 2;

    /**
     * Always set RSV flag, on all frame types
     */
    protected static final int RSV_USE_ALWAYS = 0;
    /**
     * Only set RSV flag on first frame in multi-frame messages.
     * <p>
     * Note: this automatically means no-continuation frames have the RSV bit set
     */
    protected static final int RSV_USE_ONLY_FIRST = 1;

    /**
     * Inflater / Decompressed Buffer Size
     */
    protected static final int INFLATE_BUFFER_SIZE = 8 * 1024;

    /**
     * Deflater / Inflater: Maximum Input Buffer Size
     */
    protected static final int INPUT_MAX_BUFFER_SIZE = 8 * 1024;

    /**
     * Inflater : Output Buffer Size
     */
    private static final int DECOMPRESS_BUF_SIZE = 8 * 1024;

    private final Queue<FrameEntry> entries = new ArrayDeque<>();
    private final IteratingCallback flusher = new Flusher();
    private DeflaterPool deflaterPool;
    private InflaterPool inflaterPool;
    private Deflater deflaterImpl;
    private Inflater inflaterImpl;
    protected AtomicInteger decompressCount = new AtomicInteger(0);
    private int tailDrop = TAIL_DROP_NEVER;
    private int rsvUse = RSV_USE_ALWAYS;

    protected CompressExtension()
    {
        tailDrop = getTailDropMode();
        rsvUse = getRsvUseMode();
    }

    public void setInflaterPool(InflaterPool inflaterPool)
    {
        this.inflaterPool = inflaterPool;
    }

    public void setDeflaterPool(DeflaterPool deflaterPool)
    {
        this.deflaterPool = deflaterPool;
    }

    public Deflater getDeflater()
    {
        if (deflaterImpl == null)
        {
            deflaterImpl = deflaterPool.acquire();
        }
        return deflaterImpl;
    }

    public Inflater getInflater()
    {
        if (inflaterImpl == null)
        {
            inflaterImpl = inflaterPool.acquire();
        }
        return inflaterImpl;
    }

    /**
     * Indicates use of RSV1 flag for indicating deflation is in use.
     */
    @Override
    public boolean isRsv1User()
    {
        return true;
    }

    /**
     * Return the mode of operation for dropping (or keeping) tail bytes in frames generated by compress (outgoing)
     *
     * @return either {@link #TAIL_DROP_ALWAYS}, {@link #TAIL_DROP_FIN_ONLY}, or {@link #TAIL_DROP_NEVER}
     */
    abstract int getTailDropMode();

    /**
     * Return the mode of operation for RSV flag use in frames generate by compress (outgoing)
     *
     * @return either {@link #RSV_USE_ALWAYS} or {@link #RSV_USE_ONLY_FIRST}
     */
    abstract int getRsvUseMode();

    protected void forwardIncoming(Frame frame, ByteAccumulator accumulator)
    {
        DataFrame newFrame = new DataFrame(frame);
        // Unset RSV1 since it's not compressed anymore.
        newFrame.setRsv1(false);

        ByteBuffer buffer = getBufferPool().acquire(accumulator.getLength(), false);
        try
        {
            BufferUtil.clearToFill(buffer);
            accumulator.transferTo(buffer);
            newFrame.setPayload(buffer);
            nextIncomingFrame(newFrame);
        }
        finally
        {
            getBufferPool().release(buffer);
        }
    }

    protected ByteAccumulator newByteAccumulator()
    {
        int maxSize = Math.max(getPolicy().getMaxTextMessageSize(), getPolicy().getMaxBinaryMessageSize());
        return new ByteAccumulator(maxSize, getBufferPool());
    }

    protected void decompress(ByteAccumulator accumulator, ByteBuffer buf) throws DataFormatException
    {
        if (BufferUtil.isEmpty(buf))
            return;

        Inflater inflater = getInflater();
        while (buf.hasRemaining() && inflater.needsInput())
        {
            if (!supplyInput(inflater, buf))
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("Needed input, but no buffer could supply input");
                return;
            }

            while (true)
            {
                // The buffer returned by the accumulator might not be empty, so we must append starting from the limit.
                ByteBuffer buffer = accumulator.ensureBuffer(DECOMPRESS_BUF_SIZE);
                int decompressed = inflater.inflate(buffer.array(), buffer.arrayOffset() + buffer.limit(), buffer.capacity() - buffer.limit());
                buffer.limit(buffer.limit() + decompressed);
                accumulator.addLength(decompressed);
                if (LOG.isDebugEnabled())
                    LOG.debug("Decompressed {} bytes into buffer {} from {}", decompressed, BufferUtil.toDetailString(buffer), toDetail(inflater));

                if (decompressed <= 0)
                    break;
            }
        }

        if (LOG.isDebugEnabled())
            LOG.debug("Decompress: exiting {}", toDetail(inflater));
    }

    @Override
    public void outgoingFrame(Frame frame, WriteCallback callback, BatchMode batchMode)
    {
        // We use a queue and an IteratingCallback to handle concurrency.
        // We must compress and write atomically, otherwise the compression
        // context on the other end gets confused.

        if (flusher.isFailed())
        {
            notifyCallbackFailure(callback, new ZipException());
            return;
        }

        FrameEntry entry = new FrameEntry(frame, callback, batchMode);
        if (LOG.isDebugEnabled())
            LOG.debug("Queuing {}", entry);
        offerEntry(entry);
        flusher.iterate();
    }

    private void offerEntry(FrameEntry entry)
    {
        synchronized (this)
        {
            entries.offer(entry);
        }
    }

    private FrameEntry pollEntry()
    {
        synchronized (this)
        {
            return entries.poll();
        }
    }

    protected void notifyCallbackSuccess(WriteCallback callback)
    {
        try
        {
            if (callback != null)
                callback.writeSuccess();
        }
        catch (Throwable x)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Exception while notifying success of callback " + callback, x);
        }
    }

    protected void notifyCallbackFailure(WriteCallback callback, Throwable failure)
    {
        try
        {
            if (callback != null)
                callback.writeFailed(failure);
        }
        catch (Throwable x)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Exception while notifying failure of callback " + callback, x);
        }
    }

    private static boolean supplyInput(Inflater inflater, ByteBuffer buf)
    {
        if (buf == null || buf.remaining() <= 0)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("No data left left to supply to Inflater");
            return false;
        }

        byte[] input;
        int inputOffset;
        int len;

        if (buf.hasArray())
        {
            // no need to create a new byte buffer, just return this one.
            len = buf.remaining();
            input = buf.array();
            inputOffset = buf.position() + buf.arrayOffset();
            buf.position(buf.position() + len);
        }
        else
        {
            // Only create an return byte buffer that is reasonable in size
            len = Math.min(INPUT_MAX_BUFFER_SIZE, buf.remaining());
            input = new byte[len];
            inputOffset = 0;
            buf.get(input, 0, len);
        }

        inflater.setInput(input, inputOffset, len);
        if (LOG.isDebugEnabled())
            LOG.debug("Supplied {} input bytes: {}", input.length, toDetail(inflater));
        return true;
    }

    private static boolean supplyInput(Deflater deflater, ByteBuffer buf)
    {
        if (buf == null || buf.remaining() <= 0)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("No data left left to supply to Deflater");
            return false;
        }

        byte[] input;
        int inputOffset;
        int len;

        if (buf.hasArray())
        {
            // no need to create a new byte buffer, just return this one.
            len = buf.remaining();
            input = buf.array();
            inputOffset = buf.position() + buf.arrayOffset();
            buf.position(buf.position() + len);
        }
        else
        {
            // Only create an return byte buffer that is reasonable in size
            len = Math.min(INPUT_MAX_BUFFER_SIZE, buf.remaining());
            input = new byte[len];
            inputOffset = 0;
            buf.get(input, 0, len);
        }

        deflater.setInput(input, inputOffset, len);
        if (LOG.isDebugEnabled())
            LOG.debug("Supplied {} input bytes: {}", input.length, toDetail(deflater));
        return true;
    }

    private static String toDetail(Inflater inflater)
    {
        return String.format("Inflater[finished=%b,read=%d,written=%d,remaining=%d,in=%d,out=%d]", inflater.finished(), inflater.getBytesRead(),
            inflater.getBytesWritten(), inflater.getRemaining(), inflater.getTotalIn(), inflater.getTotalOut());
    }

    private static String toDetail(Deflater deflater)
    {
        return String.format("Deflater[finished=%b,read=%d,written=%d,in=%d,out=%d]", deflater.finished(), deflater.getBytesRead(), deflater.getBytesWritten(),
            deflater.getTotalIn(), deflater.getTotalOut());
    }

    public static boolean endsWithTail(ByteBuffer buf)
    {
        if ((buf == null) || (buf.remaining() < TAIL_BYTES.length))
        {
            return false;
        }
        int limit = buf.limit();
        for (int i = TAIL_BYTES.length; i > 0; i--)
        {
            if (buf.get(limit - i) != TAIL_BYTES[TAIL_BYTES.length - i])
            {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doStop() throws Exception
    {
        if (deflaterImpl != null)
        {
            deflaterPool.release(deflaterImpl);
            deflaterImpl = null;
        }

        if (inflaterImpl != null)
        {
            inflaterPool.release(inflaterImpl);
            inflaterImpl = null;
        }

        super.doStop();
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }

    private static class FrameEntry
    {
        private final Frame frame;
        private final WriteCallback callback;
        private final BatchMode batchMode;

        private FrameEntry(Frame frame, WriteCallback callback, BatchMode batchMode)
        {
            this.frame = frame;
            this.callback = callback;
            this.batchMode = batchMode;
        }

        @Override
        public String toString()
        {
            return frame.toString();
        }
    }

    private class Flusher extends IteratingCallback implements WriteCallback
    {
        private FrameEntry current;
        private boolean finished = true;

        @Override
        public void failed(Throwable x)
        {
            notifyCallbackFailure(current.callback, x);
            // If something went wrong, very likely the compression context
            // will be invalid, so we need to fail this IteratingCallback.
            super.failed(x);
        }

        @Override
        protected Action process() throws Exception
        {
            if (finished)
            {
                current = pollEntry();
                if (LOG.isDebugEnabled())
                    LOG.debug("Processing {}", current);
                if (current == null)
                    return Action.IDLE;
                deflate(current);
            }
            else
            {
                compress(current, false);
            }
            return Action.SCHEDULED;
        }

        private void deflate(FrameEntry entry)
        {
            Frame frame = entry.frame;
            BatchMode batchMode = entry.batchMode;
            if (OpCode.isControlFrame(frame.getOpCode()))
            {
                // Do not deflate control frames
                nextOutgoingFrame(frame, this, batchMode);
                return;
            }

            compress(entry, true);
        }

        private void compress(FrameEntry entry, boolean first)
        {
            // Get a chunk of the payload to avoid to blow
            // the heap if the payload is a huge mapped file.
            Frame frame = entry.frame;
            boolean fin = frame.isFin();
            ByteBuffer data = frame.getPayload();
            Deflater deflater = getDeflater();

            if (data == null)
                data = BufferUtil.EMPTY_BUFFER;

            int remaining = data.remaining();
            int outputLength = Math.max(256, data.remaining());
            if (LOG.isDebugEnabled())
                LOG.debug("Compressing {}: {} bytes in {} bytes chunk", entry, remaining, outputLength);

            ByteBuffer payload = BufferUtil.EMPTY_BUFFER;
            WriteCallback callback = this;
            if (!deflater.needsInput() || supplyInput(deflater, data))
            {
                ByteBufferPool bufferPool = getBufferPool();
                try (ByteBufferAccumulator accumulator = new ByteBufferAccumulator(bufferPool, false))
                {
                    while (true)
                    {
                        // The buffer returned by the accumulator might not be empty, so we must append starting from the limit.
                        ByteBuffer buffer = accumulator.ensureBuffer(8, outputLength);
                        int compressed = deflater.deflate(buffer.array(), buffer.arrayOffset() + buffer.limit(), buffer.capacity() - buffer.limit(), Deflater.SYNC_FLUSH);
                        buffer.limit(buffer.limit() + compressed);

                        if (LOG.isDebugEnabled())
                            LOG.debug("Wrote {} bytes to output buffer", accumulator);

                        if (compressed <= 0)
                            break;
                    }

                    ByteBuffer buffer = accumulator.takeByteBuffer();
                    payload = buffer;
                    callback = new WriteCallback()
                    {
                        @Override
                        public void writeFailed(Throwable x)
                        {
                            bufferPool.release(buffer);
                            Flusher.this.writeFailed(x);
                        }

                        @Override
                        public void writeSuccess()
                        {
                            bufferPool.release(buffer);
                            Flusher.this.writeSuccess();
                        }
                    };
                }
            }

            if (payload.remaining() > 0)
            {
                // Handle tail bytes generated by SYNC_FLUSH.
                if (LOG.isDebugEnabled())
                    LOG.debug("compressed[] bytes = {}", BufferUtil.toDetailString(payload));

                if (tailDrop == TAIL_DROP_ALWAYS)
                {
                    if (endsWithTail(payload))
                    {
                        payload.limit(payload.limit() - TAIL_BYTES.length);
                    }
                    if (LOG.isDebugEnabled())
                        LOG.debug("payload (TAIL_DROP_ALWAYS) = {}", BufferUtil.toDetailString(payload));
                }
                else if (tailDrop == TAIL_DROP_FIN_ONLY)
                {
                    if (frame.isFin() && endsWithTail(payload))
                    {
                        payload.limit(payload.limit() - TAIL_BYTES.length);
                    }
                    if (LOG.isDebugEnabled())
                        LOG.debug("payload (TAIL_DROP_FIN_ONLY) = {}", BufferUtil.toDetailString(payload));
                }
            }
            else if (fin)
            {
                // Special case: 7.2.3.6.  Generating an Empty Fragment Manually
                // https://tools.ietf.org/html/rfc7692#section-7.2.3.6
                payload = ByteBuffer.wrap(new byte[]{0x00});
            }

            if (LOG.isDebugEnabled())
                LOG.debug("Compressed {}: input:{} -> payload:{}", entry, outputLength, payload.remaining());

            boolean continuation = frame.getType().isContinuation() || !first;
            DataFrame chunk = new DataFrame(frame, continuation);
            if (rsvUse == RSV_USE_ONLY_FIRST)
            {
                chunk.setRsv1(!continuation);
            }
            else
            {
                // always set
                chunk.setRsv1(true);
            }
            chunk.setPayload(payload);
            chunk.setFin(fin);
            nextOutgoingFrame(chunk, callback, entry.batchMode);
        }

        @Override
        protected void onCompleteSuccess()
        {
            // This IteratingCallback never completes.
        }

        @Override
        protected void onCompleteFailure(Throwable x)
        {
            // Fail all the frames in the queue.
            FrameEntry entry;
            while ((entry = pollEntry()) != null)
            {
                notifyCallbackFailure(entry.callback, x);
            }
        }

        @Override
        public void writeSuccess()
        {
            if (finished)
                notifyCallbackSuccess(current.callback);
            succeeded();
        }

        @Override
        public void writeFailed(Throwable x)
        {
            failed(x);
        }
    }
}
