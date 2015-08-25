
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
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javadoc.*;

import de.unkrig.commons.doclet.Annotations;
import de.unkrig.commons.doclet.Docs;
import de.unkrig.commons.doclet.Types;
import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.doclet.html.Html.LinkMaker;
import de.unkrig.commons.io.IoUtil;
import de.unkrig.commons.lang.protocol.Consumer;
import de.unkrig.commons.lang.protocol.ConsumerUtil;
import de.unkrig.commons.lang.protocol.ConsumerWhichThrows;
import de.unkrig.commons.lang.protocol.Longjump;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.util.collections.IterableUtil;
import de.unkrig.commons.util.collections.IterableUtil.ElementWithContext;
import de.unkrig.doclet.cs.html.templates.AllRulesFrameHtml;
import de.unkrig.doclet.cs.html.templates.IndexHtml;
import de.unkrig.doclet.cs.html.templates.OverviewSummaryHtml;
import de.unkrig.doclet.cs.html.templates.QuickfixDetailHtml;
import de.unkrig.doclet.cs.html.templates.RuleDetailHtml;
import de.unkrig.notemplate.NoTemplate;
import de.unkrig.notemplate.javadocish.IndexPages;
import de.unkrig.notemplate.javadocish.IndexPages.IndexEntry;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractRightFrameHtml;
import net.sf.eclipsecs.core.config.meta.IOptionProvider;

/**
 * A doclet that creates ECLIPSE-CS metadata files and/or documentation for CheckStyle rules in MediaWiki markup
 * format.
 */
public final
class CsDoclet {

    /**
     * Doclets are never instantiated.
     */
    private CsDoclet() {}

    private static final Pattern SETTER = Pattern.compile("set[A-Z].*");

    public static LanguageVersion languageVersion() { return LanguageVersion.JAVA_1_5; }

    enum IndexStyle { NONE, SINGLE, SPLIT }

