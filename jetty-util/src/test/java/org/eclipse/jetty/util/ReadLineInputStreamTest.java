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

package org.eclipse.jetty.util;

import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.ReadLineInputStream.Termination;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReadLineInputStreamTest
{
    BlockingArrayQueue<String> _queue = new BlockingArrayQueue<>();
    PipedInputStream _pin;
    volatile PipedOutputStream _pout;
    ReadLineInputStream _in;
    volatile Thread _writer;

    @BeforeEach
    public void before() throws Exception
    {
        _queue.clear();
        _pin = new PipedInputStream();
        _pout = new PipedOutputStream(_pin);
        _in = new ReadLineInputStream(_pin);
        _writer = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    OutputStream out = _pout;
                    while (out != null)
                    {
                        String s = _queue.poll(100, TimeUnit.MILLISECONDS);
                        if (s != null)
                        {
                            if ("__CLOSE__".equals(s))
                                _pout.close();
                            else
                            {
                                _pout.write(s.getBytes(StandardCharsets.UTF_8));
                                Thread.sleep(50);
                            }
                        }
                        out = _pout;
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    _writer = null;
                }
            }
        };
        _writer.start();
    }

    @AfterEach
    public void after() throws Exception
    {
        _pout = null;
        while (_writer != null)
        {
            Thread.sleep(10);
        }
    }

    @Test
    public void testCR() throws Exception
    {
        _queue.add("\rHello\rWorld\r\r");
        _queue.add("__CLOSE__");

        assertEquals("", _in.readLine());
        assertEquals("Hello", _in.readLine());
        assertEquals("World", _in.readLine());
        assertEquals("", _in.readLine());
        assertEquals(null, _in.readLine());
        assertEquals(EnumSet.of(Termination.CR), _in.getLineTerminations());
    }

    @Test
    public void testLF() throws Exception
    {
        _queue.add("\nHello\nWorld\n\n");
        _queue.add("__CLOSE__");

        assertEquals("", _in.readLine());
        assertEquals("Hello", _in.readLine());
        assertEquals("World", _in.readLine());
        assertEquals("", _in.readLine());
        assertEquals(null, _in.readLine());
        assertEquals(EnumSet.of(Termination.LF), _in.getLineTerminations());
    }

    @Test
    public void testCRLF() throws Exception
    {
        _queue.add("\r\nHello\r\nWorld\r\n\r\n");
        _queue.add("__CLOSE__");

        assertEquals("", _in.readLine());
        assertEquals("Hello", _in.readLine());
        assertEquals("World", _in.readLine());
        assertEquals("", _in.readLine());
        assertEquals(null, _in.readLine());
        assertEquals(EnumSet.of(Termination.CRLF), _in.getLineTerminations());
    }

    @Test
    public void testCRBlocking() throws Exception
    {
        _queue.add("");
        _queue.add("\r");
        _queue.add("Hello");
        _queue.add("\rWorld\r");
        _queue.add("\r");
        _queue.add("__CLOSE__");

        assertEquals("", _in.readLine());
        assertEquals("Hello", _in.readLine());
        assertEquals("World", _in.readLine());
        assertEquals("", _in.readLine());
        assertEquals(null, _in.readLine());
        assertEquals(EnumSet.of(Termination.CR), _in.getLineTerminations());
    }

    @Test
    public void testLFBlocking() throws Exception
    {
        _queue.add("");
        _queue.add("\n");
        _queue.add("Hello");
        _queue.add("\nWorld\n");
        _queue.add("\n");
        _queue.add("__CLOSE__");

        assertEquals("", _in.readLine());
        assertEquals("Hello", _in.readLine());
        assertEquals("World", _in.readLine());
        assertEquals("", _in.readLine());
        assertEquals(null, _in.readLine());
        assertEquals(EnumSet.of(Termination.LF), _in.getLineTerminations());
    }

    @Test
    public void testCRLFBlocking() throws Exception
    {
        _queue.add("\r");
        _queue.add("\nHello");
        _queue.add("\r\nWorld\r");
        _queue.add("\n\r");
        _queue.add("\n");
        _queue.add("");
        _queue.add("__CLOSE__");

        assertEquals("", _in.readLine());
        assertEquals("Hello", _in.readLine());
        assertEquals("World", _in.readLine());
        assertEquals("", _in.readLine());
        assertEquals(null, _in.readLine());
        assertEquals(EnumSet.of(Termination.CRLF), _in.getLineTerminations());
    }

    @Test
    public void testHeaderLFBodyLF() throws Exception
    {
        _queue.add("Header\n");
        _queue.add("\n");
        _queue.add("\nBody\n");
        _queue.add("\n");
        _queue.add("__CLOSE__");

        assertEquals("Header", _in.readLine());
        assertEquals("", _in.readLine());

        byte[] body = new byte[6];
        _in.read(body);
        assertEquals("\nBody\n", new String(body, 0, 6, StandardCharsets.UTF_8));

        assertEquals("", _in.readLine());
        assertEquals(null, _in.readLine());
        assertEquals(EnumSet.of(Termination.LF), _in.getLineTerminations());
    }

    @Test
    public void testHeaderCRBodyLF() throws Exception
    {
        _queue.add("Header\r");
        _queue.add("\r");
        _queue.add("\nBody\n");
        _queue.add("\r");
        _queue.add("__CLOSE__");

        assertEquals("Header", _in.readLine());
        assertEquals("", _in.readLine());

        byte[] body = new byte[6];
        _in.read(body);
        assertEquals("\nBody\n", new String(body, 0, 6, StandardCharsets.UTF_8));

        assertEquals("", _in.readLine());
        assertEquals(null, _in.readLine());
        assertEquals(EnumSet.of(Termination.CR), _in.getLineTerminations());
    }

    @Test
    public void testHeaderCRLFBodyLF() throws Exception
    {
        _queue.add("Header\r\n");
        _queue.add("\r\n");
        _queue.add("\nBody\n");
        _queue.add("\r\n");
        _queue.add("__CLOSE__");

        assertEquals("Header", _in.readLine());
        assertEquals("", _in.readLine());

        byte[] body = new byte[6];
        _in.read(body);
        assertEquals("\nBody\n", new String(body, 0, 6, StandardCharsets.UTF_8));

        assertEquals("", _in.readLine());
        assertEquals(null, _in.readLine());
        assertEquals(EnumSet.of(Termination.CRLF), _in.getLineTerminations());
    }
}
