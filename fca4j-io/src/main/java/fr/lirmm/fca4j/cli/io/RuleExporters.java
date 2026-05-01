/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

public final class RuleExporters {

    private RuleExporters() {}

    public static RuleExporter fromFormat(String format) {
        switch (format.toUpperCase()) {
            case "TXT" : return new TxtRuleExporter();
            case "JSON"  : return new JsonRuleExporter();
            case "XML" : return new XmlRuleExporter();
            case "DATALOG" : return new DatalogRuleExporter();
            default: throw new IllegalArgumentException(
                    "Unknown output format: " + format);
        }
    }
}