    /**
     * See <a href="https://docs.oracle.com/javase/6/docs/technotes/guides/javadoc/doclet/overview.html">"Doclet
     * Overview"</a>.
     */
    public static int
    optionLength(String option) {

        // Options that go into the "Options" object:
        if ("-d".equals(option))           return 2;
        if ("-windowtitle".equals(option)) return 2;
        if ("-doctitle".equals(option))    return 2;
        if ("-header".equals(option))      return 2;
        if ("-footer".equals(option))      return 2;
        if ("-top".equals(option))         return 2;
        if ("-bottom".equals(option))      return 2;
        if ("-notimestamp".equals(option)) return 1;

        // "Other" options:
        if ("-checkstyle-metadata.properties-dir".equals(option)) return 2;
        if ("-checkstyle-metadata.xml-dir".equals(option))        return 2;
        if ("-messages.properties-dir".equals(option))            return 2;
        if ("-mediawiki-dir".equals(option))                      return 2;
        if ("-link".equals(option))                               return 2;
        if ("-linkoffline".equals(option))                        return 3;
        if ("-splitindex".equals(option))                         return 1;
        if ("-noindex".equals(option))                            return 1;

        return 0;
    }

    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/doclet/overview.html">"Doclet
     * Overview"</a>.
     */
    public static boolean
    start(final RootDoc rootDoc) throws IOException {

        // Because "IMarkerResolution2.getLabel()" and "IMarkerResolution2.getDescription()" eventually use "NLS",
        // we have to change the default locale to "ENGLISH", because we want the quickfixes' labels and descriptions
        // in english.
        Locale.setDefault(Locale.ENGLISH);

        boolean generateHtml     = false;
        Options options          = new Options();

        File    checkstyleMetadataDotPropertiesDir = null;
        File    checkstyleMetadataDotXmlDir        = null;
        File    messagesDotPropertiesDir           = null;
        File    mediawikiDir                       = null;

        final Map<String /*packageName*/, URL /*target*/> externalJavadocs = new HashMap<String, URL>();

        IndexStyle indexStyle = IndexStyle.SINGLE;

        for (String[] option : rootDoc.options()) {

            // Options that go into the "Options" object:
            if ("-d".equals(option[0])) {
                options.destination = new File(option[1]);
                generateHtml        = true;
            } else
            if ("-windowtitle".equals(option[0])) {
                options.windowTitle = option[1];
            } else
            if ("-doctitle".equals(option[0])) {
                options.docTitle = option[1];
            } else
            if ("-header".equals(option[0])) {
                options.header = option[1];
            } else
            if ("-footer".equals(option[0])) {
                options.footer = option[1];
            } else
            if ("-top".equals(option[0])) {
                options.top = option[1];
            } else
            if ("-bottom".equals(option[0])) {
                options.bottom = option[1];
            } else
            if ("-notimestamp".equals(option[0])) {
                options.noTimestamp = Boolean.parseBoolean(option[1]);
            } else

            // "Other" options.
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
            if ("-link".equals(option[0])) {
                URL targetUrl = new URL(option[1] + '/');
                Docs.readExternalJavadocs(targetUrl, targetUrl, externalJavadocs, rootDoc);
            } else
            if ("-linkoffline".equals(option[0])) {
                URL targetUrl      = new URL(option[1] + '/');
                URL packageListUrl = new URL(option[2] + '/');

                Docs.readExternalJavadocs(targetUrl, packageListUrl, externalJavadocs, rootDoc);
            } else
            if ("-splitindex".equals(option[0])) {
                indexStyle = IndexStyle.SPLIT;
            } else
            if ("-noindex".equals(option[0])) {
                indexStyle = IndexStyle.NONE;
            } else
            {

                // It is quite counterintuitive, but 'options()' returns ALL options, not only those which
                // qualified by 'optionLength()'.
                ;
            }
        }

        if (
            !generateHtml
            && checkstyleMetadataDotPropertiesDir == null
            && checkstyleMetadataDotXmlDir == null
            && messagesDotPropertiesDir == null
            && mediawikiDir == null
        ) {
            rootDoc.printWarning(
                "None of \"-d\", \"-checkstyle-metadata.properties-dir\", \"-checkstyle-metadata.xml-dir\", "
                + "\"-messages.properties-dir\" and \"-mediawiki-dir\" specified - nothing to be done."
            );
        }

        ClassDoc checkClass, filterClass, quickfixClass;
        try {
            checkClass    = Docs.classNamed(rootDoc, "com.puppycrawl.tools.checkstyle.api.Check");
            filterClass   = Docs.classNamed(rootDoc, "com.puppycrawl.tools.checkstyle.api.Filter");
            quickfixClass = Docs.classNamed(rootDoc, "net.sf.eclipsecs.ui.quickfixes.ICheckstyleMarkerResolution");
        } catch (Longjump l) {
            return false;
        }

        Html html = new Html(new Html.ExternalJavadocsLinkMaker(externalJavadocs, new LinkMaker() {

            @Override @Nullable public String
            makeHref(Doc from, Doc to, RootDoc rootDoc) {

                if (!(to instanceof ClassDoc)) return null;
                ClassDoc cd = (ClassDoc) to;

                if (Docs.isSubclassOf(cd, checkClass))    return "checks/"     + cd.simpleTypeName() + ".html";
                if (Docs.isSubclassOf(cd, filterClass))   return "filters/"    + cd.simpleTypeName() + ".html";
                if (Docs.isSubclassOf(cd, quickfixClass)) return "quickfixes/" + cd.simpleTypeName() + ".html";

                return null;
            }

            @Override public String
            makeDefaultLabel(Doc from, Doc to, RootDoc rootDoc) { return to.name(); }
        }));

        // Process all specified packages.
        Collection<Rule>         allRules      = new ArrayList<Rule>();
        Collection<Quickfix> allQuickfixes = new ArrayList<Quickfix>();
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

            final Collection<Rule> rulesInPackage;
            try {
                rulesInPackage = CsDoclet.rules(classDocs.values(), rootDoc, html);
            } catch (Longjump l) {
                continue;
            }
            allRules.addAll(rulesInPackage);

            final Collection<Quickfix> quickfixesInPackage;
            try {
                quickfixesInPackage = CsDoclet.quickfixes(classDocs.values(), rootDoc, html);
            } catch (Longjump l) {
                continue;
            }
            allQuickfixes.addAll(quickfixesInPackage);

            // Generate 'checkstyle-metadata.properties' for the package.
            if (checkstyleMetadataDotPropertiesDir != null) {

                CsDoclet.printToFile(
                    new File(new File(
                        checkstyleMetadataDotPropertiesDir,
                        checkstylePackage.replace('.', File.separatorChar)
                    ), "checkstyle-metadata.properties"),
                    Charset.forName("ISO-8859-1"),
                    pw -> {
                        CheckstyleMetadataDotPropertiesGenerator.generate(rulesInPackage, pw, rootDoc);
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
                    pw -> {
                        CheckstyleMetadataDotXmlGenerator.generate(rulesInPackage, pw, rootDoc);
                    }
                );
            }

            // Generate 'messages.properties' for the package.
            if (messagesDotPropertiesDir != null) {
                CsDoclet.printToFile(
                    new File(new File(
                        checkstyleMetadataDotPropertiesDir,
                        checkstylePackage.replace('.', File.separatorChar)
                    ), "messages.properties"),
                    Charset.forName("ISO-8859-1"),
                    pw -> {
                        MessagesDotPropertiesGenerator.generate(rulesInPackage, pw, rootDoc);
                    }
                );
            }

            // Generate MediaWiki markup documents for each rule in the package.
            if (mediawikiDir != null) {

                for (final Rule rule : rulesInPackage) {
                    try {
                        CsDoclet.printToFile(
                            new File(
                                new File(mediawikiDir, rule.family()),
                                rule.name().replaceAll(":\\s+", " ") + ".mediawiki"
                            ),
                            Charset.forName("ISO-8859-1"),
                            pw -> {
                                MediawikiGenerator.generate(rule, pw, rootDoc);
                            }
                        );
                    } catch (Longjump l) {
                        ;
                    }
                }
            }
        }

        // Generate HTML (JAVADOCish) documentation.
        if (generateHtml) {
            CsDoclet.generateHtml(allRules, allQuickfixes, options, indexStyle, rootDoc, html);
        }

        return true;
    }

