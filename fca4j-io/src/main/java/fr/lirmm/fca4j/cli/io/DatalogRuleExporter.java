/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.PrintWriter;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.util.DlgpUtils;

public class DatalogRuleExporter implements RuleExporter {

    @Override
    public void export(
            PrintWriter writer,
            Iterable<? extends Implication> implications,
            IBinaryContext context) {

		String[] attrNames=new String[context.getAttributeCount()];
		
		for(int attr=0;attr<context.getAttributeCount();attr++)
			attrNames[attr]=context.getAttributeName(attr);
       for (Implication impl : implications) {
            String head = DlgpUtils.buildConjunction(
                    impl.getConclusion(), attrNames);
            String body = DlgpUtils.buildConjunction(
                    impl.getPremise(), attrNames);

            writer.printf("%% support=%d\n%s :- %s.%n", impl.getSupportSize(),head, body);
        }
    }
}
