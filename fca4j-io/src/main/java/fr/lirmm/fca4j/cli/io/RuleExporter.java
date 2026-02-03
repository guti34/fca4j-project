package fr.lirmm.fca4j.cli.io;

import java.io.PrintWriter;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;

public interface RuleExporter {

    void export(
            PrintWriter writer,
            Iterable<? extends Implication> implications,
            IBinaryContext context
    );
}
