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

package org.eclipse.jetty.jaas;

import java.io.Serializable;
import java.security.Principal;

/**
 * JAASPrincipal
 * <p>
 * Impl class of Principal interface.
 */
public class JAASPrincipal implements Principal, Serializable
{
    private static final long serialVersionUID = -5538962177019315479L;

    private final String _name;

    public JAASPrincipal(String userName)
    {
        this._name = userName;
    }

    @Override
    public boolean equals(Object p)
    {
        if (!(p instanceof JAASPrincipal))
            return false;

        return getName().equals(((JAASPrincipal)p).getName());
    }

    @Override
    public int hashCode()
    {
        return getName().hashCode();
    }

    @Override
    public String getName()
    {
        return this._name;
    }

    @Override
    public String toString()
    {
        return getName();
    }
}

    
