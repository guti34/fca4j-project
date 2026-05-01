/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

/**
 * The Class RCFTReader.
 */
public class RCFTReader {
    
    /**
     * Read.
     *
     * @param filePath the file path
     * @param compressed the compressed
     * @return the RCA family
     * @throws Exception the exception
     */
    public static RCAFamily read(String filePath,boolean compressed)  throws Exception {
        return read(filePath,new BitSetFactory(),compressed);
    }
    
    /**
     * Read.
     *
     * @param filePath the file path
     * @param factory the factory
     * @param compressed the compressed
     * @return the RCA family
     * @throws Exception the exception
     */
    public static RCAFamily read(String filePath,ISetFactory factory,boolean compressed) throws Exception {
        MyParseRcft parser = new MyParseRcft();
        Reader reader = compressed
        	    ? new InputStreamReader(
        	          new GZIPInputStream(new FileInputStream(filePath)), "UTF-8")
        	    : new InputStreamReader(
        	          new FileInputStream(filePath), "UTF-8");
        return parser.parse(new BufferedReader(reader),factory);
    }
}
