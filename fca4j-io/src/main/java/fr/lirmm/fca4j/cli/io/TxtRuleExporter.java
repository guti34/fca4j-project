package fr.lirmm.fca4j.cli.io;

import java.io.PrintWriter;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;

public class TxtRuleExporter implements RuleExporter {

    @Override
    public void export(
            PrintWriter writer,
            Iterable<? extends Implication> implications,
            IBinaryContext context) {

        for (Implication impl : implications) {
            writer.printf("<%d> %s => %s%n",
                    impl.getSupportSize(),
                    RuleExporterUtils.formatAttributes(impl.getPremise(), context),
                    RuleExporterUtils.formatAttributes(impl.getConclusion(), context));
        }
    }
}
