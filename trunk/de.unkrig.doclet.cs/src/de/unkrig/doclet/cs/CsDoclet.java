
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javadoc.*;

import de.unkrig.commons.doclet.Annotations;
import de.unkrig.commons.doclet.Docs;
import de.unkrig.commons.doclet.Tags;
import de.unkrig.commons.doclet.Types;
import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.doclet.html.Html.LinkMaker;
import de.unkrig.commons.io.IoUtil;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.Consumer;
import de.unkrig.commons.lang.protocol.ConsumerUtil;
import de.unkrig.commons.lang.protocol.ConsumerWhichThrows;
import de.unkrig.commons.lang.protocol.Longjump;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.text.CamelCase;
import de.unkrig.commons.util.collections.IterableUtil;
import de.unkrig.commons.util.collections.IterableUtil.ElementWithContext;
import de.unkrig.csdoclet.annotation.BooleanRuleProperty;
import de.unkrig.csdoclet.annotation.FileRuleProperty;
import de.unkrig.csdoclet.annotation.HiddenRuleProperty;
import de.unkrig.csdoclet.annotation.IntegerRuleProperty;
import de.unkrig.csdoclet.annotation.MultiCheckRuleProperty;
import de.unkrig.csdoclet.annotation.RegexRuleProperty;
import de.unkrig.csdoclet.annotation.SingleSelectRuleProperty;
import de.unkrig.csdoclet.annotation.StringRuleProperty;
import de.unkrig.doclet.cs.CsDoclet.RuleProperty.Datatype;
import de.unkrig.doclet.cs.html.templates.AllRulesFrameHtml;
import de.unkrig.doclet.cs.html.templates.IndexHtml;
import de.unkrig.doclet.cs.html.templates.OptionProviderDetailHtml;
import de.unkrig.doclet.cs.html.templates.OverviewSummaryHtml;
import de.unkrig.doclet.cs.html.templates.QuickfixDetailHtml;
import de.unkrig.doclet.cs.html.templates.RuleDetailHtml;
import de.unkrig.notemplate.NoTemplate;
import de.unkrig.notemplate.javadocish.IndexPages;
import de.unkrig.notemplate.javadocish.IndexPages.IndexEntry;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractRightFrameHtml;

/**
 * A doclet that creates ECLIPSE-CS metadata files and/or documentation for CheckStyle rules in MediaWiki markup
 * format.
 */
public final
class CsDoclet {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    /**
     * Doclets are never instantiated.
     */
    private CsDoclet() {}

    private static final Pattern SETTER = Pattern.compile("set[A-Z].*");

    public static LanguageVersion languageVersion() { return LanguageVersion.JAVA_1_5; }

    enum IndexStyle {

        /**
         * Do not generate an index (typically due to a "-no-index" command line option).
         */
        NONE,

        /**
         * Generate an index on a single page (typically the default).
         */
        SINGLE,

