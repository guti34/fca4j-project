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
