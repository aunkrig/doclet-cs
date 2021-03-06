
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

// SUPPRESS CHECKSTYLE WrapMethod:9999

package de.unkrig.doclet.cs.html.templates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.javadoc.RootDoc;

import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.Consumer;
import de.unkrig.commons.util.collections.ElementWithContext;
import de.unkrig.doclet.cs.CsDoclet.OptionProvider;
import de.unkrig.doclet.cs.CsDoclet.ValueOption;
import de.unkrig.notemplate.javadocish.IndexPages.IndexEntry;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractDetailHtml;
import de.unkrig.notemplate.javadocish.templates.AbstractRightFrameHtml;

/**
 * Renderer for the "per-option provider" documentation document.
 */
public
class OptionProviderDetailHtml extends AbstractDetailHtml {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    /**
     * Renderer for the "per-option provider" documentation document.
     */
    public void
    render(
        final ElementWithContext<OptionProvider> optionProviderTriplet,
        final Html                               html,
        final RootDoc                            rootDoc,
        Options                                  options,
        Consumer<? super IndexEntry>             indexEntries
    ) {

        final OptionProvider previousOptionProvider = optionProviderTriplet.previous();
        final OptionProvider optionProvider         = optionProviderTriplet.current();
        final OptionProvider nextOptionProvider     = optionProviderTriplet.next();

        List<SectionItem> constantItems = new ArrayList<AbstractDetailHtml.SectionItem>();
        for (ValueOption vo : optionProvider.valueOptions()) {

            constantItems.add(new SectionItem(
                vo.name(),                                         // anchor
                new String[] { vo.name(), vo.shortDescription() }, // summaryTableCells
                "Value Option \"" + vo.name() + "\"",              // detailTitle
                () -> {                                            // printDetailContent
                    String longDescription = vo.longDescription();
                    if (longDescription != null) this.p(longDescription);
                }
            ));
        }

        AbstractDetailHtml.Section constantsSection = new Section(
            "constants",                            // anchor
            "Constants",                            // navigationLinkLabel
            "Constant Summary",                     // summaryTitle1
            "Constants",                            // summaryTitle2
            new String[] { "Name", "Description" }, // summaryTableHeadings
            "Constant Detail",                      // detailTitle
            null,                                   // detailDescription
            null                                    // summaryItemComparator
        );

        constantsSection.items.addAll(constantItems);

        super.rDetail(
            "Option Provider " + optionProvider.name(),                 // windowTitle
            options,                                                    // options
            new String[] { "../stylesheet.css", "../stylesheet2.css" }, // stylesheetLinks
            new String[] {                                              // nav1
                "Overview",        "../overview-summary.html",
                "Option Provider", AbstractRightFrameHtml.HIGHLIT,
                "Deprecated",      "../deprecated-list.html",
                "Index",           "../" + (options.splitIndex ? "index-files/index-1.html" : "index-all.html"),
                "Help",            "../help-doc.html",
            },
            new String[] {                                              // nav2
                previousOptionProvider == null ? "Prev Option Provider" : "<a href=\"\">Prev Option Provider</a>",
                nextOptionProvider     == null ? "Next Option Provider" : "<a href=\"\">Next Option Provider</a>",
            },
            new String[] {                                              // nav3
                "Frames",    "../index.html?option-providers/" + optionProvider.className() + ".html",
                "No Frames", "#top",
            },
            new String[] {                                              // nav4
                "All Rules", "../allrules-noframe.html",
            },
            null,                                                       // subtitle
            "Option Provider \"" + optionProvider.name() + "\"",        // heading
            "Option Provider \"" + optionProvider.name() + "\"",        // headingTitle
            () -> {                                                     // prolog
                OptionProviderDetailHtml.this.l(
"      <div class=\"description\">",
"        " + optionProvider.longDescription(),
"      </div>"
                );
            },
            Collections.singletonList(constantsSection)                 // sections
        );
    }
}
