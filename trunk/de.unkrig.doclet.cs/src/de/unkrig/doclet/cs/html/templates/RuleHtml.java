
/*
 * de.unkrig.doclet.cs - A doclet which generates metadata documents for a CheckStyle extension
 *
 * Copyright (c) 2015, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. The name of the author may not be used to endorse or promote products derived from this software without
 *       specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package de.unkrig.doclet.cs.html.templates;

import java.text.MessageFormat;

import com.sun.javadoc.RootDoc;

import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.doclet.cs.CsDoclet.Rule;
import de.unkrig.notemplate.HtmlTemplate;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractRightFrameHtml;

/**
 * Renderer for the "per-rule" documentation document.
 *
 * @copyright (C) 2015, SWM Services GmbH
 */
public
class RuleHtml extends AbstractRightFrameHtml {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    public void
    render(
        final Rule    rule,
        final Html    html,
        final RootDoc rootDoc,
        Options       options
    ) {

        MessageFormat titleMf   = new MessageFormat("Task \"&lt;{0}&gt;\"");
        MessageFormat headingMf = new MessageFormat("Task \"<code>&lt;{0}&gt;</code>\"");

        super.rRightFrameHtml(
            titleMf.format(new String[] { rule.name() }),                // windowTitle
            options,                                                     // options
            new String[] { "../stylesheet.css", "../stylesheet2.css" },  // stylesheetLinks
            new String[] { "nav1", AbstractRightFrameHtml.DISABLED },    // nav1
            new String[] { "nav2" },                                     // nav2
            new String[] { "nav3", AbstractRightFrameHtml.DISABLED },    // nav3
            new String[] { "nav4", AbstractRightFrameHtml.DISABLED },    // nav4
            new String[] { "nav5", AbstractRightFrameHtml.DISABLED },    // nav5
            new String[] { "nav6", AbstractRightFrameHtml.DISABLED },    // nav6
            () -> {
                String typeTitle   = titleMf.format(new String[] { rule.name() });
                String typeHeading = headingMf.format(new String[] { rule.name() });
                RuleHtml.this.l(
"<div class=\"header\">",
"  <div class=\"subTitle\">" + HtmlTemplate.esc(rule.family()) + "</div>",
"  <h2 title=\"" + typeTitle + "\" class=\"title\">" + typeHeading +  "</h2>",
"</div>",
"<div class=\"contentContainer\">",
"  <div class=\"description\">"
                );

                RuleHtml.this.printType(rule, html, rootDoc);

                RuleHtml.this.l(
"  </div>",
"</div>"
                );
            }
        );
    }

    private void
    printType(final Rule rule, final Html html, final RootDoc rootDoc) {
    }
}
