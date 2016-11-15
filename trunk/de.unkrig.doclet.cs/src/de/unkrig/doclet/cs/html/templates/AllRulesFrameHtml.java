
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

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import com.sun.javadoc.RootDoc;

import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.lang.StringUtil;
import de.unkrig.commons.lang.protocol.Longjump;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.doclet.cs.CsDoclet.Quickfix;
import de.unkrig.doclet.cs.CsDoclet.Rule;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractBottomLeftFrameHtml;

/**
 * Renders the "all rules" frame (on the left 20% of the frame set).
 */
public
class AllRulesFrameHtml extends AbstractBottomLeftFrameHtml {

    /**
     * Renders the "all rules" frame (on the left 20% of the frame set).
     */
    public void
    render(
        final Collection<Rule>     rules,
        final Collection<Quickfix> quickfixes,
        final RootDoc              rootDoc,
        Options                    options,
        final Html                 html
    ) {

        super.rBottomLeftFrameHtml(
            "All rules",                       // windowTitle
            options,                           // options
            new String[] { "stylesheet.css" }, // stylesheetLinks
            "All rules",                       // heading
            "overview-summary.html",           // headingLink
            null,                              // renderIndexHeader
            () -> {                            // renderIndexContainer
                AllRulesFrameHtml.this.l(
"    <div class=\"indexContainer \">"
                );

                Map<String /*family*/, Collection<Rule>> rulesByFamily = new TreeMap<String, Collection<Rule>>();
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

                for (Entry<String, Collection<Rule>> e : rulesByFamily.entrySet()) {
                    String           familyPlural  = e.getKey();
                    Collection<Rule> rulesOfFamily = e.getValue();


                    AllRulesFrameHtml.this.l(
"      <h2>" + StringUtil.firstLetterToUpperCase(familyPlural) + "</h2>",
"      <ul>"
                    );

                    for (Rule rule : rulesOfFamily) {
                        try {
                            String link = html.makeLink(
                                rootDoc,     // from
                                rule.ref(),  // to
                                true,        // plain
                                rule.name(), // label
                                "ruleFrame", // target
                                rootDoc      // rootDoc
                            );
                            AllRulesFrameHtml.this.l(
"        <li>" + link + "</li>"
                            );
                        } catch (Longjump l) {
                            ;
                        }
                    }
                    this.l(
"      </ul>"
                    );
                }

                AllRulesFrameHtml.this.l(
"      <h2>Quickfixes</h2>",
"      <ul>"
                );

                for (Quickfix quickfix : quickfixes) {
                    try {
                        String link = html.makeLink(
                            rootDoc,          // from
                            quickfix.ref(),   // to
                            true,             // plain
                            quickfix.label(), // label
                            "ruleFrame",      // target
                            rootDoc           // rootDoc
                        );
                        AllRulesFrameHtml.this.l(
"        <li>" + link + "</li>"
                        );
                    } catch (Longjump l) {
                        ;
                    }
                }
                this.l(
"      </ul>"
                );

                AllRulesFrameHtml.this.l(
"    </div>"
                );
            }
        );
    }
}
