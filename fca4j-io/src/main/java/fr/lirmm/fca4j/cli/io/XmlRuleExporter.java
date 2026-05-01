/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.PrintWriter;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;

public class XmlRuleExporter implements RuleExporter {

    @Override
    public void export(
            PrintWriter writer,
            Iterable<? extends Implication> implications,
            IBinaryContext context) {

        writer.println("<ruleBasis>");

        for (Implication impl : implications) {
            writer.printf("  <rule support=\"%d\">%n", impl.getSupportSize());
            writer.printf("    <premise>%s</premise>%n",
                    RuleExporterUtils.formatAttributes(impl.getPremise(), context));
            writer.printf("    <conclusion>%s</conclusion>%n",
                    RuleExporterUtils.formatAttributes(impl.getConclusion(), context));
            writer.println("  </rule>");
        }

        writer.println("</ruleBasis>");
    }
}
