package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.zip.GZIPOutputStream;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;

public class RCFTWriter {
	
    public static File write(RCAFamily rcf, String outputPath, boolean compressed) throws IOException {
        Writer fw;
        File f = new File(outputPath);
        if (compressed) {
            fw = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(new FileOutputStream(f))));
        } else {
            fw = new BufferedWriter(new FileWriter(f, false));
        }
        for (FormalContext fc : rcf.getFormalContexts()) {
            writeFC(fw, fc.getContext());
        }
        for (RelationalContext rc : rcf.getRelationalContexts()) {
            writeRC(fw, rc.getContext(), rc.getRelationName(), rc.getOperator().getName(), rcf.getSourceOf(rc).getName(), rcf.getTargetOf(rc).getName());
        }
        fw.close();
        return f;
    }

    /**
     * Ecrit le contexte formel dans le fichier rcft
     */
    private static void writeFC(Writer writer, IBinaryContext context) throws IOException {
        //ecriture de l'en-tete du contexte formel
        writer.write("FormalContext " + context.getName() + "\n"
                + "||");

        //ecriture de la premiere ligne du contexte, cad les attributs
        for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
            writer.write(context.getAttributeName(numattr) + "|");
        }
        writer.write("\n");

        for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
            //remplissage de l'intention de l'objet
            String ret = "|";
            ret += context.getObjectName(numobj) + "|";
            for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
                if (context.get(numobj, numattr)) {
                    ret += "x|";
                } else {
                    ret += "|";
                }
            }
            ret += "\n";
            writer.write(ret);
        }
        writer.write("\n");
    }

    /**
     * Ecrit le contexte formel dans le fichier rcft
     */
    private static void writeRC(Writer writer, IBinaryContext context, String rname, String op, String source, String target) throws IOException {
        //ecriture de l'en-tete du contexte relationnel
        writer.write("RelationalContext " + rname + "\n"
                + "source " + source + "\n"
                + "target " + target + "\n"
                + "scaling " + op + "\n"
                + "||");

        //ecriture de la premiere ligne du contexte, cad les attributs
        for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
            writer.write(context.getAttributeName(numattr) + "|");
        }
        writer.write("\n");
        StringBuffer buffer = new StringBuffer();
        for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
            //remplissage de l'intention de l'objet
            String ret = "|";
            ret += context.getObjectName(numobj) + "|";
            for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
                if (context.get(numobj, numattr)) {
                    ret += "x|";
                } else {
                    ret += "|";
                }
            }
            ret += "\n";
            buffer.append(ret);
        }
        writer.write(buffer.toString());
        writer.write("\n");
    }

}
