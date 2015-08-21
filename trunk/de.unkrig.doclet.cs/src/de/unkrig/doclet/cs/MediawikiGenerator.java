
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

package de.unkrig.doclet.cs;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.eclipsecs.core.config.meta.IOptionProvider;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SourcePosition;

import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.doclet.mediawiki.Mediawiki;
import de.unkrig.commons.lang.protocol.Longjump;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.doclet.cs.CsDoclet.Rule;
import de.unkrig.doclet.cs.CsDoclet.RuleProperty;

/**
 * Generates <a href="http://www.mediawiki.org/wiki/Help:Formatting">MediaWiki markup</a> from JAVADOC.
 */
public final
class MediawikiGenerator {

    private static Html html = new Html(Html.STANDARD_LINK_MAKER);

    private MediawikiGenerator() {}

    /**
     * Generates a MediaWiki markup document files in {@code todir} for the given {@code classDoc}.
     */
    public static void
    generate(final Rule rule, PrintWriter pw, final RootDoc rootDoc) throws Longjump {

        pw.println("<!-- This file was generated by the CS doclet; see http://cs-contrib.unkrig.de -->");

        String ruleDesc = MediawikiGenerator.html.fromJavadocText(rule.longDescription(), rule.ref(), rootDoc);

        ruleDesc = Mediawiki.fromHtml(ruleDesc);

        pw.println(ruleDesc);
        pw.println();

        MediawikiGenerator.printProperties(rule.properties(), pw, rule.ref(), rootDoc);

        ClassDoc[] quickfixClasses = rule.quickfixClasses();
        if (quickfixClasses != null && quickfixClasses.length > 0) {
            pw.printf(
                ""
                + "== Quickfixes ==%n"
                + "%n"
                + "<dl>%n"
            );
            for (ClassDoc quickfixClass : quickfixClasses) {

                final String quickfixLabel = MediawikiGenerator.html.optionalTag(
                    quickfixClass,
                    "@cs-label",
                    quickfixClass.qualifiedTypeName(), // defaulT
                    rootDoc
                );
                final String quickfixShortDescription = MediawikiGenerator.html.fromTags(
                    quickfixClass.firstSentenceTags(),
                    quickfixClass,
                    rootDoc
                );
                pw.printf((
                    ""
                    + "%n"
                    + "<dt>%1$s%n" // Mediawiki forbids leading space and closing tag
                    + "<dd>%2$s%n" // Mediawiki forbids leading space and closing tag
                ), quickfixLabel, quickfixShortDescription);
            }
            pw.printf("</dl>%n");
        }
    }

    private static void
    printProperties(
        Collection<RuleProperty> properties,
        final PrintWriter        pw,
        Doc                      ref,
        final RootDoc            rootDoc
    ) throws Longjump {

        boolean isFirstProperty = true;
        for (RuleProperty property : properties) {

            Object defaultValue;
            {
                Object tmp = property.defaultValue();
                if (tmp == null) tmp = property.overrideDefaultValue();
                defaultValue = tmp;
            }

            if (isFirstProperty) {
                isFirstProperty = false;
                pw.printf(
                    ""
                    + "== Properties ==%n"
                    + "%n"
                    + "Default values appear <u>underlined</u>.%n"
                    + "%n"
                );
            }

            String datatype = property.datatype().intern();

            String nav = property.name() + " = ";
            if (datatype == "Boolean") { // SUPPRESS CHECKSTYLE StringLiteralEquality
                nav += "\"" + MediawikiGenerator.catValues(
                    new String[] { "true", "false" },
                    defaultValue == null ? null : defaultValue.toString(),
                    " | "
                ) + "\"";
            } else
            if (datatype == "SingleSelect") { // SUPPRESS CHECKSTYLE StringLiteralEquality
                String[] values = MediawikiGenerator.valueOptions(
                    ref.position(),
                    property.optionProvider(),
                    property.valueOptions(),
                    rootDoc
                );
                nav += "\"" + MediawikiGenerator.catValues(values, defaultValue, " | ") + "\"";
            } else
            if (datatype == "MultiCheck") { // SUPPRESS CHECKSTYLE StringLiteralEquality
                String[] values = MediawikiGenerator.valueOptions(
                    ref.position(),
                    property.optionProvider(),
                    property.valueOptions(),
                    rootDoc
                );
                nav += "\"" + MediawikiGenerator.catValues(
                    values,
                    defaultValue == null ? new Object[0] : ((String) defaultValue).split(","), ", "
                ) + "\"";
            } else
            if (datatype == "Regex") { // SUPPRESS CHECKSTYLE StringLiteralEquality
                nav += (
                    "\"''[http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html#sum "
                    + datatype
                    + "]''\""
                );
                if (defaultValue != null) {
                    nav += " (optional; default value is \"" + defaultValue + "\")";
                }
            } else
            {
                nav += "\"<i>" + datatype + "</i>\"";
                if (defaultValue != null) {
                    nav += " (optional; default value is " + defaultValue + ")";
                } else {
                    nav += " (mandatory)";
                }
            }

            String intertitle = property.intertitle();
            if (intertitle != null) {
                try {
                    intertitle = MediawikiGenerator.html.fromJavadocText(intertitle, ref, rootDoc);
                    intertitle = Mediawiki.fromHtml(intertitle);
                    pw.printf("%1$s%n%n", intertitle);
                } catch (Longjump de) {
                    ; // SUPPRESS CHECKSTYLE AvoidHidingCause
                }
            }

            String longDescription = Mediawiki.fromHtml(property.longDescription());

            pw.printf((
                ""
                + "<dl>%n"
                + "<dt>%1$s%n" // Mediawiki forbids leading space and closing tag
                + "<dd>%2$s%n" // Mediawiki forbids leading space and closing tag
                + "</dl>%n"
                + "%n"
            ), nav, longDescription);
        }
    }

