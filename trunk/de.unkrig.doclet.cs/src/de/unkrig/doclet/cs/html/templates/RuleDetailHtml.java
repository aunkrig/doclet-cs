
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

import com.sun.javadoc.RootDoc;

import de.unkrig.commons.doclet.html.Html;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.StringUtil;
import de.unkrig.commons.lang.protocol.Consumer;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.commons.util.collections.IterableUtil.ElementWithContext;
import de.unkrig.doclet.cs.CsDoclet.OptionProvider;
import de.unkrig.doclet.cs.CsDoclet.Rule;
import de.unkrig.doclet.cs.CsDoclet.RuleProperty;
import de.unkrig.doclet.cs.CsDoclet.ValueOption;
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

            String detailTitle = property.name() + " = ";

            switch (property.datatype()) {

            case BOOLEAN:
                detailTitle += (
                    defaultValue == null ? "\"true|false" :
                    Boolean.TRUE.equals(defaultValue) ? "\"<u>true</u>|false\"" :
                    "\"true|<u>false</u>\""
                );
                break;

            case MULTI_CHECK:
                {
                    OptionProvider op = property.optionProvider();
                    if (op == null) {
                        rootDoc.printError(property.ref().position(), "Multi-check property lacks the option provider");
                        detailTitle += "???";
                        break;
                    }
                    detailTitle += "\"" + RuleDetailHtml.catValues(
                        op,  // optionProvider
                        (    // defaultValues
                            defaultValue == null
                            ? new Object[0]
                            : ((String) defaultValue).split(",")
                        ),
                        ", " // glue
                    ) + "\"";
                }
                break;

            case REGEX:
                detailTitle += (
                    "<i>"
                    + "<a href=\"http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html\">"
                    + "regular-expression"
                    + "</a>"
                    + "</i>"
                );
                if (defaultValue != null) {
                    detailTitle += " (optional; default value is \"" + defaultValue + "\")";
                }
                break;

            case SINGLE_SELECT:
                {
                    OptionProvider op = property.optionProvider();
                    if (op == null) {
                        rootDoc.printError(
                            property.ref().position(),
                            "Single-select property lacks the option provider"
                        );
                        detailTitle += "???";
                        break;
                    }
                    detailTitle += "\"" + RuleDetailHtml.catValues(
                        op,    // optionProvider
                        (      // defaultValue
                            defaultValue == null
                            ? new Object[0]
                            : new Object[] { defaultValue }
                        ),
                        " | "  // glue
                    ) + "\"";
                }
                break;


            case FILE:
            case HIDDEN:
            case INTEGER:
            case STRING:
                detailTitle += "\"<i>" + property.datatype() + "</i>\"";
                if (defaultValue == null) {
                    detailTitle += " (mandatory)";
                } else {
                    detailTitle += " (optional; default value is " + defaultValue + ")";
                }
                break;
            }

            String propertyLongDescription = property.longDescription();
            if (property.optionProvider() != null) {
                propertyLongDescription += "<p>Default values are <u>underlined</u>.</p>";
                propertyLongDescription += "<p>For a description of the individual values, click them.</p>";
            }

            SectionItem propertyItem = new SectionItem();

            propertyItem.anchor            = property.name();
            propertyItem.summaryTableCells = new String[] { property.name(), property.shortDescription() };
            propertyItem.detailTitle       = detailTitle;
            propertyItem.detailContent     = propertyLongDescription;

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

//    /**
//     * @param optionProvider Either an enum type, or an implementation of {@link IOptionProvider}; mutually exclusive
//     *                       with non-{@code null} <var>valueOptions</var>
//     * @param valueOptions   The list of possible values; mutually exclusive with a non-{@code null}
//     *                       <var>optionProvider</var>
//     * @param html TODO
//     * @return               The value options for the given setter method; may contain HTML markup
//     */
//    private static String[]
//    valueOptions(
//        SourcePosition     position,
//        @Nullable ClassDoc optionProvider,
//        @Nullable String[] valueOptions,
//        final RootDoc      rootDoc,
//        Html               html
//    ) throws Longjump {
//
//        String[] result;
//        if (optionProvider == null) {
//            if (valueOptions == null) {
//                rootDoc.printError(position, "Both option provider and value options are missing");
//                throw new Longjump();
//            }
//            result = valueOptions;
//        } else
//        if (valueOptions != null) {
//            rootDoc.printError(position, "Option provider and value options are mutually exclusive");
//            throw new Longjump();
//        } else
//        if (optionProvider.isEnum()) {
//
//            // "optionProvider" is an ENUM type.
//            List<String> tmp = new ArrayList<String>();
//            for (FieldDoc fd : optionProvider.enumConstants()) {
//                if (fd.isEnumConstant()) {
//
//                    String s = fd.name().toLowerCase();
//
//                    Tag[] inlineTags = fd.inlineTags();
//                    if (inlineTags != null && inlineTags.length > 0) {
//                        String htmlText = html.fromTags(inlineTags, rootDoc, rootDoc);
//                        s = "<span title=\"" + NoTemplate.html(htmlText) + "\">" + s + "</span>";
//                    }
//                    tmp.add(s);
//                }
//            }
//            result = tmp.toArray(new String[0]);
//        } else
//        if (optionProvider.subclassOf(
//            Docs.classNamed(rootDoc, "net.sf.eclipsecs.core.config.meta.IOptionProvider")
//        )) {
//
//            // "optionProvider" extends "IOptionProvider".
//            Class<?> opc = Types.loadType(position, optionProvider, rootDoc);
//
//            List<?> tmp;
//            try {
//                tmp = (List<?>) opc.getDeclaredMethod("getOptions").invoke(opc.newInstance());
//            } catch (Exception e) {
//                rootDoc.printError(position, e.getMessage()); // SUPPRESS CHECKSTYLE AvoidHidingCause
//                throw new Longjump();
//            }
//            result = tmp.toArray(new String[0]);
//        } else
//        {
//            rootDoc.printError(position, (
//                ""
//                + "Option provider class '"
//                + optionProvider
//                + "' must either extend 'Enum' or implement 'IOptionProvider'"
//            ));
//            throw new Longjump();
//        }
//
//        return result;
//    }

    /**
     * Concatenate the given {@code values}, separated with {@code glue}, and underline the value which equals the
     * {@code defaultValue}.
     */
    private static String
    catValues(OptionProvider optionProvider, @Nullable Object[] defaultValues, String glue) {

        ValueOption[] valueOptions = optionProvider.valueOptions();
        assert valueOptions.length >= 1;

        Set<Object> dvs = new HashSet<Object>();
        for (Object o : defaultValues) dvs.add(o.toString());

        StringBuilder sb  = new StringBuilder();
        for (int i = 0;;) {
            ValueOption vo = valueOptions[i];
            sb.append("<a href=\"../option-providers/").append(optionProvider.className()).append(".html#");
            sb.append(vo.name()).append("_detail\">");
            if (dvs.contains(vo.name())) {
                sb.append("<u>").append(vo.name()).append("</u>");
            } else {
                sb.append(vo.name());
            }
            sb.append("</a>");
            if (++i == valueOptions.length) break;
            sb.append(glue);
        }
        return sb.toString();
    }
}
