
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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.RootDoc;

import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.StringUtil;
import de.unkrig.commons.lang.protocol.Longjump;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.doclet.cs.CsDoclet.Quickfix;
import de.unkrig.doclet.cs.CsDoclet.Rule;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractRightFrameHtml;
import de.unkrig.notemplate.javadocish.templates.AbstractSummaryHtml;

/**
 * Renders the "overview summary" page.
 */
public
class OverviewSummaryHtml extends AbstractSummaryHtml {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    /**
     * Renders the "overview summary" page.
     */
    public void
    render(
        Collection<Rule>     rules,
        Collection<Quickfix> quickfixes,
        final RootDoc        rootDoc,
        final Options        options,
        final Html           html
    ) {

        final String overviewFirstSentenceHtml = AssertionUtil.notNull(Longjump.catchLongjump(
            () -> html.fromTags(rootDoc.firstSentenceTags(), rootDoc, rootDoc),
            ""
        ));

        final String overviewHtml = AssertionUtil.notNull(Longjump.catchLongjump(
            () -> html.fromTags(rootDoc.inlineTags(), rootDoc, rootDoc),
            ""
        ));

        Map<String /*familyPlural*/, Collection<Rule>> rulesByFamily = new TreeMap<String, Collection<Rule>>();
        for (Rule rule : rules) {

            Collection<Rule> rulesOfFamily = rulesByFamily.get(rule.familyPlural());
            if (rulesOfFamily == null) {
                rulesOfFamily = new TreeSet<Rule>(new Comparator<Rule>() {

                    @Override public int
                    compare(@Nullable Rule r1, @Nullable Rule r2) {
                        assert r1 != null;
                        assert r2 != null;
                        return r1.name().compareTo(r2.name());
                    }
                });
                rulesByFamily.put(rule.familyPlural(), rulesOfFamily);
            }

            rulesOfFamily.add(rule);
        }

        List<Section> sections = new ArrayList<Section>();
        for (Entry<String, Collection<Rule>> e : rulesByFamily.entrySet()) {
            String                 familyPlural  = e.getKey();
            final Collection<Rule> rulesOfFamily = e.getValue();

            Section section = new Section(
                familyPlural,                                    // anchor
                StringUtil.firstLetterToUpperCase(familyPlural), // title
                familyPlural,                                    // summary
                "Name"                                           //  firstColumnHeading
            );

            for (Rule rule : rulesOfFamily) {
                section.items.add(new SectionItem(
                    familyPlural + '/' + ((ClassDoc) rule.ref()).simpleTypeName() + ".html", // link
                    rule.name(),                                                             // name
                    rule.shortDescription()                                                  // summaryy
                ));
            }

            sections.add(section);
        }

        // Add the "Quickfixes" section.
        {
            Section section = new Section(
                "quickfixes",                 // anchor
                "Quickfixes",                 // title
                "Quickfixes for the checks.", // summary
                "Name"                        // firstColumnHeading
            );

            for (Quickfix quickfix : quickfixes) {
                section.items.add(new SectionItem(
                    "quickfixes/" + quickfix.label() + ".html", // link
                    quickfix.label(),                           // name
                    quickfix.shortDescription()                 // summary
                ));
            }

            sections.add(section);
        }

        this.rSummary(
            "Overview",                                              // windowTitle
            options,
            new String[] { "stylesheet.css", "../stylesheet2.css" }, // stylesheetLinks
            new String[] {                                           // nav1
                "Overview",   AbstractRightFrameHtml.HIGHLIT,
                "Check",      AbstractRightFrameHtml.DISABLED,
                "Deprecated", "deprecated-list.html",
                "Index",      options.splitIndex ? "index-files/index-1.html" : "index-all.html",
                "Help",       "help-doc.html",
            },
            new String[] {                                           // nav2
                "Prev",
                "Next",
            },
            new String[] {                                           // nav3
                "Frames",    "index.html?overview-summary.html",
                "No Frames", "overview-summary.html",
            },
            new String[] {                                           // nav4
                "All Rules", "allclasses-noframe.html",
            },
            new Runnable[] {                                         // renderHeaders
                options.docTitle == null ? null : () -> {
                    this.l(
"      <h1 class=\"title\">" + options.docTitle + "</h1>"
                    );
                },
                overviewFirstSentenceHtml.isEmpty() ? null : () -> {
                    this.l(
"      <div class=\"docSummary\">",
"        <div class=\"subTitle\">",
"          <div class=\"block\">" + overviewFirstSentenceHtml + "</div>",
"        </div>"
                    );
                    if (!overviewHtml.isEmpty()) {
                        this.l(
"        <p>See: <a href=\"#description\">Description</a></p>"
                        );
                    }
                    this.l(
"      </div>"
                    );
                },
            },
            () -> {                                                  // epilog
                this.l(
"      <a name=\"description\" />"
                );
                if (!overviewHtml.isEmpty()) {
                    this.l(
"      " + overviewHtml
                    );
                }
            },
            sections                                                 // sections
        );
    }
}
