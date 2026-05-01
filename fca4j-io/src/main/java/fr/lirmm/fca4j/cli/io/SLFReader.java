/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

/**
 * The Class SLFReader.
 */
public class SLFReader {

	/**
	 * Read.
	 *
	 * @param file the file
	 * @return the i binary context
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static IBinaryContext read(File file) throws IOException {
		return read(file, new BitSetFactory());
	}

	/**
	 * Read.
	 *
	 * @param file the file
	 * @param factory the factory
	 * @return the i binary context
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static IBinaryContext read(File file, ISetFactory factory) throws IOException {

		IBinaryContext binRel = null;
		int nbObj = 0;
		int nbAtt = 0;
		String rel = "";
		BufferedReader buff = new BufferedReader(new FileReader(file));
		// La ligne 1 doit contenir [Lattice]
		if (!buff.readLine().trim().equalsIgnoreCase("[Lattice]")) {
			throw new IOException("Attempt to read SLF format failed.");
		}

		// Les 2 lignes, qui suivent, contiennent le nombre d'objet et le nombre
		// d'attributs
		nbObj = Integer.parseInt(buff.readLine().trim());
		nbAtt = Integer.parseInt(buff.readLine().trim());

		binRel = new BinaryContext(nbObj, nbAtt, "SLF_Context", factory);

		// La ligne suivante contient la mention [Objects]
		if (!buff.readLine().trim().equalsIgnoreCase("[Objects]")) {
			throw new IOException("Attempt to read SLF format failed.");
		}

		// Les lignes qui suivent doivent contenir des nom d'Objets
		String nomObj = null;
		boolean names_truncated = false;
		for (int i = 0; i < nbObj; i++) {
			nomObj = buff.readLine().trim();
			if (nomObj.trim().equalsIgnoreCase("[Attributes]")) {
				names_truncated = true;
				break;
			}
			binRel.addObjectName(nomObj);
		}

		if (!names_truncated) {
			// La ligne suivante contient la mention [Attributes]
			if (!buff.readLine().trim().equalsIgnoreCase("[Attributes]")) {
				throw new IOException("Attempt to read SLF format failed.");
			}
		}
		names_truncated = false;
		// Les lignes qui suivent doivent contenir des nom d'Attributs
		String nomAtt = null;
		for (int i = 0; i < nbAtt; i++) {
			nomAtt = buff.readLine().trim();
			if (nomAtt.trim().equalsIgnoreCase("[relation]")) {
				names_truncated = true;
				break;
			}
			binRel.addAttributeName(nomAtt);
		}

		if (!names_truncated) {
			// La ligne suivante contient la mention [Relation]
			if (!buff.readLine().trim().equalsIgnoreCase("[relation]")) {
				throw new IOException("Attempt to read SLF format failed.");
			}
		}
		// lecture de la relation binaire
		int i = 0, j = 0, r;
		while ((r = buff.read()) != -1 && i < nbObj) {
			if ((char) r == '0') {
				binRel.set(i, j, false);
				j++;
				if (j == nbAtt) {
					i++;
					j = 0;
				}
			}
			if ((char) r == '1') {
				binRel.set(i, j, true);
				j++;
				if (j == nbAtt) {
					i++;
					j = 0;
				}
			}
		}

		buff.close();

		return binRel;
	}
}
