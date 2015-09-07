
/*
 * de.unkrig.doclet.cs - A doclet which generates metadata documents for a CheckStyle extension
 *
 * Copyright (c) 2015, Arno Unkrig
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

package de.unkrig.doclet.cs.html.templates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.eclipsecs.core.config.meta.IOptionProvider;

import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SourcePosition;

import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.StringUtil;
import de.unkrig.commons.lang.protocol.Consumer;
import de.unkrig.commons.lang.protocol.Longjump;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.util.collections.IterableUtil.ElementWithContext;
import de.unkrig.doclet.cs.CsDoclet.Rule;
import de.unkrig.doclet.cs.CsDoclet.RuleProperty;
import de.unkrig.notemplate.javadocish.IndexPages;
import de.unkrig.notemplate.javadocish.IndexPages.IndexEntry;
import de.unkrig.notemplate.javadocish.Options;
import de.unkrig.notemplate.javadocish.templates.AbstractDetailHtml;
import de.unkrig.notemplate.javadocish.templates.AbstractRightFrameHtml;

/**
 * Renderer for the "per-rule" documentation document.
 */
public
class RuleDetailHtml extends AbstractDetailHtml {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    /**
     * Renderer for the "per-rule" documentation document.
     */
    public void
    render(
        final ElementWithContext<Rule> ruleTriplet,
        final Html                     html,
        final RootDoc                  rootDoc,
        Options                        options,
        String                         indexLink,
        Consumer<? super IndexEntry>   indexEntries
    ) {

        final Rule previousRule = ruleTriplet.previous();
        final Rule rule         = ruleTriplet.current();
        final Rule nextRule     = ruleTriplet.next();

        // Index entry for rule.
        {
            String ruleLink = rule.familyPlural() + "/" + rule.ref().name().replace('.', '/');
            indexEntries.consume(IndexPages.indexEntry(
                rule.name(),            // key
                ruleLink,               // link
                "Rule",                 // explanation
                rule.shortDescription() // shortDescription
            ));
        }

        List<SectionItem> propertyItems = new ArrayList<SectionItem>();
        for (RuleProperty property : rule.properties()) {

            Object defaultValue;
            {
                Object tmp = property.defaultValue();
                if (tmp == null) tmp = property.overrideDefaultValue();
                defaultValue = tmp;
            }

            String nav = property.name() + " = ";

            switch (property.datatype()) {

            case BOOLEAN:
                nav += "\"" + RuleDetailHtml.catValues(
                    new String[] { "true", "false" },                      // values
                    defaultValue == null ? null : defaultValue.toString(), // defaultValue
                    " | "                                                  // glue
                ) + "\"";
                break;

            case MULTI_CHECK:
                try {
                    nav += "\"" + RuleDetailHtml.catValues(
                        RuleDetailHtml.valueOptions( // values
                            property.ref().position(), // position
                            property.optionProvider(), // optionProvider
                            property.valueOptions(),   // valueOptions
                            rootDoc                    // docErrorReporter
                        ),
                        (                            // defaultValues
                            defaultValue == null ? new Object[0] : ((String) defaultValue).split(",")
                        ),
                        ", "                         // glue
                    ) + "\"";
                } catch (Longjump l) {
                    nav += "???";
                }
                break;

            case REGEX:
                nav += (
                    "\"''[http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html#sum "
                    + property.datatype()
                    + "]''\""
                );
                if (defaultValue != null) {
                    nav += " (optional; default value is \"" + defaultValue + "\")";
                }
                break;

            case SINGLE_SELECT:
                try {
                    nav += "\"" + RuleDetailHtml.catValues(
                        RuleDetailHtml.valueOptions( // values
                            property.ref().position(), // position
                            property.optionProvider(), // optionProvider
                            property.valueOptions(),   // valueOptions
                            rootDoc                    // docErrorReporter
                        ),
                        defaultValue,                // defaultValue
                        " | "                        // glue
                    ) + "\"";
                } catch (Longjump l) {
                    nav += "???";
                }
                break;


            case FILE:
            case HIDDEN:
            case INTEGER:
            case STRING:
                nav += "\"<i>" + property.datatype() + "</i>\"";
                if (defaultValue == null) {
                    nav += " (mandatory)";
                } else {
                    nav += " (optional; default value is " + defaultValue + ")";
                }
                break;
            }

            SectionItem propertyItem = new SectionItem();

            propertyItem.anchor            = property.name();
            propertyItem.summaryTableCells = new String[] { property.name(), property.shortDescription() };
            propertyItem.detailTitle       = nav;
            propertyItem.detailContent     = property.longDescription();

            propertyItems.add(propertyItem);

            // Index entry for rule property.
            {
                String ruleLink     = rule.familyPlural() + "/" + rule.ref().name().replace('.', '/');
                String propertyLink = ruleLink + "#property_" + property.name();
                indexEntries.consume(IndexPages.indexEntry(
                    property.name(),                                                         // key
                    propertyLink,                                                            // link
                    "Property of rule <a href=\"" + ruleLink + "\">" + rule.name() + "</a>", // explanation
                    property.shortDescription()                                              // shortDescription
                ));
            }
        }

        Section propertiesSection = new Section();
        propertiesSection.anchor               = "property";
        propertiesSection.navigationLinkLabel  = "Props";
        propertiesSection.summaryTitle1        = "Property Summary";
        propertiesSection.summaryTitle2        = "Properties";
        propertiesSection.summaryTableHeadings = new String[] { "Name", "Description" };
        propertiesSection.detailTitle          = "Property Detail";
        propertiesSection.items                = propertyItems;

        String familyCap = StringUtil.firstLetterToUpperCase(rule.familySingular());
        super.rDetail(
            familyCap + " \"" + rule.name() + "\"",                     // windowTitle
            options,                                                    // options
            new String[] { "../stylesheet.css", "../stylesheet2.css" }, // stylesheetLinks
            new String[] {                                              // nav1
                "Overview",   "../overview-summary.html",
                familyCap,    AbstractRightFrameHtml.HIGHLIT,
                "Deprecated", "../deprecated-list.html",
                "Index",      "../" + indexLink,
                "Help",       "../help-doc.html",
            },
            new String[] {                                                // nav2
                previousRule == null ? "Prev " + familyCap : "<a href=\"" + previousRule.simpleName() + ".html\"><span class=\"typeNameLink\">Prev " + familyCap + "</span></a>",
                nextRule     == null ? "Next " + familyCap : "<a href=\"" +     nextRule.simpleName() + ".html\"><span class=\"typeNameLink\">Next " + familyCap + "</span></a>",
            },
            new String[] {                                                // nav3
                "Frames",    "../index.html?" + rule.familyPlural() + "/" + rule.simpleName() + ".html",
                "No Frames", "#top",
            },
            new String[] {                                                // nav4
                "All Rules", "../allrules-noframe.html",
            },
            null,                                                         // subtitle
            familyCap + " \"" + rule.name() + "\"",                       // title
            () -> {                                                       // prolog
                RuleDetailHtml.this.l(
"  <div class=\"description\">"
                );
                this.l(rule.longDescription());
                RuleDetailHtml.this.l(
"  </div>"
                );
            },
            Collections.singletonList(propertiesSection)
        );
    }

