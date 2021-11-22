package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;

import fr.lirmm.fca4j.core.IBinaryContext;

/**
 *
 * @author agutierr
 */
public class CXTWriter {


    public static void writeContext(BufferedWriter writer, IBinaryContext context) throws Exception {
        writer.write("B\n");
        writer.write(context.getName() + "\n");
        writer.write(Integer.toString(context.getObjectCount()) + "\n");
        writer.write(Integer.toString(context.getAttributeCount()) + "\n");
        for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
            writer.write(context.getObjectName(numobj) + "\n");
        }
        for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
            writer.write(context.getAttributeName(numattr) + "\n");
        }
        for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
            for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
                if (context.get(numobj, numattr)) {
                    writer.write("X");
                } else {
                    writer.write(".");
                }
            }
            writer.newLine();
        }
        writer.flush();
        writer.close();
    }
}
