
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

import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.SourcePosition;
import com.sun.javadoc.Tag;

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
     * @param sourcePosition Used to report errors
     * @param errorReporter  Used to report errors
     */
    public static String
    javadocTextToHtml(String s, SourcePosition sourcePosition, DocErrorReporter errorReporter) {

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
                {
                    errorReporter.printError(sourcePosition, (
                        "Inline tag '{@"
                        + tagName
                        + "}' is not supported; you could (A) remove it from the text, or (B) improve '"
                        + Doclet.class.getName()
                        + "' to transform it into nice HTML (if that is reasonably possible)"
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
     * Verifies that the named tag exists at most <b>once</b>, and replaces line breaks with spaces.
     *
     * @return          {@code null} if the tag does not exist
     * @throws Longjump A probem has occurred and been reported through the given {@code errorReporter}
     */
    @Nullable public static String
    optionalTag(Doc doc, String tagName, DocErrorReporter errorReporter) throws Longjump {
        Tag[] tags = doc.tags(tagName);
        if (tags.length == 0) return null;
        if (tags.length > 1) {
            errorReporter.printError(doc.position(), "'" + tagName + "' must appear at most once");
            throw new Longjump();
        }
        return Pattern.compile("\\s*\n\\s*").matcher(tags[0].text()).replaceAll(" ");
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
}