    /**
     * @return The value options for the given setter method
     */
    private static String[]
    valueOptions(
        SourcePosition         position,
        @Nullable Class<?>     optionProvider,
        @Nullable String[]     valueOptions,
        final DocErrorReporter docErrorReporter
    ) throws Longjump {

        String[] result;
        if (optionProvider == null) {
            if (valueOptions == null) {
                docErrorReporter.printError(position, "Both option provider and value options are missing");
                throw new Longjump();
            }
            result = valueOptions;
        } else
        if (valueOptions != null) {
            docErrorReporter.printError(position, "Option provider and value options are mutually exclusive");
            throw new Longjump();
        } else
        {

            if (optionProvider.getSuperclass() == Enum.class) {
                Object[] tmp;
                try {
                    tmp = (Object[]) optionProvider.getDeclaredMethod("values").invoke(null);
                } catch (Exception e) {
                    docErrorReporter.printError(position, e.getMessage()); // SUPPRESS CHECKSTYLE AvoidHidingCause
                    throw new Longjump();
                }
                result = new String[tmp.length];
                for (int i = 0; i < tmp.length; i++) {
                    result[i] = ((Enum<?>) tmp[i]).name().toLowerCase();
                }
            } else
            if (IOptionProvider.class.isAssignableFrom(optionProvider)) {
                List<?> tmp;
                try {
                    tmp = (List<?>) optionProvider.getDeclaredMethod("getOptions").invoke(optionProvider.newInstance());
                } catch (Exception e) {
                    docErrorReporter.printError(position, e.getMessage()); // SUPPRESS CHECKSTYLE AvoidHidingCause
                    throw new Longjump();
                }
                result = tmp.toArray(new String[0]);
            } else
            {
                docErrorReporter.printError(position, (
                    ""
                    + "Option provider class '"
                    + optionProvider
                    + "' must either extend 'Enum' or implement 'IOptionProvider'"
                ));
                throw new Longjump();
            }
        }
        return result;
    }

    /**
     * Concatenate the given {@code values}, separated with {@code glue}, and underline the value which equals the
     * {@code defaultValue}.
     */
    private static String
    catValues(String[] values, @Nullable Object defaultValue, String glue) {

        return RuleDetailHtml.catValues(
            values,
            defaultValue == null ? new Object[0] : new Object[] { defaultValue }, glue
        );
    }

    /**
     * Concatenate the given {@code values}, separated with {@code glue}, and underline the values which are also
     * contained in {@code defaultValue}.
     */
    private static String
    catValues(String[] values, Object[] defaultValues, String glue) {
        assert values.length >= 1;

        Set<Object>   dvs = new HashSet<Object>();
        for (Object o : defaultValues) dvs.add(o.toString());

        StringBuilder sb  = new StringBuilder();
        for (int i = 0;;) {
            String value = values[i];
            if (dvs.contains(value)) {
                sb.append("<u>" + value + "</u>");
            } else {
                sb.append(value);
            }
            if (++i == values.length) break;
            sb.append(glue);
        }
        return sb.toString();
    }
}