    /**
     * Generates all HTML documents, including the static ones ("stylesheet.css", for example).
     */
    private static void
    generateHtml(
        Collection<Rule>     allRules,
        Collection<Quickfix> allQuickfixes,
        Options              options,
        IndexStyle           indexStyle,
        RootDoc              rootDoc,
        Html                 html
    ) throws IOException {

        // Create "stylesheet.css".
        IoUtil.copyResource(
            CsDoclet.class.getClassLoader(),
            "de/unkrig/doclet/cs/html/templates/stylesheet.css",
            new File(options.destination, "stylesheet.css"),
            true                                                  // createMissingParentDirectories
        );

        final Collection<IndexEntry> indexEntries       = new ArrayList<IndexEntry>();
        Consumer<IndexEntry>         indexEntryConsumer = ConsumerUtil.addToCollection(indexEntries);

        // Render "index.html" (the frameset).
        NoTemplate.render(
            IndexHtml.class,
            new File(options.destination, "index.html"),
            indexHtml -> { indexHtml.render(options); }
        );

        String indexLink;
        switch (indexStyle) {
        case NONE:   indexLink = AbstractRightFrameHtml.DISABLED; break;
        case SINGLE: indexLink = "index-all.html";                break;
        case SPLIT:  indexLink = "index-pages/index-1.html";      break;
        default:     throw new AssertionError(indexStyle);
        }

        // Render the per-rule document for all rules.
        for (ElementWithContext<Rule> rule : IterableUtil.iterableWithContext(allRules)) {

            NoTemplate.render(
                RuleDetailHtml.class, // templateClass
                new File(             // outputFile
                    options.destination,
                    rule.current().family() + '/' + ((ClassDoc) rule.current().ref()).simpleTypeName() + ".html"
                ),
                ruleHtml -> {         // renderer
                    ruleHtml.render(rule, html, rootDoc, options, indexLink, indexEntryConsumer);
                }
            );
        }

        // Generate documentation for quickfixes.
        for (ElementWithContext<Quickfix> quickfix : IterableUtil.iterableWithContext(allQuickfixes)) {

            NoTemplate.render(
                QuickfixDetailHtml.class, // templateClass
                new File(                 // outputFile
                    options.destination,
                    "quickfixes/" + ((ClassDoc) quickfix.current().ref()).simpleTypeName() + ".html"
                ),
                quickfixHtml -> {         // renderer
                    quickfixHtml.render(quickfix, html, rootDoc, options, indexLink, indexEntryConsumer);
                }
            );
        }

        // Generate the document that is loaded into the "left frame" and displays all rules in "family" groups and
        // the quickfixes.
        NoTemplate.render(
            AllRulesFrameHtml.class,
            new File(options.destination, "allrules-frame.html"),
            allRulesFrameHtml -> {
                allRulesFrameHtml.render(allRules, allQuickfixes, rootDoc, options, html);
            }
        );

        // Generate "overview-summary.html" - the document that is initially loaded into the "right frame" and displays
        // all rule summaries (rule name and first sentence of description).
        NoTemplate.render(
            OverviewSummaryHtml.class,
            new File(options.destination, "overview-summary.html"),
            overviewSummaryHtml -> {
                overviewSummaryHtml.render(allRules, allQuickfixes, rootDoc, options, indexLink, html);
            }
        );

        // Generate the index file(s).
        switch (indexStyle) {

        case NONE:
            ;
            break;

        case SINGLE:
            IndexPages.createSingleIndex(
                new File(options.destination, "index-all.html"), // outputFile
                indexEntries,                                    // entries
                options,                                         // options
                new String[] {                                   // nav1
                    "Overview",   "overview-summary.html",
                    "Rule",       AbstractRightFrameHtml.DISABLED,
                    "Deprecated", "deprecated-list.html",
                    "Index",      AbstractRightFrameHtml.HIGHLIT,
                    "Help",       "help-doc.html",
                }
            );
            break;

        case SPLIT:
            IndexPages.createSplitIndex(
                new File(options.destination, "index-all.html"), // outputFile
                indexEntries,                                    // entries
                options,                                         // options
                new String[] {                                   // nav1
                    "Overview",   "overview-summary.html",
                    "Rule",       AbstractRightFrameHtml.DISABLED,
                    "Deprecated", "deprecated-list.html",
                    "Index",      AbstractRightFrameHtml.HIGHLIT,
                    "Help",       "help-doc.html",
                }
            );
            break;

        default:
            throw new AssertionError(indexStyle);
        }
    }

