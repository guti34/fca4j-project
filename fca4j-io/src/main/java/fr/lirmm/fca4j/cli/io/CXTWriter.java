/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;

import fr.lirmm.fca4j.core.IBinaryContext;

/**
 * The Class CXTWriter.
 *
 * @author agutierr
 */
public class CXTWriter {


    /**
     * Write context.
     *
     * @param writer the writer
     * @param context the context
     * @throws Exception the exception
     */
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
