
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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;

import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.doclet.cs.CsDoclet.ConsumerWhichThrows;

public class Mediawiki {

    private static final Pattern PRE2 = Pattern.compile("^");
    private static final Pattern PRE1 = Pattern.compile("<pre>(.*)</pre>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public static void
    generate(final RootDoc rootDoc, File todir) throws IOException {

        for (final ClassDoc classDoc : rootDoc.classes()) {

            try {

                final String  ruleGroup   = Mediawiki.optionalTag(classDoc, "@cs-rule-group", rootDoc);
                final String  ruleName    = Mediawiki.optionalTag(classDoc, "@cs-rule-name", rootDoc);
                final String  ruleParent  = Mediawiki.optionalTag(classDoc, "@cs-rule-parent", rootDoc);

                if (ruleGroup == null && ruleName == null && ruleParent == null) continue;

                CsDoclet.printToFile(
                    new File(todir, ruleName),
                    Charset.forName("ISO-8859-1"),
                        new ConsumerWhichThrows<PrintWriter, HandledException>() {

                        @Override
                        public void consume(PrintWriter pw) throws HandledException {

                            String ruleDesc = classDoc.commentText();

                            if (ruleName == null) {
                                rootDoc.printError(classDoc.position(), "Doc tag '@cs-rule-name' missing");
                                throw new HandledException();
                            }

                            ruleDesc = this.transformPre(ruleDesc);

                            pw.println(ruleDesc);
                            pw.println();

                            boolean first = true;
                            for (MethodDoc methodDoc : classDoc.methods()) {

                                String name                 = Mediawiki.optionalTag(methodDoc, "@cs-property-name", rootDoc);
                                String description          = Mediawiki.optionalTag(methodDoc, "@cs-property-desc", rootDoc);
                                String datatype             = Mediawiki.optionalTag(methodDoc, "@cs-property-datatype", rootDoc);
                                String defaultValue         = Mediawiki.optionalTag(methodDoc, "@cs-property-default-value", rootDoc);
                                String overrideDefaultValue = Mediawiki.optionalTag(methodDoc, "@cs-property-override-default-value", rootDoc);
                                String optionProvider       = Mediawiki.optionalTag(methodDoc, "@cs-property-option-provider", rootDoc);
                                Tag[]  valueOptions         = methodDoc.tags("@cs-property-value-option");

                                if (
                                    name == null
                                    && description == null
                                    && datatype == null
                                    && defaultValue == null
                                    && overrideDefaultValue == null
                                ) continue;

                                if (first) {
                                    first = false;
                                    pw.println("== Properties ==");
                                    pw.println();
                                    pw.println("Default values appear <u>underlined</u>.");
                                    pw.println();
                                }

                                // Get the 'property description'. This is a bit complicated because the methods 'comment text' and
                                // and the '@cs-property-desc' doc tag must both be taken into account.
                                // We also check whether there is HTML markup in the description, because ECLIPSE-CS does not
                                // support that.
                                if (description == null) {
                                    description = methodDoc.commentText();
                                    if (description == null) {
                                        rootDoc.printError(methodDoc.position(), (
                                            "Method has neither a comment text nor a '@cs-property-text' tag; " +
                                            "at least one of them must exist (and must not contain HTML markup)"
                                        ));
                                        continue;
                                    }
                                    if (CsDoclet.containsHtmlMarkup(description)) {
                                        rootDoc.printWarning(methodDoc.position(), (
                                            "The coment text appears to contain HTML markup. " +
                                            "ECLIPSE-CS cannot handle HTML markup in property descriptions; " +
                                            "it is therefore recommended to add a '@cs-property-desc' tag without markup"
                                        ));
                                    }
                                } else {
                                    if (CsDoclet.containsHtmlMarkup(description)) {
                                        rootDoc.printWarning(methodDoc.position(), (
                                            "The text after the '@cs-property-desc' tag appears to contain HTML markup; " +
                                            "ECLIPSE-CS cannot handle HTML markup in property descriptions"
                                        ));
                                    }
                                }

                                // Some consistency checks.
                                String methodName = methodDoc.name();
                                if (!methodName.startsWith("set")) {
                                    rootDoc.printError(methodDoc.position(), "Method is not a setter");
                                    continue;
                                }
                                if (!methodName.substring(3).equalsIgnoreCase(name)) {
                                    rootDoc.printError(methodDoc.position(), "Property name does not match method name");
                                    continue;
                                }
                                if (methodDoc.parameters().length != 1) {
                                    rootDoc.printError(methodDoc.position(), "Setter must have exactly one parameter");
                                    continue;
                                }
                                if (optionProvider != null && valueOptions.length > 0) {
                                    rootDoc.printError(
                                        methodDoc.position(),
                                        "@cs-property-option-provider and @cs-property-value-option are mutually exclusive"
                                    );
                                    continue;
                                }

                                String nav;

                                // For possible 'datatype's see
                                // http://code.google.com/p/lambdastyle/source/browse/etc/checkstyle-metadata_1_0.dtd
                                if (datatype != null) datatype = datatype.intern();
                                if (datatype == "Boolean") {
                                    nav = name + " = \"" + this.oredValues(new String[] { "true", "false" }, defaultValue) + "\"";
                                } else
                                if (datatype == "SingleSelect") {
                                    String[] values = this.valueOptions(rootDoc, methodDoc, optionProvider, valueOptions);
                                    nav = name + " = \"" + this.oredValues(values, defaultValue) + "\"";
                                } else
                                if (datatype == "MultiCheck") {
                                    String[] defaultValues = defaultValue == null ? new String[0] : defaultValue.split(",");
                                    String[] values = this.valueOptions(rootDoc, methodDoc, optionProvider, valueOptions);
                                    nav = name + " = \"" + this.oredValues(values, defaultValues) + "\"";
                                } else
                                {
                                    nav = name + " = \"<i>" + datatype + "</i>\"";
                                    if (defaultValue != null) {
                                        nav += " (optional; default value is " + defaultValue + ")";
                                    }
                                }
                                pw.printf((
                                    ";%1$-32s%n" +
                                    ":%2$s%n" +
                                    "%n"
                                ), nav, description);

//                                if (optionProvider != null) {
//                                    pw.printf("                <enumeration option-provider=\"%s\"/>%n", optionProvider);
//                                }
//                                if (valueOptions.length > 0) {
//                                    pw.printf("                <enumeration>%n");
//                                    for (Tag valueOption : valueOptions) {
//                                        pw.printf(
//                                            "                    <property-value-option value=\"%s\"/>%n",
//                                            valueOption.text()
//                                        );
//                                    }
//                                    pw.printf("                </enumeration>%n");
//                                }
//                                pw.printf("            </property-metadata>%n");
                            }

                            if (classDoc.tags("@cs-quickfix-classname").length > 0) {
                                pw.println();
                                pw.println("Quickfixes are available for this check.");
                            }
                        }

                        private String[]
                        valueOptions(
                            final DocErrorReporter errorReported,
                            MethodDoc              methodDoc,
                            @Nullable String       optionProvider,
                            Tag[]                  valueOptions
                        ) throws HandledException {

                            String[] values;
                            if (optionProvider != null) {
                                try {
                                    Class<?> c = CsDoclet.class.getClassLoader().loadClass(optionProvider);
                                    if (c.getSuperclass() == Enum.class) {
                                        Object[] tmp = (Object[]) c.getDeclaredMethod("values").invoke(null);
                                        values = new String[tmp.length];
                                        for (int i = 0; i < tmp.length; i++) {
                                            values[i] = tmp[i].toString();
                                        }
                                    } else {
                                        values = ((List<?>) c.getDeclaredMethod("getOptions").invoke(c.newInstance())).toArray(new String[0]);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    errorReported.printError(methodDoc.position(), e.toString());
                                    throw new HandledException();
                                }
                            } else {
                                values = new String[valueOptions.length];
                                for (int i = 0; i < valueOptions.length; i++) {
                                    values[i] = valueOptions[i].text();
                                }
                            }
                            return values;
                        }

                        private String
                        oredValues(String[] values, String defaultValue) {
                            return this.oredValues(values, new String[] { defaultValue });
                        }

                        private String
                        oredValues(String[] values, String[] defaultValues) {
                            assert values.length >= 1;

                            StringBuilder sb = new StringBuilder();
                            Set<String> dvs = new HashSet<String>();
                            dvs.addAll(Arrays.asList(defaultValues));
                            for (int i = 0;;) {
                                String value = values[i];
                                if (dvs.contains(value)) {
                                    sb.append("<u>" + value + "</u>");
                                } else {
                                    sb.append(value);
                                }
                                if (++i == values.length) break;
                                sb.append(" | ");
                            }
                            return sb.toString();
                        }

                        private String
                        transformPre(String s) {

                            Matcher m = Mediawiki.PRE1.matcher(s);
                            if (!m.find()) return s;

                            StringBuffer sb = new StringBuffer();
                            do {
                                m.appendReplacement(
                                    sb,
                                    Mediawiki.PRE2.matcher(m.group(1)).replaceAll(" ")
                                );
                            } while (m.find());
                            return m.appendTail(sb).toString();
                        }
                    }
                );
            } catch (HandledException c) {
                ;
            }
        }
    }

    @Nullable private static String
    optionalTag(Doc doc, String tagName, DocErrorReporter errorReporter) throws HandledException {
        Tag[] tags = doc.tags(tagName);
        if (tags.length == 0) return null;
        if (tags.length > 1) {
            errorReporter.printError(doc.position(), "'" + tagName + "' must appear at most once");
            throw new HandledException();
        }
        return tags[0].text();
    }

    public static
    class HandledException extends Exception {
        private static final long  serialVersionUID = 1L;
        @Override public Throwable fillInStackTrace() { return this; }
    }
}