        /**
         * Generate an index with one page per initial ("A", "B", ...) (typically due to a "-split-index" command line
         * option).
         */
        SPLIT,
    }

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
        ) {
            rootDoc.printWarning(
                "None of \"-d\", \"-checkstyle-metadata.properties-dir\", \"-checkstyle-metadata.xml-dir\" and "
                + "\"-messages.properties-dir\" specified - nothing to be done."
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
        Collection<Rule>     allRules           = new ArrayList<Rule>();
        Collection<Quickfix> allQuickfixes      = new ArrayList<Quickfix>();
        Set<OptionProvider>  allOptionProviders = new TreeSet<OptionProvider>(new Comparator<OptionProvider>() {

            @SuppressWarnings("null") @Override public int
            compare(@Nullable OptionProvider op1, @Nullable OptionProvider op2) {
                return op1.className().compareTo(op2.className());
            }
        });

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

            try {
                final Collection<Rule> rulesInPackage = CsDoclet.rules(
                    classDocs.values(),
                    rootDoc,
                    ConsumerUtil.addToCollection(allOptionProviders),
                    html
                );

                if (!rulesInPackage.isEmpty()) {

                    allRules.addAll(rulesInPackage);

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
                }
            } catch (Longjump l) {}

            final Collection<Quickfix> quickfixesInPackage;
            try {
                quickfixesInPackage = CsDoclet.quickfixes(classDocs.values(), rootDoc, html);
                allQuickfixes.addAll(quickfixesInPackage);
            } catch (Longjump l) {}
        }

        // Generate HTML (JAVADOCish) documentation.
        if (generateHtml) {
            CsDoclet.generateHtml(allRules, allQuickfixes, allOptionProviders, options, indexStyle, rootDoc, html);
        }

        return true;
    }

    /**
     * Generates all HTML documents, including the static ones ("stylesheet.css", for example).
     * @param allOptionProviders TODO
     */
    private static void
    generateHtml(
        Collection<Rule>           allRules,
        Collection<Quickfix>       allQuickfixes,
        Collection<OptionProvider> allOptionProviders,
        Options                    options,
        IndexStyle                 indexStyle,
        RootDoc                    rootDoc,
        Html                       html
    ) throws IOException {

        // Create "stylesheet.css".
        IoUtil.copyResource(
            CsDoclet.class.getClassLoader(),
            "de/unkrig/doclet/cs/html/templates/stylesheet.css",
            new File(options.destination, "stylesheet.css"),
            true                                                  // createMissingParentDirectories
        );

        final Collection<IndexEntry>       indexEntries       = new ArrayList<IndexEntry>();
        final Consumer<? super IndexEntry> indexEntryConsumer = ConsumerUtil.addToCollection(indexEntries);

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
                    rule.current().familyPlural() + '/' + ((ClassDoc) rule.current().ref()).simpleTypeName() + ".html"
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

        // Generate documentation for option providers.
        for (ElementWithContext<OptionProvider> optionProvider : IterableUtil.iterableWithContext(allOptionProviders)) {

            NoTemplate.render(
                OptionProviderDetailHtml.class, // templateClass
                new File(                       // outputFile
                    options.destination,
                    "option-providers/" + optionProvider.current().className() + ".html"
                ),
                optionProviderHtml -> {               // renderer
                    optionProviderHtml.render(optionProvider, html, rootDoc, options, indexLink, indexEntryConsumer);
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
        File                                         file,
        Charset                                      charset,
        ConsumerWhichThrows<? super PrintWriter, EX> printer
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

        /** @return The family to which this rule belongs ("check" or "filter") */
        String familySingular();

        /** @return The family to which this rule belongs ("checks" or "filters") */
        String familyPlural();

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
     *
     * @param usedOptionProviders Gets the option providers used by the rules
     */
    public static Collection<Rule>
    rules(
        final Collection<ClassDoc>       classDocs,
        RootDoc                          rootDoc,
        Consumer<? super OptionProvider> usedOptionProviders,
        Html                             html
    ) throws Longjump {

        ClassDoc checkClass  = Docs.classNamed(rootDoc, "com.puppycrawl.tools.checkstyle.api.Check");
        ClassDoc filterClass = Docs.classNamed(rootDoc, "com.puppycrawl.tools.checkstyle.api.Filter");

        List<Rule> rules = new ArrayList<CsDoclet.Rule>();
        for (final ClassDoc classDoc : classDocs) {

            AnnotationDesc ra = Annotations.get(classDoc, "Rule");
            if (ra == null) continue;

            String familySingular, familyPlural;
            if (Docs.isSubclassOf(classDoc, checkClass)) {
                familySingular = "check";
                familyPlural   = "checks";
            } else
            if (Docs.isSubclassOf(classDoc, filterClass)) {
                familySingular = "filter";
                familyPlural   = "filters";
            } else
            {
                rootDoc.printError(classDoc.position(), "Rule cannot be identified as a check or a filter");
                continue;
            }

            try {

                rules.add(
                    CsDoclet.rule(ra, classDoc, rootDoc, familySingular, familyPlural, usedOptionProviders, html)
                );
            } catch (Longjump l) {
                ; // SUPPRESS CHECKSTYLE AvoidHidingCause
            }
        }

        return rules;
    }

    /**
     * Derives a collection of quickfixes from the given {@code classDocs}.
     */
    public static Collection<Quickfix>
    quickfixes(final Collection<ClassDoc> classDocs, RootDoc rootDoc, Html html) throws Longjump {

        ClassDoc
        quickfixInterface = Docs.classNamed(rootDoc, "net.sf.eclipsecs.ui.quickfixes.ICheckstyleMarkerResolution");

        List<Quickfix> quickfixes = new ArrayList<Quickfix>();
        for (final ClassDoc classDoc : classDocs) {

            if (!Docs.isSubclassOf(classDoc, quickfixInterface)) continue;

            if (classDoc.isAbstract()) continue;

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
     * @param usedOptionProviders
     */
    private static Rule
    rule(
        AnnotationDesc                   ruleAnnotation,
        final ClassDoc                   classDoc,
        RootDoc                          rootDoc,
        final String                     familySingular,
        String                           familyPlural,
        Consumer<? super OptionProvider> usedOptionProviders,
        Html                             html
    ) throws Longjump {

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

        final Collection<RuleProperty> properties = CsDoclet.properties(classDoc, rootDoc, html, usedOptionProviders);

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
            @Override public String                    familySingular()    { return familySingular;   }
            @Override public String                    familyPlural()      { return familyPlural;     }
            @Override public String                    group()             { return group;            }
            @Override public String                    groupName()         { return groupName;        }
            @Override public String                    simpleName()        { return simpleName;       }
            @Override public String                    name()              { return name;             }
            @Override public String                    internalName()      { return internalName;     }
            @Override public String                    parent()            { return parent;           }
            @Override public String                    shortDescription()  { return shortDescription; }
            @Override public String                    longDescription()   { return longDescription;  }
            @Override public Collection<RuleProperty>  properties()        { return properties;       }
            @Override @Nullable public ClassDoc[]      quickfixClasses()   { return quickfixClasses;  }
            @Override @Nullable public Boolean         hasSeverity()       { return hasSeverity;      }
            @Override public SortedMap<String, String> messages()          { return messages;         }
        };
    }

    /** Representation of a property of a rule. */
    public
    interface RuleProperty {

        /**
         * The possible data types, as defined <a href="http://eclipse-cs.sourceforge.net/dtds/checkstyle-met
         *adata_1_1.dtd">here</a>, in alphabetical order.
         */
        enum Datatype { BOOLEAN, FILE, HIDDEN, INTEGER, MULTI_CHECK, REGEX, SINGLE_SELECT, STRING }

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
        Datatype datatype();

        /** @return The option provider for this property */
        @Nullable OptionProvider optionProvider();

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

    /** Representation of an "option provider". */
    public
    interface OptionProvider {

        /** @return The value of the "{@code cs-name}" tag, or the qualified class name */
        @Nullable String name();

        /** @return The qualified class name, or {@code null} if this property has only {@code valueOptions=...} */
        @Nullable String className();

        /**
         * @return The one-sentence description
         * @throws Longjump
         */
        @Nullable String shortDescription();

        /**
         * @return The verbose description
         * @throws Longjump
         */
        @Nullable String longDescription();

        /**
         * @return The "value options" as defined by the {@code optionProvider=} ENUM or {@link
         *         net.sf.eclipsecs.core.config.meta.IOptionProvider}, or by {@code valueOptions=...}
         */
        ValueOption[] valueOptions();
    }

    /**
     * Representation of a "value option", i.e. one value that an enumerator may have.
     */
    public
    interface ValueOption {

        /** @return The value of value option */
        String name();

        /** @return The one-sentence description */
        @Nullable String shortDescription();

        /** @return The verbose description */
        @Nullable String longDescription();
    }

    /**
     * Invokes {@link RulePropertyHandler#handeRuleProperty(String, SourcePosition, String, String, String, String,
     * Class, AnnotationValue[], Object, Object)} for each property of the rule designated by {@code classDoc}.
     *
     * @param usedOptionProviders Consumers any option provider (ENUM type or {@link
     *                            net.sf.eclipsecs.core.config.meta.IOptionProvider} needed by the properties
     */
    public static Collection<RuleProperty>
    properties(ClassDoc classDoc, RootDoc rootDoc, Html html, Consumer<? super OptionProvider> usedOptionProviders)
    throws Longjump {

        List<RuleProperty> properties = new ArrayList<RuleProperty>();
        for (final MethodDoc methodDoc : classDoc.methods(false)) {

            // Is this method annotated as a property?
            AnnotationDesc rpa = null;
            SourcePosition pos = methodDoc.position();
            rpa = CsDoclet.xor(rpa, Annotations.get(methodDoc, BooleanRuleProperty.class,      rootDoc), pos, rootDoc);
            rpa = CsDoclet.xor(rpa, Annotations.get(methodDoc, FileRuleProperty.class,         rootDoc), pos, rootDoc);
            rpa = CsDoclet.xor(rpa, Annotations.get(methodDoc, HiddenRuleProperty.class,       rootDoc), pos, rootDoc);
            rpa = CsDoclet.xor(rpa, Annotations.get(methodDoc, IntegerRuleProperty.class,      rootDoc), pos, rootDoc);
            rpa = CsDoclet.xor(rpa, Annotations.get(methodDoc, MultiCheckRuleProperty.class,   rootDoc), pos, rootDoc);
            rpa = CsDoclet.xor(rpa, Annotations.get(methodDoc, RegexRuleProperty.class,        rootDoc), pos, rootDoc);
            rpa = CsDoclet.xor(rpa, Annotations.get(methodDoc, SingleSelectRuleProperty.class, rootDoc), pos, rootDoc);
            rpa = CsDoclet.xor(rpa, Annotations.get(methodDoc, StringRuleProperty.class,       rootDoc), pos, rootDoc);

            if (rpa == null) continue;

            try {
                properties.add(CsDoclet.property(methodDoc, rpa, rootDoc, usedOptionProviders, html));
            } catch (Longjump l) {}
        }

        return properties;
    }

    /**
     * @param usedOptionProvider Gets the option provider used by the property (if any)
     */
    private static RuleProperty
    property(
        MethodDoc                        methodDoc,
        AnnotationDesc                   rpa,
        RootDoc                          rootDoc,
        Consumer<? super OptionProvider> usedOptionProvider,
        Html                             html
    ) throws Longjump {

        // Determine the datatype.
        final Datatype datatype;
        {
            String atsn = rpa.annotationType().simpleTypeName();
            int    idx  = atsn.indexOf("RuleProperty");

            assert idx != -1 : atsn;
            datatype = Datatype.valueOf(CamelCase.toUpperCaseUnderscoreSeparated(atsn.substring(0, idx)));
        }

        // Determine the property name.
        final String propertyName;
        {
            String n = Annotations.getElementValue(rpa, "name", String.class);
            if (n == null) {
                String methodName = methodDoc.name();
                if (!CsDoclet.SETTER.matcher(methodName).matches()) {
                    rootDoc.printError(methodDoc.position(), "Cannot determine property name");
                    throw new Longjump();
                }
                n = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            }
            propertyName = n;
        }

        // Determine short and long description.
        final String ruleShortDescription = html.fromTags(methodDoc.firstSentenceTags(), methodDoc, rootDoc);
        final String ruleLongDescription  = html.fromTags(methodDoc.inlineTags(),        methodDoc, rootDoc);

        // Determine the (optional) option provider.
        final OptionProvider optionProvider;
        {

            // Get the "optionProvider=..." element-value pair.
            ClassDoc opc;
            {
                Type tmp = Annotations.getElementValue(rpa, "optionProvider", Type.class);
                opc = tmp == null ? null : tmp.asClassDoc();
            }

            // Get the "valueOptions=..." element-value pair.
            String[] valueOptions = Annotations.getElementValue(rpa, "valueOptions", String[].class);

            if (opc == null) {
                if (valueOptions == null) {
                    optionProvider = null;
                } else {
                    List<ValueOption> tmp = new ArrayList<CsDoclet.ValueOption>();
                    for (String ev : valueOptions) {
                        tmp.add(new ValueOption() {
                            @Override public String           name()             { return ev; }
                            @Override @Nullable public String shortDescription() { return null; }
                            @Override @Nullable public String longDescription()  { return null; }
                        });
                    }
                    final ValueOption[] valueOptions2 = tmp.toArray(new ValueOption[tmp.size()]);
                    optionProvider = new OptionProvider() {
                        @Override @Nullable public String name()             { return null; }
                        @Override @Nullable public String className()        { return null; }
                        @Override public ValueOption[]    valueOptions()     { return valueOptions2; }
                        @Override @Nullable public String shortDescription() { return null; }
                        @Override @Nullable public String longDescription()  { return null; }
                    };
                }
            } else {
                final String  optionProviderShortDescription = html.fromTags(opc.firstSentenceTags(), opc, rootDoc);
                final String  optionProviderLongDescription  = html.fromTags(opc.inlineTags(),        opc, rootDoc);
                ValueOption[] valueOptions2;
                if (opc.isEnum()) {

                    // Property is an ENUM.
                    List<ValueOption> tmp2 = new ArrayList<ValueOption>();
                    for (FieldDoc fd : opc.enumConstants()) {

                        String valueOptionShortDescription = html.fromTags(fd.firstSentenceTags(), rootDoc, rootDoc);
                        String valueOptionLongDescription  = html.fromTags(fd.inlineTags(),        rootDoc, rootDoc);
                        tmp2.add(new ValueOption() {

                            @Override public String
                            name() { return fd.name().toLowerCase(); }

                            @Override public String
                            shortDescription() { return valueOptionShortDescription; }

                            @Override public String
                            longDescription() { return valueOptionLongDescription; }
                        });
                    }
                    valueOptions2 = tmp2.toArray(new ValueOption[0]);
                } else
                if (opc.subclassOf(Docs.classNamed(rootDoc, "net.sf.eclipsecs.core.config.meta.IOptionProvider"))) {

                    // Property
                    Class<?> opc2 = Types.loadType(methodDoc.position(), opc, rootDoc);

                    List<String> tmp2;
                    try {
                        @SuppressWarnings("unchecked") List<String>
                        tmp3 = (List<String>) opc2.getDeclaredMethod("getOptions").invoke(opc2.newInstance());
                        tmp2 = tmp3;
                    } catch (Exception e) {
                        rootDoc.printError(methodDoc.position(), e.getMessage());
                        throw new Longjump(); // SUPPRESS CHECKSTYLE AvoidHidingCause
                    }
                    List<ValueOption> tmp3 = new ArrayList<CsDoclet.ValueOption>();
                    for (final String von : tmp2) {
                        tmp3.add(new ValueOption() {
                            @Override public String           name()             { return von; }
                            @Override @Nullable public String shortDescription() { return null; }
                            @Override @Nullable public String longDescription()  { return null; }
                        });
                    }
                    valueOptions2 = tmp3.toArray(new ValueOption[0]);
                } else
                {
                    rootDoc.printError(methodDoc.position(), (
                        ""
                        + "Option provider class '"
                        + opc
                        + "' must either extend 'Enum' or implement "
                        + "\"net.sf.eclipsecs.core.config.meta.IOptionProvider\""
                    ));
                    throw new Longjump();
                }

                String qualifiedClassName;
                {
                    ClassDoc containingClass = opc.containingClass();
                    if (containingClass == null) {
                        qualifiedClassName = opc.qualifiedName();
                    } else {
                        qualifiedClassName = opc.containingPackage().name() + '.' + opc.name().replace('.', '$');
                    }
                }
                optionProvider = new OptionProvider() {

                    @Override public String
                    name() { return Tags.optionalTag(opc, "@cs-name", opc.qualifiedName(), rootDoc); }

                    @Override public String
                    className() { return qualifiedClassName; }

                    @Override public String
                    shortDescription() { return optionProviderShortDescription; }

                    @Override public String
                    longDescription() { return optionProviderLongDescription; }

                    @Override public ValueOption[]
                    valueOptions() { return valueOptions2; }
                };

                usedOptionProvider.consume(optionProvider);
            }
        }

        // Determine the default values.
        final Object
        defaultValue = Annotations.getElementValue(rpa, "defaultValue", String.class);
        final String
        overrideDefaultValue = Annotations.getElementValue(rpa, "overrideDefaultValue", String.class);

        return new RuleProperty() {

            @Override public Doc                      ref()                  { return methodDoc;            }
            @Override public String                   name()                 { return propertyName;         }
            @Override public String                   shortDescription()     { return ruleShortDescription; }
            @Override public String                   longDescription()      { return ruleLongDescription;  }
            @Override public Datatype                 datatype()             { return datatype;             }
            @Override @Nullable public OptionProvider optionProvider()       { return optionProvider;       }
            @Override @Nullable public Object         defaultValue()         { return defaultValue;         }
            @Override @Nullable public Object         overrideDefaultValue() { return overrideDefaultValue; }
        };
    }

    @Nullable private static AnnotationDesc
    xor(
        @Nullable AnnotationDesc rpa1,
        @Nullable AnnotationDesc rpa2,
        SourcePosition           position,
        DocErrorReporter         errorReporter
    ) {

        if (rpa1 == null) {
            return rpa2;
        }

        if (rpa2 == null) {
            return rpa1;
        }

        errorReporter.printError(position, "\"" + rpa1 + "\" and \"" + rpa2 + "\" are mutually exclusive");
        return rpa1;
    }
}
