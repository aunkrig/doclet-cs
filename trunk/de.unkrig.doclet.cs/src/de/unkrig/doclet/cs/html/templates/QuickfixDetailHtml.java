
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
 *    3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.unkrig.doclet.cs.html.templates;

import java.util.Collections;

import com.sun.javadoc.RootDoc;

import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.Consumer;
import de.unkrig.commons.util.collections.IterableUtil.ElementWithContext;
import de.unkrig.doclet.cs.CsDoclet.Quickfix;
import de.unkrig.notemplate.javadocish.IndexPages.IndexEntry;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractDetailHtml;
import de.unkrig.notemplate.javadocish.templates.AbstractRightFrameHtml;

/**
 * Renderer for the "per-rule" documentation document.
 */
public
class QuickfixDetailHtml extends AbstractDetailHtml {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    /**
     * Renderer for the "per-quickfix" documentation document.
     */
    public void
    render(
        final ElementWithContext<Quickfix> quickfixTriplet,
        final Html                         html,
        final RootDoc                      rootDoc,
        Options                            options,
        Consumer<? super IndexEntry>       indexEntries
    ) {

        Quickfix previousQuickfix = quickfixTriplet.previous();
        Quickfix quickfix         = quickfixTriplet.current();
        Quickfix nextQuickfix     = quickfixTriplet.next();

        super.rDetail(
            "Quickfix " + quickfix.label(),                             // windowTitle
            options,                                                    // options
            new String[] { "../stylesheet.css", "../stylesheet2.css" }, // stylesheetLinks
            new String[] {                                              // nav1
                "Overview",   "../overview-summary.html",
                "QUickfix",   AbstractRightFrameHtml.HIGHLIT,
                "Deprecated", "../deprecated-list.html",
                "Index",      "../" + (options.splitIndex ? "index-files/index-1.html" : "index-all.html"),
                "Help",       "../help-doc.html",
            },
            new String[] {                                              // nav2
                previousQuickfix == null ? "Prev Quickfix" : "<a href=\"\">Prev Quickfix</a>",
                nextQuickfix     == null ? "Next Quickfix" : "<a href=\"\">Next Quickfix</a>",
            },
            new String[] {                                              // nav3
                "Frames",    "../index.html?quickfixes/" + quickfix.label() + ".html",
                "No Frames", "#top",
            },
            new String[] {                                              // nav4
                "All Rules", "../allrules-noframe.html",
            },
            null,                                                       // subtitle
            "Quickfix \"" + quickfix.label() + "\"",                    // heading
            "Quickfix \"" + quickfix.label() + "\"",                    // headingTitle
            () -> {                                                     // prolog
                QuickfixDetailHtml.this.l(
"      <div class=\"description\">"
                );
                this.l(quickfix.longDescription());
                QuickfixDetailHtml.this.l(
"      </div>"
                );
            },
            Collections.emptyList()                                     // sections
        );
    }
}