    /**
     * Creates the named {@code file}, lets the {@code printer} print text to it, and closes the file.
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

        File directory = file.getParentFile();

        directory.mkdirs();

        File newFile = new File(directory, "." + file.getName() + ".new");

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

    /**
     * @return         The HTML document {@code s}, converted to plain text
     * @param position Merely used when printing warnings
     * @param rootDoc  Merely used when printing warnings
     */
    public static String
    htmlToPlainText(String s, SourcePosition position, RootDoc rootDoc) {

        for (;;) {
            Matcher matcher = CsDoclet.CODE_BLOCK.matcher(s);
            if (matcher.find()) {
                s = matcher.group(1) + matcher.group(2) + s.substring(matcher.end());
            } else
            {
                break;
            }
        }

        {
            Matcher matcher = CsDoclet.CONTAINS_HTML_MARKUP.matcher(s);
            if (matcher.find()) {
                rootDoc.printWarning(
                    position,
                    "'" + matcher.group() + "' cannot be reasonably converted to plain text"
                );
            }
        }

        return s;
    }
    private static final Pattern
    CONTAINS_HTML_MARKUP = Pattern.compile("<\\s*\\w.*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern
    CODE_BLOCK = Pattern.compile("(.*)<code>(.*?)</code>", Pattern.CASE_INSENSITIVE);

    /**
     * @return The data type guessed from the method's argument type
     */
    public static Type
    guessDatatype(MethodDoc methodDoc, RootDoc rootDoc) throws Longjump {

        Parameter[] parameters = methodDoc.parameters();

        if (parameters.length != 1) {
            rootDoc.printError(
                methodDoc.position(),
                "Cannot guess the property type because the number of parameters is not one"
            );
            throw new Longjump();
        }

        return parameters[0].type();
    }

    /** Representation of a CheckStyle rule (a check or a filter). */
    public
    interface Rule {

        /** @return The doc comment of the Java element that implements this rule */
        Doc ref();

        /** @return The family to which this rule belongs ("checks" or "filters") */
        String family();

        /** @return The ID of the group to which this rule belongs */
        String group();

        /** @return The default localized name of the {@link #group()} */
        String groupName();

        /** @return The default localized name of this rule */
        String name();

        /** @return The 'internal name' of this rule, which is identical with the implementation's class name */
        String internalName();

        /** @return The simple (unqualified) class name of the implementation  */
        String simpleName();

        /** The 'parent rule', as defined by eclipse-cs */
        String parent();

        /** @return The first sentence of the description of this rule; may contain HTML markup */
        String shortDescription();

        /** @return The verbose description of this rule; may contain HTML markup */
        String longDescription();

        /** @return The properties of this rule */
        Collection<RuleProperty> properties();

        /** @return The quickfixes which are related to this rule */
        @Nullable ClassDoc[] quickfixClasses();

        /** @return Whether this rule has a severity; typically checks do, and filters don't */
        @Nullable Boolean hasSeverity();

        /** @return The default localized messages of this rule */
        SortedMap<String, String> messages();
    }

    /**
     * Derives a collection of CheckStyle rules from the given {@code classDocs}.
     */
    public static Collection<Rule>
    rules(final Collection<ClassDoc> classDocs, RootDoc rootDoc, Html html) throws Longjump {

        ClassDoc checkClass  = Docs.classNamed(rootDoc, "com.puppycrawl.tools.checkstyle.api.Check");
        ClassDoc filterClass = Docs.classNamed(rootDoc, "com.puppycrawl.tools.checkstyle.api.Filter");

        List<Rule> rules = new ArrayList<CsDoclet.Rule>();
        for (final ClassDoc classDoc : classDocs) {

            AnnotationDesc ra = Annotations.get(classDoc, "Rule");
            if (ra == null) continue;

            String family;
            if (Docs.isSubclassOf(classDoc, checkClass)) {
                family = "checks";
            } else
            if (Docs.isSubclassOf(classDoc, filterClass)) {
                family = "filters";
            } else
            {
                rootDoc.printError(classDoc.position(), "Rule cannot be identified as a check or a filter");
                continue;
            }

            try {

                rules.add(CsDoclet.rule(ra, classDoc, rootDoc, family, html));
            } catch (Longjump l) {
                ; // SUPPRESS CHECKSTYLE AvoidHidingCause
            }
        }

        return rules;
    }

    /**
     * Derives a collection of quickfixes from the given {@code classDocs}.
     *
     * @param quickfixes Quickfixes to re-use, or the set to add new quickfixes to
     */
    public static Collection<Quickfix>
    quickfixes(final Collection<ClassDoc> classDocs, RootDoc rootDoc, Html html) throws Longjump {

        ClassDoc
        quickfixInterface = Docs.classNamed(rootDoc, "net.sf.eclipsecs.ui.quickfixes.ICheckstyleMarkerResolution");

        List<Quickfix> quickfixes = new ArrayList<Quickfix>();
        for (final ClassDoc classDoc : classDocs) {

            if (!Docs.isSubclassOf(classDoc, quickfixInterface)) continue;

            try {

                final String
                className = classDoc.qualifiedTypeName();

                final String
                quickfixLabel = html.optionalTag(
                    classDoc,
                    "@cs-label",
                    classDoc.qualifiedTypeName(), // defaulT
                    rootDoc
                );

                final String
                quickfixShortDescription = html.fromTags(
                    classDoc.firstSentenceTags(),
                    classDoc,
                    rootDoc
                );

                final String
                quickfixLongDescription = html.fromTags(
                    classDoc.inlineTags(),
                    classDoc,
                    rootDoc
                );

                quickfixes.add(new Quickfix() {
                    @Override public Doc              ref()              { return classDoc;                 }
                    @Override @Nullable public String className()        { return className;                }
                    @Override public String           label()            { return quickfixLabel;            }
                    @Override public String           shortDescription() { return quickfixShortDescription; }
                    @Override public String           longDescription()  { return quickfixLongDescription;  }
                });
            } catch (Longjump l) {}
        }

        return quickfixes;
    }

    /**
     * Parses a CheckStyle rule.
     */
    private static Rule
    rule(AnnotationDesc ruleAnnotation, final ClassDoc classDoc, RootDoc rootDoc, final String family, Html html)
    throws Longjump {

        // CHECKSTYLE LineLength:OFF
        final String     group            = Annotations.getElementValue(ruleAnnotation, "group", String.class);
        final String     groupName        = Annotations.getElementValue(ruleAnnotation, "groupName", String.class);
        final String     simpleName       = classDoc.simpleTypeName();
        final String     name             = Annotations.getElementValue(ruleAnnotation, "name", String.class);
        final String     internalName     = classDoc.qualifiedTypeName();
        final String     parent           = Annotations.getElementValue(ruleAnnotation, "parent", String.class);
        final String     shortDescription = html.fromTags(classDoc.firstSentenceTags(), classDoc, rootDoc);
        final String     longDescription  = html.fromTags(classDoc.inlineTags(), classDoc, rootDoc);
        final ClassDoc[] quickfixClasses  = Annotations.getElementValue(ruleAnnotation, "quickfixes", ClassDoc[].class);
        final Boolean    hasSeverity      = Annotations.getElementValue(ruleAnnotation, "hasSeverity",  Boolean.class);
        // CHECKSTYLE LineLength:ON

        assert group     != null;
        assert groupName != null;
        assert name      != null;
        assert parent    != null;

        final Collection<RuleProperty> properties = CsDoclet.properties(classDoc, rootDoc, html);

        final SortedMap<String, String> messages = new TreeMap<String, String>();
        for (ClassDoc cd = classDoc; cd != null; cd = cd.superclass()) {
            for (FieldDoc fd : cd.fields(false)) {

                AnnotationDesc a = Annotations.get(fd, "Message");
                if (a == null) continue;

                final String messageKey;
                {
                    Object o = fd.constantValue();
                    if (o == null) {
                        rootDoc.printError(
                            fd.position(),
                            "Field '" + fd.name() + "' has a '@Message' annotation, but not a constant value"
                        );
                        continue;
                    }

                    if (!(o instanceof String)) {
                        rootDoc.printError(
                            fd.position(),
                            "Constant '" + fd.name() + "' must have type 'String'"
                        );
                        continue;
                    }

                    messageKey = (String) o;
                }

                String message = Annotations.getElementValue(a, "value", String.class);
                if (message == null) {
                    rootDoc.printError(fd.position(), "Message lacks a default text");
                    continue;
                }

                String orig = messages.put(messageKey, message);

                if (orig != null && !message.equals(orig)) {
                    rootDoc.printError(fd.position(), (
                        "Inconsistent redefinition of message \""
                        + messageKey
                        + "\": Previously \""
                        + orig
                        + "\", now \""
                        + message
                        + "\""
                    ));
                }
            }
        }

        return new Rule() {
            @Override public Doc                       ref()               { return classDoc;         }
            @Override public String                    family()            { return family;           }
            @Override public String                    group()             { return group;            }
            @Override public String                    groupName()         { return groupName;        }
            @Override public String                    simpleName()        { return simpleName;       }
            @Override public String                    name()              { return name;             }
            @Override public String                    internalName()      { return internalName;     }
            @Override public String                    parent()            { return parent;           }
            @Override public String                    shortDescription()  { return shortDescription; }
            @Override public String                    longDescription()   { return longDescription;  }
            @Override public Collection<RuleProperty>  properties()        { return properties;       }
            @Override public ClassDoc[]                quickfixClasses()   { return quickfixClasses;  }
            @Override @Nullable public Boolean         hasSeverity()       { return hasSeverity;      }
            @Override public SortedMap<String, String> messages()          { return messages;         }
        };
    }

    /** Representation of a property of a rule. */
    public
    interface RuleProperty {

        /** To be displayed <i>above</i> the property; useful to form groups of properties. May contain HTML markup. */
        @Nullable String intertitle();

        /** @return The doc comment from which this property originates; useful for resolution of relative names */
        Doc ref();

        /** @return The default localized name */
        String name();

        /** @return The one-sentence description */
        String shortDescription();

        /** @return The verbose description */
        String longDescription();

        /**
         * @return The <a href="http://eclipse-cs.sourceforge.net/dtds/checkstyle-metadata_1_1.dtd">datatype</a> of
         *         this property
         */
        String datatype();

        /** @return The {@link IOptionProvider} for this property */
        @Nullable Class<?> optionProvider();

        /** @return The 'value options' list of this property */
        @Nullable String[] valueOptions();

        /** @return The default value for this property */
        @Nullable Object defaultValue();

        /** @return The 'override-default' value for this property */
        @Nullable Object overrideDefaultValue();
    }

    /**
     * Representation of a 'quickfix' for a rule.
     */
    public
    interface Quickfix {

        /** @return The doc comment to which this quickfix is related */
        Doc ref();

        /** @return Fully qualified class name of the quickfix */
        @Nullable String className();

        /** @return The "label" of the quickfix, as given in the "{@code @cs-label}" block tag */
        @Nullable String label();

        /** @return The first sentence of the description of; may contain HTML markup */
        String shortDescription();

        /** @return The verbose description; may contain HTML markup */
        String longDescription();
    }

    /**
     * Invokes {@link RulePropertyHandler#handeRuleProperty(String, SourcePosition, String, String, String, String,
     * Class, AnnotationValue[], Object, Object)} for each property of the rule designated by {@code classDoc}
     */
    public static Collection<RuleProperty>
    properties(ClassDoc classDoc, RootDoc rootDoc, Html html) throws Longjump {

        List<RuleProperty> properties = new ArrayList<RuleProperty>();
        for (final MethodDoc methodDoc : classDoc.methods(false)) {

            // Is this the setter for a property?
            // For possible 'datatype's see "http://eclipse-cs.sourceforge.net/dtds/checkstyle-metadata_1_1.dtd"
            AnnotationDesc rpa = Annotations.get(methodDoc, "BooleanRuleProperty");
            if (rpa == null) rpa = Annotations.get(methodDoc, "StringRuleProperty");
            if (rpa == null) rpa = Annotations.get(methodDoc, "MultiCheckRuleProperty");
            if (rpa == null) rpa = Annotations.get(methodDoc, "SingleSelectRuleProperty");
            if (rpa == null) rpa = Annotations.get(methodDoc, "RegexRuleProperty");
            if (rpa == null) rpa = Annotations.get(methodDoc, "IntegerRuleProperty");
            if (rpa == null) continue;

            // Determine the (optional) 'intertitle', which is useful to form groups of properties in a documentation.
            final String intertitle = html.optionalTag(methodDoc, "@cs-intertitle", rootDoc);

            // Determine the property name.
            final String propertyName;
            {
                String n = Annotations.getElementValue(rpa, "name", String.class);
                if (n == null) {
                    String methodName = methodDoc.name();
                    if (!CsDoclet.SETTER.matcher(methodName).matches()) {
                        rootDoc.printError(methodDoc.position(), "Cannot determine property name");
                        continue;
                    }
                    n = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                }
                propertyName = n;
            }

            // Determine short and long description.
            final String shortDescription = html.fromTags(methodDoc.firstSentenceTags(), methodDoc, rootDoc);
            final String longDescription  = html.fromTags(methodDoc.inlineTags(),        methodDoc, rootDoc);

            // Determine the (optional) option provider.
            final Class<?> optionProvider;
            {
                final Type t = Annotations.getElementValue(rpa, "optionProvider", Type.class);
                optionProvider = t == null ? null : Types.loadType(methodDoc.position(), t, rootDoc);
            }

            // Determine the (optional) value options.
            final String[] valueOptions = Annotations.getElementValue(rpa, "valueOptions", String[].class);

            if (optionProvider != null && valueOptions != null) {
                rootDoc.printError(
                    methodDoc.position(),
                    "@cs-property-option-provider and @cs-property-value-option are mutually exclusive"
                );
                continue;
            }

            // Determine the datatype.
            final String datatype;
            {
                String s = rpa.annotationType().simpleTypeName();
                datatype = s.substring(0, s.indexOf("RuleProperty"));
            }

            // Determine the default values.
            final Object
            defaultValue = Annotations.getElementValue(rpa, "defaultValue", String.class);
            final String
            overrideDefaultValue = Annotations.getElementValue(rpa, "overrideDefaultValue", String.class);

            properties.add(new RuleProperty() {

                @Override @Nullable public String   intertitle()           { return intertitle;           }
                @Override public Doc                ref()                  { return methodDoc;            }
                @Override public String             name()                 { return propertyName;         }
                @Override public String             shortDescription()     { return shortDescription;     }
                @Override public String             longDescription()      { return longDescription;      }
                @Override public String             datatype()             { return datatype;             }
                @Override @Nullable public Class<?> optionProvider()       { return optionProvider;       }
                @Override @Nullable public String[] valueOptions()         { return valueOptions;         }
                @Override @Nullable public Object   defaultValue()         { return defaultValue;         }
                @Override @Nullable public Object   overrideDefaultValue() { return overrideDefaultValue; }
            });
        }

        return properties;
    }
}
