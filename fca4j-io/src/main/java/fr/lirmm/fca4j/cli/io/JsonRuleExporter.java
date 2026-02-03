package fr.lirmm.fca4j.cli.io;

import java.io.PrintWriter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;

public class JsonRuleExporter implements RuleExporter {

    @Override
    public void export(
            PrintWriter writer,
            Iterable<? extends Implication> implications,
            IBinaryContext context) {

        JSONArray rules = new JSONArray();

        for (Implication impl : implications) {
            JSONObject rule = new JSONObject();
            rule.put("support", impl.getSupportSize());
            rule.put("premise",
                    RuleExporterUtils.attributesAsJsonArray(impl.getPremise(), context));
            rule.put("conclusion",
                    RuleExporterUtils.attributesAsJsonArray(impl.getConclusion(), context));
            rules.add(rule);
        }

        writer.print(rules.toJSONString());
    }
}
