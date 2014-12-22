
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;

import de.unkrig.commons.nullanalysis.Nullable;

public class CsDoclet {

    interface ConsumerWhichThrows<T, EX extends Exception> { void consume(T durable) throws EX; }

    public static int
    optionLength(String option) {

        if (option.equals("-todir")) return 2;

        return 0;
    }

    public static boolean
    start(final RootDoc rootDoc) throws IOException {

        File todir = null;
        for (String[] option : rootDoc.options()) {
            if ("-todir".equals(option[0])) {
                todir = new File(option[1]);
            } else
            {

                // It is quite counterintuitive, but 'options()' returns ALL options, not only those which
                // qualified by 'optionLength()'.
                ;
            }
        }

        if (todir == null) {
            rootDoc.printError("'-todir' command line option missing");
            return false;
        }

        CsDoclet.generateFiles(rootDoc, todir);

        return true;
    }

    private static void
    generateFiles(final RootDoc rootDoc, final File todir) throws IOException {

        CsDoclet.printToFile(
            new File(todir, "checkstyle-metadata.xml"),
            Charset.forName("UTF-8"),
            new ConsumerWhichThrows<PrintWriter, IOException>() {

            @Override public void
            consume(final PrintWriter cmx) throws IOException{

                CsDoclet.checkstyleMetadataXml(rootDoc, cmx);

                CsDoclet.printToFile(
                    new File(todir, "checkstyle-metadata.properties"),
                    Charset.forName("ISO-8859-1"),
                    new ConsumerWhichThrows<PrintWriter, IOException>() {

                        @Override public void
                        consume(PrintWriter cmp) throws IOException {
                            CsDoclet.checkstyleMetadataProperties(rootDoc, cmp);
                        }
                    }
                );
            }
        });
    }

    private static void
    checkstyleMetadataXml(final RootDoc rootDoc, final PrintWriter pw) {
        pw.printf(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>%n" +
            "<!DOCTYPE checkstyle-metadata PUBLIC%n" +
            "\"-//eclipse-cs//DTD Check Metadata 1.1//EN\"%n" +
            "\"http://eclipse-cs.sourceforge.net/dtds/checkstyle-metadata_1_1.dtd\">%n" +
            "<checkstyle-metadata>%n"
        );
        for (ClassDoc classDoc : rootDoc.classes()) {
            try {

                String  ruleGroup   = CsDoclet.optionalTag(classDoc, "@cs-rule-group", rootDoc);
                String  ruleParent  = CsDoclet.optionalTag(classDoc, "@cs-rule-parent", rootDoc);
                Boolean hasSeverity = CsDoclet.optionalBooleanTag(classDoc, "@cs-rule-has-severity", rootDoc);

                if (ruleGroup == null && ruleParent == null) continue;

                pw.printf((
                    "%n" +
                    "    <!-- %1$s -->%n" +
                    "%n" +
                    "    <rule-group-metadata name=\"%3$s\" priority=\"999\">%n" +
                    "        <rule-metadata%n" +
                    "            internal-name=\"%2$s\"%n" +
                    "            parent=\"%4$s\"%n" +
                    (hasSeverity == null ? "" : "            hasSeverity=\"%5$s\"%n") +
                    "            name=\"%%%2$s.name\"%n" +
                    "        >%n" +
                    "            <alternative-name internal-name=\"%2$s\"/>%n" +
                    "            <description>%%%2$s.desc</description>%n"
                ), classDoc.simpleTypeName(), classDoc.qualifiedName(), ruleGroup, ruleParent, hasSeverity);

                boolean isFirstProperty = true;
                for (MethodDoc methodDoc : classDoc.methods()) {

                    String name                 = CsDoclet.optionalTag(methodDoc, "@cs-property-name", rootDoc);
                    String datatype             = CsDoclet.optionalTag(methodDoc, "@cs-property-datatype", rootDoc);
                    String defaultValue         = CsDoclet.optionalTag(methodDoc, "@cs-property-default-value", rootDoc);
                    String overrideDefaultValue = CsDoclet.optionalTag(methodDoc, "@cs-property-override-default-value", rootDoc);
                    String optionProvider       = CsDoclet.optionalTag(methodDoc, "@cs-property-option-provider", rootDoc);
                    Tag[]  valueOptions         = methodDoc.tags("@cs-property-value-option");

                    if (
                        name == null
                        && datatype == null
                        && defaultValue == null
                        && overrideDefaultValue == null
                    ) continue;

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

                    // Generate property description.
                    if (isFirstProperty) {
                        pw.printf("%n");         // Insert one blank line before the first property description.
                        isFirstProperty = false;
                    }
                    pw.printf(
                        (
                            "            <property-metadata name=\"%2$s\" datatype=\"%3$s\"%4$s%5$s>%n" +
                            "                <description>%%%1$s.%2$s</description>%n"
                        ),
                        classDoc.qualifiedName(),
                        name,
                        datatype,
                        CsDoclet.optionalAttribute("default-value",           defaultValue),
                        CsDoclet.optionalAttribute("override-default-value",  overrideDefaultValue)
                    );
                    if (optionProvider != null) {
                        pw.printf("                <enumeration option-provider=\"%s\"/>%n", optionProvider);
                    }
                    if (valueOptions.length > 0) {
                        pw.printf("                <enumeration>%n");
                        for (Tag valueOption : valueOptions) {
                            pw.printf(
                                "                    <property-value-option value=\"%s\"/>%n",
                                valueOption.text()
                            );
                        }
                        pw.printf("                </enumeration>%n");
                    }
                    pw.printf("            </property-metadata>%n");
                }

                Tag[] quickfixes = classDoc.tags("@cs-quickfix-classname");
                if (quickfixes.length > 0) {
                    pw.printf("%n");
                    for (Tag quickfix : quickfixes) {
                        pw.printf("            <quickfix classname=\"%s\"/>%n", quickfix.text());
                    }
                }

                Tag[] messageKeys = classDoc.tags("@cs-message-key");
                if (messageKeys.length > 0) {
                    pw.printf("%n");
                    for (Tag messageKey : messageKeys) {
                        pw.printf("            <message-key key=\"%s\"/>%n", messageKey.text());
                    }
                }
                pw.printf((
                    "        </rule-metadata>%n" +
                    "    </rule-group-metadata>%n"
                ));
            } catch (HandledException c) {
                ;
            }
        }
        pw.printf("</checkstyle-metadata>%n");
    }

