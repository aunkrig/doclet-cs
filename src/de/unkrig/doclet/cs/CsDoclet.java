
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.eclipsecs.core.config.meta.IOptionProvider;

import org.eclipse.ui.IMarkerResolution2;

import com.sun.javadoc.*;

import de.unkrig.commons.doclet.Annotations;
import de.unkrig.commons.doclet.Types;
import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.lang.protocol.ConsumerWhichThrows;
import de.unkrig.commons.lang.protocol.Longjump;
import de.unkrig.commons.nullanalysis.Nullable;

/**
 * A doclet that creates ECLIPSE-CS metadata files and/or documentation for CheckStyle rules in MediaWiki markup
 * format.
 */
public final
class CsDoclet {

    private static Html html = new Html(Html.STANDARD_LINK_MAKER);

    /**
     * Doclets are never instantiated.
     */
    private CsDoclet() {}

    private static final Pattern SETTER = Pattern.compile("set[A-Z].*");

    public static LanguageVersion languageVersion() { return LanguageVersion.JAVA_1_5; }

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
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/doclet/overview.html">"Doclet
     * Overview"</a>.
     */
    public static boolean
    start(final RootDoc rootDoc) throws IOException {

        // Because "IMarkerResolution2.getLabel()" and "IMarkerResolution2.getDescription()" eventually use "NLS",
        // we have to change the default locale to "ENGLISH", because we want the quickfixes' labels and descriptions
        // in english.
        Locale.setDefault(Locale.ENGLISH);

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

            final Collection<Rule> rules = CsDoclet.rules(classDocs.values(), rootDoc);

            // Generate 'checkstyle-metadata.properties' for the package.
            if (checkstyleMetadataDotPropertiesDir != null) {

                CsDoclet.printToFile(
                    new File(new File(
                        checkstyleMetadataDotPropertiesDir,
                        checkstylePackage.replace('.', File.separatorChar)
                    ), "checkstyle-metadata.properties"),
                    Charset.forName("ISO-8859-1"),
                    new ConsumerWhichThrows<PrintWriter, RuntimeException>() {

                        @Override public void
                        consume(PrintWriter pw) {
                            CheckstyleMetadataDotPropertiesGenerator.generate(rules, pw, rootDoc);
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
                            CheckstyleMetadataDotXmlGenerator.generate(rules, pw, rootDoc);
                        }
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
                    new ConsumerWhichThrows<PrintWriter, IOException>() {

                        @Override public void
                        consume(PrintWriter pw) {
                            MessagesDotPropertiesGenerator.generate(rules, pw, rootDoc);
                        }
                    }
                );
            }

            // Generate MediaWiki markup documents for each rule in the package.
            if (mediawikiDir != null) {

                for (final Rule rule : rules) {
                    try {
                        CsDoclet.printToFile(
                            new File(mediawikiDir, rule.name().replaceAll(":\\s+", " ") + ".mediawiki"),
                            Charset.forName("ISO-8859-1"),
                            new ConsumerWhichThrows<PrintWriter, Longjump>() {

                                @Override public void
                                consume(PrintWriter pw) throws Longjump {
                                    MediawikiGenerator.generate(rule, pw, rootDoc);
                                }
                            }
                        );
                    } catch (Longjump l) {
                        ;
                    }
                }
            }
        }

        return true;
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

        /** @return The doc comment to which this rule is related */
        Doc ref();

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

        /** @return The verbose description of this rule; may contain HTML markup */
        String description();

        /** @return The properties of this rule */
        Collection<RuleProperty> properties();

        /** @return The quickfixes which are related to this rule */
        RuleQuickfix[] quickfixes();

        /** @return Whether this rule has a severity; typically checks do, and filters don't */
        @Nullable Boolean hasSeverity();

        /** @return The default localized messages of this rule */
        SortedMap<String, String> messages();
    }

    /**
     * Derives a collection of CheckStyle rules from the given {@code classDocs}.
     */
    public static Collection<Rule>
    rules(final Collection<ClassDoc> classDocs, RootDoc rootDoc) {

        List<Rule> rules = new ArrayList<CsDoclet.Rule>();
        for (final ClassDoc classDoc : classDocs) {

            AnnotationDesc ra = Annotations.get(classDoc, "Rule");
            if (ra == null) continue;

            try {

                rules.add(CsDoclet.rule(ra, classDoc, rootDoc));
            } catch (Longjump l) {
                ; // SUPPRESS CHECKSTYLE AvoidHidingCause
            }
        }

        return rules;
    }