    /**
     * @return The value options for the given setter method
     */
    private static String[]
    valueOptions(
        SourcePosition         position,
        @Nullable Class<?>     optionProvider,
        @Nullable String[]     valueOptions,
        final DocErrorReporter docErrorReporter
    ) throws Longjump {

        String[] result;
        if (optionProvider == null) {
            if (valueOptions == null) {
                docErrorReporter.printError(position, "Both option provider and value options are missing");
                throw new Longjump();
            }
            result = valueOptions;
        } else
        if (valueOptions != null) {
            docErrorReporter.printError(position, "Option provider and value options are mutually exclusive");
            throw new Longjump();
        } else
        {

            if (optionProvider.getSuperclass() == Enum.class) {
                Object[] tmp;
                try {
                    tmp = (Object[]) optionProvider.getDeclaredMethod("values").invoke(null);
                } catch (Exception e) {
                    docErrorReporter.printError(position, e.getMessage()); // SUPPRESS CHECKSTYLE AvoidHidingCause
                    throw new Longjump();
                }
                result = new String[tmp.length];
                for (int i = 0; i < tmp.length; i++) {
                    result[i] = ((Enum<?>) tmp[i]).name().toLowerCase();
                }
            } else
            if (IOptionProvider.class.isAssignableFrom(optionProvider)) {
                List<?> tmp;
                try {
                    tmp = (List<?>) optionProvider.getDeclaredMethod("getOptions").invoke(optionProvider.newInstance());
                } catch (Exception e) {
                    docErrorReporter.printError(position, e.getMessage()); // SUPPRESS CHECKSTYLE AvoidHidingCause
                    throw new Longjump();
                }
                result = tmp.toArray(new String[0]);
            } else
            {
                docErrorReporter.printError(position, (
                    ""
                    + "Option provider class '"
                    + optionProvider
                    + "' must either extend 'Enum' or implement 'IOptionProvider'"
                ));
                throw new Longjump();
            }
        }
        return result;
    }

    /**
     * Concatenate the given {@code values}, separated with {@code glue}, and underline the value which equals the
     * {@code defaultValue}.
     */
    private static String
    catValues(String[] values, @Nullable Object defaultValue, String glue) {

        return MediawikiGenerator.catValues(
            values,
            defaultValue == null ? new Object[0] : new Object[] { defaultValue }, glue
        );
    }

    /**
     * Concatenate the given {@code values}, separated with {@code glue}, and underline the values which are also
     * contained in {@code defaultValue}.
     */
    private static String
    catValues(String[] values, Object[] defaultValues, String glue) {
        assert values.length >= 1;

        Set<Object>   dvs = new HashSet<Object>();
        for (Object o : defaultValues) dvs.add(o.toString());

        StringBuilder sb  = new StringBuilder();
        for (int i = 0;;) {
            String value = values[i];
            if (dvs.contains(value)) {
                sb.append("<u>" + value + "</u>");
            } else {
                sb.append(value);
            }
            if (++i == values.length) break;
            sb.append(glue);
        }
        return sb.toString();
    }
}