    private static void
    checkstyleMetadataProperties(final RootDoc rootDoc, final PrintWriter pw) throws IOException {

        pw.printf("%n# Rule groups:%n");

        SortedMap<String, String> groups = new TreeMap<String /*group*/, String /*groupName*/>();
        for (ClassDoc classDoc : rootDoc.classes()) {

            try {
                String ruleGroup     = CsDoclet.optionalTag(classDoc, "@cs-rule-group", rootDoc);
                String ruleGroupName = CsDoclet.optionalTag(classDoc, "@cs-rule-group-name", rootDoc);

                if (ruleGroup == null && ruleGroupName == null) continue;

                if (ruleGroup == null) {
                    rootDoc.printError(classDoc.position(), "'@cs-rule-group' doc tag missing");
                    continue;
                }

                if (!ruleGroup.startsWith("%")) continue;

               ruleGroup = ruleGroup.substring(1);

                String standardGroupName = CsDoclet.STANDARD_GROUPS.get(ruleGroup);
                if (standardGroupName == null) {
                    if (ruleGroupName == null) {
                        rootDoc.printError(classDoc.position(), (
                            "'@cs-rule-group-name' missing (must be specified because '" +
                            ruleGroup +
                            "' is not one of the CheckStyle standard groups)"
                        ));
                        continue;
                    }
                } else {
                    if (ruleGroupName == null) {
                        ruleGroupName = standardGroupName;
                    } else
                    if (ruleGroupName.equals(standardGroupName)) {
                        ;
                    } else
                    {
                        rootDoc.printWarning(classDoc.position(), (
                            "Group name differs from CS's standard group name '" +
                            standardGroupName +
                            "' - you should fix your group name, because otherwise editors will display TWO groups"
                        ));
                    }
                }

                String existingGroupName = groups.get(ruleGroup);
                if (existingGroupName != null && !existingGroupName.equals(ruleGroupName)) {
                    rootDoc.printError(classDoc.position(), (
                        "You define two different names for rule group '" +
                        ruleGroup +
                        "': '" +
                        existingGroupName +
                        "' and now '" +
                        ruleGroupName +
                        "'; you cannot have two different names for the same group in one package"
                    ));
                    continue;
                }

                groups.put(ruleGroup, ruleGroupName);
            } catch (HandledException c) {
                ;
            }
        }
        for (Entry<String, String> e : groups.entrySet()) {
            pw.printf("%-16s = %s%n", e.getKey(), e.getValue());
        }

        pw.printf(
            "%n" +
            "# Custom checks, in alphabetical order.%n"
        );

        for (ClassDoc classDoc : rootDoc.classes()) {

            try {
                String ruleName = CsDoclet.optionalTag(classDoc, "@cs-rule-name", rootDoc);
                if (ruleName == null) continue;

                String ruleDescription = classDoc.commentText().trim();

                pw.printf((
                    "%n" +
                    "# --------------- %1$s ---------------%n" +
                    "%n" +
                    "%2$s.name = %1$s%n" +
                    "%2$s.desc =\\%n"
                ), ruleName, classDoc.qualifiedName());

                boolean isFirst = true;
                for (BufferedReader br = new BufferedReader(new StringReader(ruleDescription));;) {
                    String line = br.readLine();
                    if (line == null) break;

                    if (line.startsWith(" ")) {
                        line = line.substring(1);
                        assert line != null;
                    }

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

                for (MethodDoc methodDoc : classDoc.methods()) {

                    String name        = CsDoclet.optionalTag(methodDoc, "@cs-property-name", rootDoc);
                    String description = CsDoclet.optionalTag(methodDoc, "@cs-property-desc", rootDoc);

                    if (name == null && description == null) continue;

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
                        if (description.contains("<") && description.contains(">")) {
                            rootDoc.printWarning(methodDoc.position(), (
                                "The coment text appears to contain HTML markup. " +
                                "ECLIPSE-CS cannot handle HTML markup in property descriptions; " +
                                "it is therefore recommended to add a '@cs-property-desc' tag without markup"
                            ));
                        }
                    } else {
                        if (description.contains("<") && description.contains(">")) {
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

                    // Generate property description.
                    pw.printf(
                        "%1$-68s = %2$s%n",
                        classDoc.qualifiedName() + '.' + name,
                        description
                    );
                }
            } catch (HandledException c) {
                ;
            }
        }
    }

    private static String
    optionalAttribute(String attributeName, @Nullable String value) {
        return value == null ? "" : " " + attributeName + "=\"" + value + "\"";
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

    @Nullable private static Boolean
    optionalBooleanTag(Doc doc, String tagName, DocErrorReporter errorReporter) throws HandledException {
        Tag[] tags = doc.tags(tagName);
        if (tags.length == 0) return null;
        if (tags.length > 1) {
            errorReporter.printError(doc.position(), "'" + tagName + "' must appear at most once");
            throw new HandledException();
        }
        return Boolean.valueOf(tags[0].text());
    }

    private static void
    printToFile(
        File                                          file,
        Charset                                       charset,
        ConsumerWhichThrows<PrintWriter, IOException> consumer
    ) throws IOException {

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), charset));
        try {
            consumer.consume(pw);
            pw.close();
        } catch (RuntimeException re) {
            try { pw.close(); } catch (Exception e) {}
            throw re;
        }
    }

