
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

import com.sun.javadoc.RootDoc;

public class CsDoclet {

    interface ConsumerWhichThrows<T, EX extends Exception> { void consume(T durable) throws EX; }

    public static int
    optionLength(String option) {

        if (option.equals("-checkstyle-metadata-dir")) return 2;
        if (option.equals("-mediawiki-dir")) return 2;

        return 0;
    }

    public static boolean
    start(final RootDoc rootDoc) throws IOException {

        File checkstyleMetadataDir = null;
        File mediawikiDir = null;

        for (String[] option : rootDoc.options()) {
            if ("-checkstyle-metadata-dir".equals(option[0])) {
                checkstyleMetadataDir = new File(option[1]);
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

        if (checkstyleMetadataDir == null && mediawikiDir == null) {
            rootDoc.printWarning(
                "Neither '-checkstyle-metadata-dir' nor '-mediawiki-dir' specified - nothing to be done."
            );
        }

        if (checkstyleMetadataDir != null) {
            CsDoclet.generateCheckstyleMetadata(rootDoc, checkstyleMetadataDir);
        }

        if (mediawikiDir != null) {
            Mediawiki.generate(rootDoc, mediawikiDir);
        }

        return true;
    }

    private static void
    generateCheckstyleMetadata(final RootDoc rootDoc, final File todir) throws IOException {

        CsDoclet.printToFile(
            new File(todir, "checkstyle-metadata.xml"),
            Charset.forName("UTF-8"),
            new ConsumerWhichThrows<PrintWriter, IOException>() {

            @Override public void
            consume(final PrintWriter cmx) {
                CheckstyleMetadata.checkstyleMetadataXml(rootDoc, cmx);
            }
        });

        CsDoclet.printToFile(
            new File(todir, "checkstyle-metadata.properties"),
            Charset.forName("ISO-8859-1"),
            new ConsumerWhichThrows<PrintWriter, IOException>() {

                @Override public void
                consume(PrintWriter cmp) throws IOException {
                    CheckstyleMetadata.checkstyleMetadataProperties(rootDoc, cmp);
                }
            }
        );
    }

    public static <EX extends Exception> void
    printToFile(
        File                                 file,
        Charset                              charset,
        ConsumerWhichThrows<PrintWriter, EX> consumer
    ) throws IOException, EX {

        File newFile = new File(file.getParentFile(), "." + file.getName() + ".new");

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(newFile), charset));
        try {
            consumer.consume(pw);
            pw.close();
            if (file.exists() && !file.delete()) {
                throw new IOException("Could not delete existing file '" + file + "'");
            }
            if (!newFile.renameTo(file)) {
                throw new IOException("Could not rename '" + newFile + "' to '" + file + "'");
            }
        } catch (RuntimeException re) {
            try { pw.close(); } catch (Exception e) {}
            newFile.delete();

            throw re;
        } catch (Exception e) {
            try { pw.close(); } catch (Exception e2) {}
            newFile.delete();

            @SuppressWarnings("unchecked") EX tmp = (EX) e;
            throw tmp;
        }
    }

    public static boolean
    containsHtmlMarkup(String s) { return s.matches("<\\s*\\w.*>"); }
}
