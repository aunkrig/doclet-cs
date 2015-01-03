
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javadoc.*;

import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.doclet.cs.MediawikiGenerator.Longjump;

/**
 * Utility methods for custom doclets.
 */
public final
class DocletUtil {

    private DocletUtil() {}

    private static final Pattern
    UNICODE_LINE_BREAK = Pattern.compile("\\r\\a|[\\x0A\\x0B\\x0C\\x0D\\x85\\u2028\\u2029]");

    private static final Pattern
    ONE_LEADING_BLANK = Pattern.compile("^ ", Pattern.MULTILINE);

    private static final Pattern
    DOC_TAG = Pattern.compile("\\{@([^\\s}]+)(?:\\s+([^\\s}][^}]*))?\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** The value of the 'line.separator' system property. */
    public static final String
    LINE_SEPARATOR = System.getProperty("line.separator");

    /**
     * @return The comment text of the given {@code doc}, trimmed and with the correct line separators
     */
    @Nullable public static String
    commentText(Doc doc) {

        String ct = doc.commentText().trim();
        if (ct.length() == 0) return null;

        // 'commentText()' returns the text with UNIX line breaks - convert them into the (system specific) 'line
        // separator'.
        ct = DocletUtil.UNICODE_LINE_BREAK.matcher(ct).replaceAll(DocletUtil.LINE_SEPARATOR);

        // It seems like 'commentText()' only strips '^ \*' (precisely: '^ *\*+') from all continuation
        // lines. In practice, however, people write doc comments such that the prefix is ' * '.
        // In other words: 'commentText()' sees the world like this:
        //     /**
        //      *        FIRST
        //      *SECOND
        //      *THIRD
        //      */
        // , while people write
        //     /**
        //      *        FIRST
        // -->  * SECOND
        // -->  * THIRD
        //      */
        // .
        ct = DocletUtil.ONE_LEADING_BLANK.matcher(ct).replaceAll("");

        return ct;
    }

    /**
     * Converts JAVADOC markup into MediaWiki markup.
     *
     * @param ref     The 'current element'; relevant to resolve relative references
     * @param rootDoc Used to resolve absolute references and to print errors and warnings
     */
    public static String
    javadocTextToHtml(String s, Doc ref, RootDoc rootDoc) throws Longjump {

        // Expand inline tags. Inline tags, as of Java 8, are:
        //   {@code text}
        //   {@docRoot}
        //   {@inheritDoc}
        //   {@link package.class#member label}
        //   {@linkplain package.class#member label}
        //   {@literal text}
        //   {@value package.class#field}
        // Only part of these are currently acceptable for the transformation into HTML.
        INLINE_TAGS: {
            Matcher m = DocletUtil.DOC_TAG.matcher(s);

            if (!m.find()) break INLINE_TAGS; // Short-circuit iff no inline tag found.

            StringBuffer sb = new StringBuffer();
            do {
                String tagName  = m.group(1).intern();
                String argument = m.group(2);

                String replacement;
                if ("code" == tagName) { // SUPPRESS CHECKSTYLE StringLiteralEquality
                    replacement = "<code>" + argument  + "</code>";
                } else
                if ("value" == tagName) { // SUPPRESS CHECKSTYLE StringLiteralEquality
                    Doc doc = DocletUtil.findDoc(argument, rootDoc, ref);
                    if (!(doc instanceof FieldDoc)) {
                        rootDoc.printError(doc.position(), "'" + argument + "' does not designate a field");
                        replacement = argument;
                    } else {
                        Object cv = ((FieldDoc) doc).constantValue();
                        if (cv == null) {
                            rootDoc.printError(
                                doc.position(),
                                "Field '" + argument + "' does not have a constant value"
                            );
                            replacement = argument;
                        } else {
                            replacement = cv.toString();
                        }
                    }
                } else
                {
                    rootDoc.printError(ref.position(), (
                        "Inline tag '{@"
                        + tagName
                        + "}' is not supported; you could "
                        + "(A) remove it from the text, or "
                        + "(B) improve 'DocetUtil.javadocTextToHtml()' to transform it into nice HTML (if that is "
                        + "reasonably possible)"
                    ));
                    replacement = m.group();
                }

                m.appendReplacement(sb, replacement);
            } while (m.find());
            return m.appendTail(sb).toString();
        }

        return s;
    }

    /**
     * Verifies that the named block tag exists at most <b>once</b>, and replaces line breaks with spaces.
     *
     * @return          {@code null} if the tag does not exist
     * @throws Longjump A probem has occurred and been reported through the given {@code errorReporter}
     */
    @Nullable public static String
    optionalTag(Doc doc, String tagName, RootDoc rootDoc) throws Longjump {

        Tag[] tags = doc.tags(tagName);
        if (tags.length == 0) return null;
        if (tags.length > 1) {
            rootDoc.printError(doc.position(), "'" + tagName + "' must appear at most once");
            throw new Longjump();
        }

        String s = tags[0].text();

        // Replace all line breaks with spaces.
        s = Pattern.compile("\\s*\n\\s*").matcher(s).replaceAll(" ");

        // Expand inine tags.
        s = DocletUtil.javadocTextToHtml(s, doc, rootDoc);

        return s;
    }

    /**
     * Verifies that the named tag exists at most <b>once</b>, and returns it value, converted to {@link Boolean}
     *
     * @return          {@code null} if the tag does not exist
     * @throws Longjump A probem has occurred and been reported through the given {@code errorReporter}
     */
    @Nullable public static Boolean
    optionalBooleanTag(Doc doc, String tagName, DocErrorReporter errorReporter) throws Longjump {
        Tag[] tags = doc.tags(tagName);
        if (tags.length == 0) return null;
        if (tags.length > 1) {
            errorReporter.printError(doc.position(), "'" + tagName + "' must appear at most once");
            throw new Longjump();
        }
        return Boolean.valueOf(tags[0].text());
    }

    /**
     * @return The {@link Doc} specified by {@code s}, relative to {@code ref}
     */
    public static Doc
    findDoc(String s, RootDoc rootDoc, Doc ref) throws Longjump {

        String where, what;
        {
            int hashPos = s.indexOf('#');
            if (hashPos == -1) {
                where = s;
                what  = null;
            } else
            if (hashPos == 0) {
                where = null;
                what  = s.substring(1);
            } else
            {
                where = s.substring(0, hashPos);
                what  = s.substring(hashPos + 1);
            }
        }

        ClassDoc classScope;
        if (ref instanceof MemberDoc) {
            classScope = ((MemberDoc) ref).containingClass();
        } else
        if (ref instanceof ClassDoc) {
            classScope = (ClassDoc) ref;
        } else
        {
            classScope = null;
        }

        ClassDoc referencedClass = null;

        // Current class?
        if (where == null) {
            if (classScope == null) {
                rootDoc.printError(ref.position(), "No type declaration in scope");
                throw new Longjump();
            }
            referencedClass = classScope;
        }

        // Member type?
        if (referencedClass == null && classScope != null) {
            referencedClass = classScope.findClass(where);
        }

        // Fully qualified type name?
        if (referencedClass == null) {
            referencedClass = rootDoc.classNamed(where);
        }

        // Type in same package?
        if (referencedClass == null && classScope != null) {
            referencedClass = rootDoc.classNamed(classScope.containingPackage().name() + "." + where);
        }

        // Package?
        if (referencedClass == null) {
            PackageDoc referencedPackage = rootDoc.packageNamed(where);
            if (referencedPackage != null) {
                if (what != null) {
                    rootDoc.printError(ref.position(), "Cannot use '#' on package");
                }
            }
        }

        if (referencedClass == null) {
            rootDoc.printError(ref.position(), "Class '" + where + "' not found");
            throw new Longjump();
        }

        where = referencedClass.qualifiedName();

        if (what == null) return referencedClass;

        for (MethodDoc md : referencedClass.methods(false)) {
            if (what.equals(md.toString())) return md;
        }
        for (ConstructorDoc cd : referencedClass.constructors(false)) {
            if (what.equals(cd.toString())) return cd;
        }
        for (FieldDoc fd : referencedClass.fields(false)) {
            if (what.equals(fd.name())) return fd;
        }

        rootDoc.printError(ref.position(), "Cannot find '" + what + "' in '" + where + "'");
        throw new Longjump();
    }
}
