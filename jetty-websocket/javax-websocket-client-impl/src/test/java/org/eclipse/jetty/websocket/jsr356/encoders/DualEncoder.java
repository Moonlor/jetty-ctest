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

package org.eclipse.jetty.websocket.jsr356.encoders;

import java.io.IOException;
import java.io.Writer;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

import org.eclipse.jetty.websocket.jsr356.samples.Fruit;

/**
 * Intentionally bad example of attempting to decode the same object to different message formats.
 */
public class DualEncoder implements Encoder.Text<Fruit>, Encoder.TextStream<Fruit>
{
    @Override
    public void destroy()
    {
    }

    @Override
    public String encode(Fruit fruit) throws EncodeException
    {
        return String.format("%s|%s", fruit.name, fruit.color);
    }

    @Override
    public void encode(Fruit fruit, Writer writer) throws EncodeException, IOException
    {
        writer.write(fruit.name);
        writer.write('|');
        writer.write(fruit.color);
    }

    @Override
    public void init(EndpointConfig config)
    {
    }
}
