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

package org.eclipse.jetty.webapp;

public class CloneConfiguration extends AbstractConfiguration
{
    final WebAppContext _template;

    CloneConfiguration(WebAppContext template)
    {
        _template = template;
    }

    @Override
    public void configure(WebAppContext context) throws Exception
    {
        for (Configuration configuration : _template.getConfigurations())
        {
            configuration.cloneConfigure(_template, context);
        }
    }

    @Override
    public void deconfigure(WebAppContext context) throws Exception
    {
        for (Configuration configuration : _template.getConfigurations())
        {
            configuration.deconfigure(context);
        }
    }
}