    /** Maps group id to (english) group name, as defined by CheckStyle. */
    private static final Map<String, String> STANDARD_GROUPS;
    static {
        Map<String, String> m = new HashMap<String, String>();
        m.put("Annotation.group",    "Annotations");
        m.put("Javadoc.group",       "Javadoc Comments");
        m.put("Naming.group",        "Naming Conventions");
        m.put("Headers.group",       "Headers");
        m.put("Imports.group",       "Imports");
        m.put("Sizes.group",         "Size Violations");
        m.put("Whitespace.group",    "Whitespace");
        m.put("Regexp.group",        "Regexp");
        m.put("Modifiers.group",     "Modifiers");
        m.put("Blocks.group",        "Blocks");
        m.put("Coding.group",        "Coding Problems");
        m.put("Design.group",        "Class Design");
        m.put("Duplicates.group",    "Duplicates");
        m.put("Metrics.group",       "Metrics");
        m.put("Miscellaneous.group", "Miscellaneous");
        m.put("Other.group",         "Other");
        m.put("Filters.group",       "Filters");
        STANDARD_GROUPS = Collections.unmodifiableMap(m);
    }

    public static
    class HandledException extends Exception {
        private static final long  serialVersionUID = 1L;
        @Override public Throwable fillInStackTrace() { return this; }
    }
}
