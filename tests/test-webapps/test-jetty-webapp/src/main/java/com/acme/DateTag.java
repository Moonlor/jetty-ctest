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

package com.acme;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.BodyTagSupport;
import javax.servlet.jsp.tagext.Tag;

@SuppressWarnings("serial")
public class DateTag extends BodyTagSupport
{
    Tag parent;
    BodyContent body;
    String tz = "GMT";

    @Override
    public void setParent(Tag parent)
    {
        this.parent = parent;
    }

    @Override
    public Tag getParent()
    {
        return parent;
    }

    @Override
    public void setBodyContent(BodyContent content)
    {
        body = content;
    }

    @Override
    public void setPageContext(PageContext pageContext)
    {
    }

    public void setTz(String value)
    {
        tz = value;
    }

    @Override
    public int doStartTag() throws JspException
    {
        return EVAL_BODY_BUFFERED;
    }

    @Override
    public int doEndTag() throws JspException
    {
        return EVAL_PAGE;
    }

    @Override
    public void doInitBody() throws JspException
    {
    }

    @Override
    public int doAfterBody() throws JspException
    {
        try
        {
            SimpleDateFormat format = new SimpleDateFormat(body.getString());
            format.setTimeZone(TimeZone.getTimeZone(tz));
            body.getEnclosingWriter().write(format.format(new Date()));
            return SKIP_BODY;
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            throw new JspTagException(ex.toString());
        }
    }

    @Override
    public void release()
    {
        body = null;
    }
}

