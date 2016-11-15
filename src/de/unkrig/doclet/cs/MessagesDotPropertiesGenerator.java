
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

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import com.sun.javadoc.RootDoc;

import de.unkrig.doclet.cs.CsDoclet.Rule;

/**
 * Produces the 'checkstyle-metadata.xml' and 'checkstyle-metadata.properties' files for ECLIPSE-CS.
 * @author Arno
 *
 */
public final
class MessagesDotPropertiesGenerator {

    private MessagesDotPropertiesGenerator() {}

    /**
     * Prints the 'checkstyle-metadata.properties' file.
     */
    public static void
    generate(final Collection<Rule> rules, final PrintWriter mp, final RootDoc rootDoc) {

        mp.printf(
            ""
            + "%n"
            + "# This file was generated by the CS doclet; see http://cs-contrib.unkrig.de%n"
            + "%n"
            + "# Custom check messages, in alphabetical order.%n"
        );

        SortedMap<String, String> allMessages = new TreeMap<String, String>();
        for (Rule rule : rules) {
            for (Entry<String, String> e : rule.messages().entrySet()) {
                String messageKey = e.getKey();
                String message    = e.getValue();

                String orig = allMessages.put(messageKey, message);
                if (orig != null && !message.equals(orig)) {
                    rootDoc.printError((
                        "Rule \""
                        + rule.name()
                        + "\" redefines message \""
                        + messageKey
                        + "\" inconsistently; previously \""
                        + orig
                        + "\", now \""
                        + message
                        + "\""
                    ));
                }
            }
        }

        for (Entry<String, String> e : allMessages.entrySet()) {
            String messageKey = e.getKey();
            String message    = e.getValue();

            mp.printf("%1$-32s = %2$s%n", messageKey, message);
        }
    }
}
