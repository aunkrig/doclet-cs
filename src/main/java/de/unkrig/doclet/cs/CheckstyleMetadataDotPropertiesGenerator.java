
/*
 * de.unkrig.doclet.cs - A doclet which generates metadata documents for a CheckStyle extension
 *
 * Copyright (c) 2014, Arno Unkrig
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

package de.unkrig.doclet.cs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import com.sun.javadoc.RootDoc;

import de.unkrig.doclet.cs.CsDoclet.Quickfix;
import de.unkrig.doclet.cs.CsDoclet.Rule;
import de.unkrig.doclet.cs.CsDoclet.RuleProperty;

/**
 * Produces the 'checkstyle-metadata.xml' and 'checkstyle-metadata.properties' files for ECLIPSE-CS.
 * @author Arno
 *
 */
public final
class CheckstyleMetadataDotPropertiesGenerator {

    private CheckstyleMetadataDotPropertiesGenerator() {}

    /**
     * Prints the 'checkstyle-metadata.properties' file.
     */
    public static void
    generate(
        final Collection<Rule> rules,
        final PrintWriter      pw,
        final RootDoc          rootDoc
    ) {

        pw.printf(
            ""
            + "%n"
            + "# This file was generated by the CheckStyle doclet; see http://cs-doclet.unkrig.de%n"
            + "%n"
            + "# Rule groups:%n"
        );

        SortedMap<String, String> groups = new TreeMap<String /*group*/, String /*groupName*/>();
        for (Rule rule : rules) {

            String ruleGroup = rule.group();
            if (!ruleGroup.startsWith("%")) continue; // Unlocalized group.

            String ruleGroupName = rule.groupName();

            ruleGroup = ruleGroup.substring(1);

            String originalRuleGroupName = groups.put(ruleGroup, ruleGroupName);
            if (originalRuleGroupName != null && !originalRuleGroupName.equals(ruleGroupName)) {
                rootDoc.printError(rule.ref().position(), (
                    "Non-equal redefinition of name of group '"
                    + ruleGroup
                    + "': Previously '"
                    + originalRuleGroupName
                    + "', now '"
                    + ruleGroupName
                    + "'"
                ));
            }
        }
        for (Entry<String, String> e : groups.entrySet()) {
            pw.printf("%-16s = %s%n", e.getKey(), e.getValue());
        }

        pw.printf(
            ""
            + "%n"
            + "# Custom checks, in alphabetical order.%n"
        );

        for (Rule rule : rules) {

            pw.printf((
                ""
                + "%n"
                + "# --------------- %2$s ---------------%n"
                + "%n"
                + "%1$s.name = %2$s%n"
                + "%1$s.desc =\\%n"
            ), rule.simpleName(), rule.name());

            String description = rule.longDescription();

            Quickfix[] qfs = rule.quickfixes();
            if (qfs != null && qfs.length > 0) {

                description += String.format("%n%n<h4>Quickfixes:</h4>%n<dl>%n");

                for (Quickfix qf : qfs) {
                    description += String.format((
                        ""
                        + "  <dt>%1$s%n"
                        + "  <dd>%2$s%n"
                    ), qf.label(), qf.shortDescription());
                }

                description += String.format("</dl>");
            }

            // TODO What was this supposed to do?
//            description = CheckstyleMetadataDotPropertiesGenerator.html.fromJavadocText(
//                description,
//                rule.ref(),
//                rootDoc
//            );

            CheckstyleMetadataDotPropertiesGenerator.printPropertyValue(description, pw);

            for (RuleProperty property : rule.properties()) {

                String shortDescription = CsDoclet.htmlToPlainText(
                    property.shortDescription(),
                    property.ref().position(),
                    rootDoc
                );
                shortDescription = shortDescription.replaceAll("\\s+", " ");

                pw.printf("%1$-40s = %2$s%n", rule.simpleName() + '.' + property.name(), shortDescription);
            }
        }
    }

    private static void
    printPropertyValue(String text, final PrintWriter pw) {

        boolean isFirst = true;
        for (BufferedReader br = new BufferedReader(new StringReader(text));;) {

            String line;
            try {
                line = br.readLine();
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            if (line == null) break;

            if (isFirst) {
                isFirst = false;
            } else {
                pw.println("\\n\\");
            }

            pw.print('\t');
            if (line.startsWith(" ")) pw.print('\\');
            pw.print(line);
        }

        pw.println();
    }
}
