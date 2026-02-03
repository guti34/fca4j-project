package fr.lirmm.fca4j.cli.io;

import java.io.PrintWriter;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;

public class DatalogRuleExporter implements RuleExporter {

    @Override
    public void export(
            PrintWriter writer,
            Iterable<? extends Implication> implications,
            IBinaryContext context) {

        for (Implication impl : implications) {
            String head = RuleExporterUtils.datalogAtoms(
                    impl.getConclusion(), context);
            String body = RuleExporterUtils.datalogAtoms(
                    impl.getPremise(), context);

            writer.printf("%% support=%d\n%s :- %s.%n", impl.getSupportSize(),head, body);
        }
    }
}
