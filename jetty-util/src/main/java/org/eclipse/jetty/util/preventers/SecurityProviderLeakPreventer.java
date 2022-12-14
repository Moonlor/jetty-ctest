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

package org.eclipse.jetty.util.preventers;

import java.security.Security;

/**
 * SecurityProviderLeakPreventer
 *
 * Some security providers, such as sun.security.pkcs11.SunPKCS11 start a deamon thread,
 * which will use the thread context classloader. Load them here to ensure the classloader
 * is not a webapp classloader.
 *
 * Inspired by Tomcat JreMemoryLeakPrevention
 * 
 * @deprecated sun.security.pkcs11.SunPKCS11 class explicitly sets thread classloader to null
 */
@Deprecated
public class SecurityProviderLeakPreventer extends AbstractLeakPreventer
{

    /**
     * @see org.eclipse.jetty.util.preventers.AbstractLeakPreventer#prevent(java.lang.ClassLoader)
     */
    @Override
    public void prevent(ClassLoader loader)
    {
        Security.getProviders();
    }
}
