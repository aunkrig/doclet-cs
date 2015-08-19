
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

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import com.sun.javadoc.RootDoc;

import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.Longjump;
import de.unkrig.doclet.cs.CsDoclet.Rule;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractRightFrameHtml;

public
class OverviewSummaryHtml extends AbstractRightFrameHtml {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    public void
    render(Collection<Rule> rules, final RootDoc rootDoc, final Options options, final Html html) {

        super.rRightFrameHtml(
            "Overview",                                                  // windowTitle
            options,                                                     // options
            new String[] { "stylesheet.css" },                           // stylesheetLinks
            new String[] { "nav1", AbstractRightFrameHtml.DISABLED },    // nav1
            new String[] { "nav2" },                                     // nav2
            new String[] { "nav3", AbstractRightFrameHtml.DISABLED },    // nav3
            new String[] { "nav4", AbstractRightFrameHtml.DISABLED },    // nav4
            new String[] { "nav5", AbstractRightFrameHtml.DISABLED },    // nav5
            new String[] { "nav6", AbstractRightFrameHtml.DISABLED },    // nav6
            () -> {                                                      // renderBody

                OverviewSummaryHtml.this.l(
"    <div class=\"contentContainer\">"
                );

                OverviewSummaryHtml.this.l(
"      <h1>ANT Library Overview</h1>"
                );

                Map<String /*family*/, Collection<Rule>> rulesByFamily = new TreeMap<String, Collection<Rule>>();
                for (Rule rule : rules) {

                    Collection<Rule> rulesOfFamily = rulesByFamily.get(rule.family());
                    if (rulesOfFamily == null) {
                        rulesOfFamily = new TreeSet<Rule>(new Comparator<Rule>() {
                            @Override public int compare(Rule r1, Rule r2) { return r1.name().compareTo(r2.name()); }
                        });
                        rulesByFamily.put(rule.family(), rulesOfFamily);
                    }

                    rulesOfFamily.add(rule);
                }
                for (Entry<String, Collection<Rule>> e : rulesByFamily.entrySet()) {
                    String           family        = e.getKey();
                    Collection<Rule> rulesOfFamily = e.getValue();

                    OverviewSummaryHtml.this.l(
"    <h2>" + family + " summary</h2>",
"    <dl>"
                    );

                    for (Rule rule : rulesOfFamily) {
                        try {
                            String link = html.makeLink(
                                rootDoc,
                                rule.ref(),
                                false, // plain
                                null,  // label
                                null,  // target
                                rootDoc
                            );
                            String htmlText = html.fromTags(
                                rule.ref().firstSentenceTags(), // tags
                                rule.ref(),                     // ref
                                rootDoc                         // rootDoc
                            );
                            OverviewSummaryHtml.this.l(
"      <dt><code>" + link + "</code></dt>",
"      <dd>" + htmlText + "</dd>"
                            );
                        } catch (Longjump l) {}
                    }
                    OverviewSummaryHtml.this.l(
"    </dl>"
                    );
                }
                OverviewSummaryHtml.this.l(
"    </div>"
                );
            }
        );
    }
}
