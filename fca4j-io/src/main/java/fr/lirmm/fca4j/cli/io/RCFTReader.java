package fr.lirmm.fca4j.cli.io;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.GZIPInputStream;

import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

public class RCFTReader {
    public static RCAFamily read(String filePath,boolean compressed)  throws Exception {
        return read(filePath,new BitSetFactory(),compressed);
    }
    public static RCAFamily read(String filePath,ISetFactory factory,boolean compressed) throws Exception {
        MyParseRcft parser = new MyParseRcft();
        Reader reader= compressed ? new InputStreamReader(new GZIPInputStream(new FileInputStream(filePath))):new InputStreamReader(new FileInputStream(filePath));
        return parser.parse(new BufferedReader(reader),factory);
    }
}
