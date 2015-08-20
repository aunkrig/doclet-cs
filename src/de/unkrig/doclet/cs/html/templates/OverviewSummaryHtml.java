
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import com.sun.javadoc.RootDoc;

import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.doclet.cs.CsDoclet.Rule;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractRightFrameHtml;
import de.unkrig.notemplate.javadocish.templates.AbstractSummaryHtml;

public
class OverviewSummaryHtml extends AbstractSummaryHtml {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    public void
    render(Collection<Rule> rules, final RootDoc rootDoc, final Options options, final Html html) {


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

        List<Section> sections = new ArrayList<Section>();
        for (Entry<String, Collection<Rule>> e : rulesByFamily.entrySet()) {
            String           family        = e.getKey();
            Collection<Rule> rulesOfFamily = e.getValue();

            Section section = new Section();
            section.anchor             = family;
            section.title              = family;
            section.firstColumnHeading = family;
            section.items              = new ArrayList<SectionItem>();
            section.summary            = family;

            for (Rule rule : rulesOfFamily) {
                SectionItem item = new SectionItem();
                item.link    = family + '/' + rule.name().replace(':', '_') + ".html";
                item.name    = rule.name();
                item.summary = rule.shortDescription();

                section.items.add(item);
            }

            sections.add(section);
        }

        this.rSummary(
            "Overview", // windowTitle
            options,
            new String[] { "stylesheet.css" }, // stylesheetLinks
            new String[] { // nav1
                "Overview",   AbstractRightFrameHtml.HIGHLIT,
                "Rule",       AbstractRightFrameHtml.DISABLED,
                "Deprecated", "deprecated-list.html",
                "Index",      "index-all.html",
                "Help",       "help-doc.html",
            },
            new String[] { // nav2
                "Prev",
                "Next",
            },
            new String[] { // nav3
                "Frames",    "index.html?overview-summary.html",
                "No Frames", "overview-summary.html",
            },
            new String[] { // nav4
                "All Rules", "allclasses-noframe.html",
            },
            () -> {}, // prolog
            () -> {}, // epilog
            sections
        );
    }
}
