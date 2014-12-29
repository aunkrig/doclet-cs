
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.SortedMap;
import java.util.TreeMap;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.RootDoc;

import de.unkrig.doclet.cs.MediawikiGenerator.Longjump;

/**
 * A doclet that creates ECLIPSE-CS metadata files and/or documentation for CheckStyle rules in MediaWiki markup
 * format.
 */
public final
class CsDoclet {

    private CsDoclet() {}

    /** An object that somehow 'consumes' another object, or objects. */
    interface ConsumerWhichThrows<T, EX extends Throwable> { void consume(T durable) throws EX; }

    /**
     * See <a href="https://docs.oracle.com/javase/6/docs/technotes/guides/javadoc/doclet/overview.html">"Doclet
     * Overview"</a>.
     */
    public static int
    optionLength(String option) {

        if ("-checkstyle-metadata.properties-dir".equals(option)) return 2;
        if ("-checkstyle-metadata.xml-dir".equals(option))        return 2;
        if ("-messages.properties-dir".equals(option))            return 2;
        if ("-mediawiki-dir".equals(option))                      return 2;

        return 0;
    }

    /**
     * See <a href="https://docs.oracle.com/javase/6/docs/technotes/guides/javadoc/doclet/overview.html">"Doclet
     * Overview"</a>.
     */
    public static boolean
    start(final RootDoc rootDoc) throws IOException {

        File checkstyleMetadataDotPropertiesDir = null;
        File checkstyleMetadataDotXmlDir        = null;
        File messagesDotPropertiesDir           = null;
        File mediawikiDir                       = null;

        for (String[] option : rootDoc.options()) {
            if ("-checkstyle-metadata.properties-dir".equals(option[0])) {
                checkstyleMetadataDotPropertiesDir = new File(option[1]);
            } else
            if ("-checkstyle-metadata.xml-dir".equals(option[0])) {
                checkstyleMetadataDotXmlDir = new File(option[1]);
            } else
            if ("-messages.properties-dir".equals(option[0])) {
                messagesDotPropertiesDir = new File(option[1]);
            } else
            if ("-mediawiki-dir".equals(option[0])) {
                mediawikiDir = new File(option[1]);
            } else
            {

                // It is quite counterintuitive, but 'options()' returns ALL options, not only those which
                // qualified by 'optionLength()'.
                ;
            }
        }

        if (
            checkstyleMetadataDotPropertiesDir == null
            && checkstyleMetadataDotXmlDir == null
            && messagesDotPropertiesDir == null
            && mediawikiDir == null
        ) {
            rootDoc.printWarning(
                "None of '-checkstyle-metadata.properties-dir', '-checkstyle-metadata.xml-dir', "
                + "'-messages.properties-dir' and '-mediawiki-dir' specified - nothing to be done."
            );
        }

        // Process all specified packages.
        for (PackageDoc pd : rootDoc.specifiedPackages()) {
            String checkstylePackage = pd.name();

            // Collect all classes in that package.
            final SortedMap<String, ClassDoc> classDocs = new TreeMap<String, ClassDoc>();
            for (ClassDoc classDoc : rootDoc.classes()) {
                String packageName = classDoc.containingPackage().name();

                if (packageName.equals(checkstylePackage)) {
                    classDocs.put(classDoc.name(), classDoc);
                }
            }

            // Generate 'checkstyle-metadata.properties' for the package.
            if (checkstyleMetadataDotPropertiesDir != null) {

                CsDoclet.printToFile(
                    new File(new File(
                        checkstyleMetadataDotPropertiesDir,
                        checkstylePackage.replace('.', File.separatorChar)
                    ), "checkstyle-metadata.properties"),
                    Charset.forName("ISO-8859-1"),
                    new ConsumerWhichThrows<PrintWriter, IOException>() {

                        @Override public void
                        consume(PrintWriter pw) throws IOException {
                            CheckstyleMetadataDotPropertiesGenerator.generate(classDocs.values(), pw, rootDoc);
                        }
                    }
                );
            }

            // Generate 'checkstyle-metadata.xml' for the package.
            if (checkstyleMetadataDotPropertiesDir != null) {

                CsDoclet.printToFile(
                    new File(new File(
                        checkstyleMetadataDotPropertiesDir,
                        checkstylePackage.replace('.', File.separatorChar)
                    ), "checkstyle-metadata.xml"),
                    Charset.forName("UTF-8"),
                    new ConsumerWhichThrows<PrintWriter, IOException>() {

                        @Override public void
                        consume(final PrintWriter pw) {
                            CheckstyleMetadataDotXmlGenerator.generate(classDocs.values(), pw, rootDoc);
                        }
                    }
                );
            }

            if (messagesDotPropertiesDir != null) {
                CsDoclet.printToFile(
                    new File(new File(
                        checkstyleMetadataDotPropertiesDir,
                        checkstylePackage.replace('.', File.separatorChar)
                    ), "messages.properties"),
                    Charset.forName("ISO-8859-1"),
                    new ConsumerWhichThrows<PrintWriter, IOException>() {

                        @Override public void
                        consume(PrintWriter pw) {
                            MessagesDotPropertiesGenerator.generate(classDocs.values(), pw, rootDoc);
                        }
                    }
                );
            }

            // Generate MediaWiki markup documents for each class in the package.
            if (mediawikiDir != null) {

                for (final ClassDoc classDoc : classDocs.values()) {
                    try {

                        final String ruleName = DocletUtil.optionalTag(classDoc, "@cs-rule-name", rootDoc);
                        if (ruleName == null) continue;

                        CsDoclet.printToFile(
                            new File(mediawikiDir, ruleName.replaceAll(":\\s+", " ") + ".mw"),
                            Charset.forName("ISO-8859-1"),
                            new ConsumerWhichThrows<PrintWriter, Longjump>() {

                                @Override public void
                                consume(PrintWriter pw) throws Longjump {
                                    MediawikiGenerator.generate(classDoc, pw, rootDoc);
                                }
                            }
                        );

                    } catch (Longjump e) {
                        ;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Creates the named {@code file}, lets the {@code printer} print text to it, and closes the file.
     * <p>
     *
     *
     * @param charset The charset to be used for printing
     * @throws EX     The throwable that the {@code printer} may throw
     */
    public static <EX extends Throwable> void
    printToFile(
        File                                 file,
        Charset                              charset,
        ConsumerWhichThrows<PrintWriter, EX> printer
    ) throws IOException, EX {

        File newFile = new File(file.getParentFile(), "." + file.getName() + ".new");

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(newFile), charset));
        try {
            printer.consume(pw);
            pw.close();
            if (file.exists() && !file.delete()) {
                throw new IOException("Could not delete existing file '" + file + "'");
            }
            if (!newFile.renameTo(file)) {
                throw new IOException("Could not rename '" + newFile + "' to '" + file + "'");
            }
        } catch (RuntimeException re) {
            try { pw.close(); } catch (Exception e2) {}
            newFile.delete();

            throw re;
        } catch (Error e) { // SUPPRESS CHECKSTYLE IllegalCatch
            try { pw.close(); } catch (Exception e2) {}
            newFile.delete();

            throw e;
        } catch (Throwable t) { // SUPPRESS CHECKSTYLE IllegalCatch
            try { pw.close(); } catch (Exception e2) {}
            newFile.delete();

            @SuppressWarnings("unchecked") EX tmp = (EX) t;
            throw tmp;
        }
    }

    /** @return Whether the given string appears to contain HTML markujp */
    public static boolean
    containsHtmlMarkup(String s) { return s.matches("<\\s*\\w.*>"); }
}
