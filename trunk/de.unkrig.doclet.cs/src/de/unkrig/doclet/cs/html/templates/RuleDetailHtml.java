
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.javadoc.RootDoc;

import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.Consumer;
import de.unkrig.commons.util.collections.IterableUtil.ElementWithContext;
import de.unkrig.doclet.cs.CsDoclet.Rule;
import de.unkrig.doclet.cs.CsDoclet.RuleProperty;
import de.unkrig.notemplate.HtmlTemplate;
import de.unkrig.notemplate.javadocish.IndexPages;
import de.unkrig.notemplate.javadocish.IndexPages.IndexEntry;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractDetailHtml;
import de.unkrig.notemplate.javadocish.templates.AbstractRightFrameHtml;

/**
 * Renderer for the "per-rule" documentation document.
 */
public
class RuleDetailHtml extends AbstractDetailHtml {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    private static final MessageFormat TITLE_MF = new MessageFormat("Task \"&lt;{0}&gt;\"");

    public void
    render(
        final ElementWithContext<Rule> ruleTriplet,
        final Html                     html,
        final RootDoc                  rootDoc,
        Options                        options,
        String                         indexLink,
        Consumer<IndexEntry>           indexEntries
    ) {

        final Rule previousRule = ruleTriplet.previous();
        final Rule rule         = ruleTriplet.current();
        final Rule nextRule     = ruleTriplet.next();

        // Index entry for rule.
        {
            String ruleLink = rule.family() + "/" + rule.ref().name().replace('.', '/');
            indexEntries.consume(IndexPages.indexEntry(
                rule.name(),            // key
                ruleLink,               // link
                "Rule",                 // explanation
                rule.shortDescription() // shortDescription
            ));
        }

        List<SectionItem> propertyItems = new ArrayList<SectionItem>();
        for (RuleProperty property : rule.properties()) {

            SectionItem propertyItem = new SectionItem();

            propertyItem.anchor           = property.name();
            propertyItem.name             = property.name();
            propertyItem.shortDescription = property.shortDescription();
            propertyItem.content          = property.longDescription();

            propertyItems.add(propertyItem);

            // Index entry for rule property.
            {
                String ruleLink     = rule.family() + "/" + rule.ref().name().replace('.', '/');
                String propertyLink = ruleLink + "#property_" + property.name();
                indexEntries.consume(IndexPages.indexEntry(
                    property.name(),                                                         // key
                    propertyLink,                                                            // link
                    "Property of rule <a href=\"" + ruleLink + "\">" + rule.name() + "</a>", // explanation
                    property.shortDescription()                                              // shortDescription
                ));
            }
        }

        Section propertiesSection = new Section();
        propertiesSection.anchor                 = "property";
        propertiesSection.namePlural             = "Properties";
        propertiesSection.nameSingular           = "Property";
        propertiesSection.summary                = rule.shortDescription();
        propertiesSection.summaryLeftColumnTitle = "Property Name and Description";
        propertiesSection.items                  = propertyItems;

        super.rDetail(
            RuleDetailHtml.TITLE_MF.format(new String[] { rule.name() }), // windowTitle
            options,                                                      // options
            new String[] { "../stylesheet.css", "../stylesheet2.css" },   // stylesheetLinks
            new String[] {                                                // nav1
                "Overview",   "../overview-summary.html",
                "Rule",       AbstractRightFrameHtml.HIGHLIT,
                "Deprecated", "../deprecated-list.html",
                "Index",      "../" + indexLink,
                "Help",       "../help-doc.html",
            },
            new String[] {                                                // nav2
                previousRule == null ? "Prev Rule" : "<a href=\"\">Prev Rule</a>",
                nextRule     == null ? "Next Rule" : "<a href=\"\">Next Rule</a>",
            },
            new String[] {                                                // nav3
                "Frames",    "../index.html?" + rule.family() + "/" + rule.name().replace(':', '_') + ".html",
                "No Frames", "#top",
            },
            new String[] {                                                // nav4
                "All Rules", "../allrules-noframe.html",
            },
            HtmlTemplate.esc(rule.family()),                              // subtitle
            rule.family() + " " + rule.name(),                            // title
            () -> {                                                       // prolog
                RuleDetailHtml.this.l(
"  <div class=\"description\">"
                );
                this.l(rule.longDescription());
                RuleDetailHtml.this.l(
"  </div>",
"</div>"
                );
            },
            Collections.singletonList(propertiesSection)
        );
    }
}
