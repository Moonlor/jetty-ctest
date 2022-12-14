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

import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Util meta Tests.
 */
public class DateCacheTest
{

    @Test
    @SuppressWarnings("ReferenceEquality")
    public void testDateCache() throws Exception
    {
        //@WAS: Test t = new Test("org.eclipse.jetty.util.DateCache");
        //                            012345678901234567890123456789
        DateCache dc = new DateCache("EEE, dd MMM yyyy HH:mm:ss zzz ZZZ", Locale.US, TimeZone.getTimeZone("GMT"));

        Thread.sleep(2000);

        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        long end = now + 3000;
        String f = dc.formatNow(now);
        String last = f;

        int hits = 0;
        int misses = 0;

        while (now < end)
        {
            last = f;
            f = dc.formatNow(now);
            // System.err.printf("%s %s%n",f,last==f);
            if (last == f)
                hits++;
            else
                misses++;

            TimeUnit.MILLISECONDS.sleep(100);
            now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        }
        assertThat(hits, Matchers.greaterThan(misses));
    }

    @Test
    public void testAllMethods()
    {
        // we simply check we do not have any exception
        DateCache dateCache = new DateCache();
        assertNotNull(dateCache.formatNow(System.currentTimeMillis()));
        assertNotNull(dateCache.formatNow(new Date().getTime()));
        assertNotNull(dateCache.formatNow(Instant.now().toEpochMilli()));

        assertNotNull(dateCache.format(new Date()));
        assertNotNull(dateCache.format(new Date(System.currentTimeMillis())));

        assertNotNull(dateCache.format(System.currentTimeMillis()));
        assertNotNull(dateCache.format(new Date().getTime()));
        assertNotNull(dateCache.format(Instant.now().toEpochMilli()));

        assertNotNull(dateCache.formatTick(System.currentTimeMillis()));
        assertNotNull(dateCache.formatTick(new Date().getTime()));
        assertNotNull(dateCache.formatTick(Instant.now().toEpochMilli()));

        assertNotNull(dateCache.getFormatString());

        assertNotNull(dateCache.getTimeZone());

        assertNotNull(dateCache.now());

        assertNotNull(dateCache.tick());
    }
}