    /**
     * Parses a CheckStyle rule.
     */
    private static Rule
    rule(AnnotationDesc ruleAnnotation, final ClassDoc classDoc, RootDoc rootDoc) throws Longjump {

        // CHECKSTYLE LineLength:OFF
        final String   group        = Annotations.getElementValue(ruleAnnotation, "group",        String.class);
        final String   groupName    = Annotations.getElementValue(ruleAnnotation, "groupName",    String.class);
        final String   name         = Annotations.getElementValue(ruleAnnotation, "name",         String.class);
        final String   parent       = Annotations.getElementValue(ruleAnnotation, "parent",       String.class);
        final Object[] avs          = Annotations.getElementValue(ruleAnnotation, "quickfixes",   Object[].class);
        final Boolean  hasSeverity  = Annotations.getElementValue(ruleAnnotation, "hasSeverity",  Boolean.class);
        // CHECKSTYLE LineLength:ON

        assert group     != null;
        assert groupName != null;
        assert name      != null;
        assert parent    != null;

        final String internalName = classDoc.qualifiedTypeName();

        final String simpleName = classDoc.simpleTypeName();

        final String description = CsDoclet.html.fromTags(classDoc.inlineTags(), classDoc, rootDoc);

        final Collection<RuleProperty> properties = CsDoclet.properties(classDoc, rootDoc);

        final RuleQuickfix[] quickfixes;
        if (avs == null) {
            quickfixes = new RuleQuickfix[0];
        } else {
            quickfixes = new RuleQuickfix[avs.length];
            for (int i = 0; i < avs.length; i++) {
                final Type quickfixType = (Type) avs[i];

                Class<?> qfc = Types.loadType(classDoc.position(), quickfixType, rootDoc);

                IMarkerResolution2 mr2;
                try {
                    mr2 = (IMarkerResolution2) qfc.newInstance();
                } catch (Exception e) {
                    rootDoc.printError(classDoc.position(), "Instantiating quickfix '" + qfc + "':" + e);
                    throw new Longjump(); // SUPPRESS CHECKSTYLE AvoidHidingCause
                }

                final String quickfixClassName   = quickfixType.qualifiedTypeName();
                final String quickfixLabel       = mr2.getLabel();
                final String quickfixDescription = mr2.getDescription();

                quickfixes[i] = new RuleQuickfix() {
                    @Override public String className()   { return quickfixClassName;   }
                    @Override public String label()       { return quickfixLabel;       }
                    @Override public String description() { return quickfixDescription; }
                };
            }
        }

        final SortedMap<String, String> messages = new TreeMap<String, String>();
        for (FieldDoc fd : classDoc.fields(false)) {

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

            messages.put(messageKey, message);
        }


        return new Rule() {
            @Override public Doc                       ref()          { return classDoc;     }
            @Override public String                    group()        { return group;        }
            @Override public String                    groupName()    { return groupName;    }
            @Override public String                    simpleName()   { return simpleName;   }
            @Override public String                    name()         { return name;         }
            @Override public String                    internalName() { return internalName; }
            @Override public String                    parent()       { return parent;       }
            @Override public String                    description()  { return description;  }
            @Override public Collection<RuleProperty>  properties()   { return properties;   }
            @Override public RuleQuickfix[]            quickfixes()   { return quickfixes;   }
            @Override @Nullable public Boolean         hasSeverity()  { return hasSeverity;  }
            @Override public SortedMap<String, String> messages()     { return messages;     }
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
    interface RuleQuickfix {

        /** @return The (fully qualified) class name of the implementation */
        String className();

        /** @return The 'label' of the quickfix, as returned by {@link IMarkerResolution2#getLabel()} */
        String label();

        /** @return The 'description' of the quickfix, as returned by {@link IMarkerResolution2#getDescription()} */
        String description();
    }

    /**
     * Invokes {@link RulePropertyHandler#handeRuleProperty(String, SourcePosition, String, String, String, String,
     * Class, AnnotationValue[], Object, Object)} for each property of the rule designated by {@code classDoc}
     */
    public static Collection<RuleProperty>
    properties(ClassDoc classDoc, RootDoc rootDoc) throws Longjump {

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
            final String intertitle = CsDoclet.html.optionalTag(methodDoc, "@cs-intertitle", rootDoc);

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
            final String shortDescription = CsDoclet.html.fromTags(methodDoc.firstSentenceTags(), methodDoc, rootDoc);
            final String longDescription  = CsDoclet.html.fromTags(methodDoc.inlineTags(),        methodDoc, rootDoc);

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
