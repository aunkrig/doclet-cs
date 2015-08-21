
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
import de.unkrig.commons.lang.protocol.Longjump;
import de.unkrig.doclet.cs.CsDoclet.Rule;
import de.unkrig.doclet.cs.CsDoclet.Quickfix;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractBottomLeftFrameHtml;

public class AllRulesFrameHtml extends AbstractBottomLeftFrameHtml {

    public void
    render(
        final Collection<Rule>         rules,
        final Collection<Quickfix> quickfixes,
        final RootDoc                  rootDoc,
        Options                        options,
        final Html                     html
    ) {

        super.rBottomLeftFrameHtml(
            "All types",                       // heading
            "overview-summary.html",           // headingLink
            options,                           // options
            new String[] { "stylesheet.css" }, // stylesheetLinks
            () -> {                            // renderBody
                AllRulesFrameHtml.this.l(
"    <dl>"
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


                    AllRulesFrameHtml.this.l(
"      <dt>" + family + "</dt>"
                    );

                    for (Rule rule : rulesOfFamily) {
                        try {
                            String link = html.makeLink(
                               rootDoc,      // from
                               rule.ref(),   // to
                               false,        // plain
                               null,         // label
                               "ruleFrame",  // target
                               rootDoc       // rootDoc
                            );
                            AllRulesFrameHtml.this.l(
"      <dd><code>" + link + "</code></dd>"
                            );
                        } catch (Longjump l) {
                            ;
                        }
                    }
                }

                AllRulesFrameHtml.this.l(
                    "      <dt>Quickfixes</dt>"
                );

                for (Quickfix quickfix : quickfixes) {
                    try {
                        String link = html.makeLink(
                            rootDoc,        // from
                            quickfix.ref(), // to
                            false,          // plain
                            null,           // label
                            "ruleFrame",    // target
                            rootDoc         // rootDoc
                        );
                        AllRulesFrameHtml.this.l(
"      <dd><code>" + link + "</code></dd>"
                        );
                    } catch (Longjump l) {
                        ;
                    }
                }

                AllRulesFrameHtml.this.l(
"    </dl>"
                );
            }
        );
    }
}
