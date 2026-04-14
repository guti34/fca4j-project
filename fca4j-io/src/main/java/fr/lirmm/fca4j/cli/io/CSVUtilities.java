package fr.lirmm.fca4j.cli.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CSVUtilities {
	/**
	 * Auto-détection du séparateur CSV.
	 * - Ignore les zones entre guillemets (quoted fields)
	 * - Valide le choix sur quelques lignes de données après l'en-tête
	 */
	public static char detectSeparator(File csvFile) throws IOException {
	    final char[] candidates = {';', ',', '\t'};
	    final int MAX_DATA_LINES = 5; // lignes de données à lire pour validation

	    List<String> lines = new ArrayList<>();
	    try (BufferedReader reader = new BufferedReader(
	            new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
	        String l;
	        // en-tête + quelques lignes de données
	        while ((l = reader.readLine()) != null && lines.size() < 1 + MAX_DATA_LINES) {
	            if (!l.trim().isEmpty()) {
	                lines.add(l);
	            }
	        }
	    }

	    if (lines.isEmpty()) {
	        return ';'; // défaut
	    }

	    // --- Phase 1 : choix initial sur l'en-tête ---
	    String header = lines.get(0);
	    char best = chooseBest(header, candidates);

	    // --- Phase 2 : validation sur les lignes de données ---
	    if (lines.size() > 1) {
	        int headerCount = countOutsideQuotes(header, best);
	        if (headerCount == 0) {
	            return best; // un seul champ, rien à valider
	        }

	        int consistent = 0;
	        int dataLines = 0;
	        for (int i = 1; i < lines.size(); i++) {
	            int c = countOutsideQuotes(lines.get(i), best);
	            dataLines++;
	            if (c == headerCount) {
	                consistent++;
	            }
	        }

	        // Si moins de la moitié des lignes sont cohérentes, on retente
	        // avec les autres candidats
	        if (consistent < (dataLines + 1) / 2) {
	            best = reEvaluateWithData(lines, candidates, best);
	        }
	    }

	    return best;
	}

	/**
	 * Choisit le séparateur dont le nombre d'occurrences (hors guillemets)
	 * est le plus élevé dans une ligne.
	 */
	private static char chooseBest(String line, char[] candidates) {
	    char best = candidates[0];
	    int bestCount = -1;
	    for (char sep : candidates) {
	        int n = countOutsideQuotes(line, sep);
	        if (n > bestCount) {
	            bestCount = n;
	            best = sep;
	        }
	    }
	    return best;
	}

	/**
	 * Réévalue en tenant compte de la cohérence en-tête / données.
	 * Retourne le candidat avec le meilleur score de cohérence,
	 * en départageant par le nombre d'occurrences dans l'en-tête.
	 */
	private static char reEvaluateWithData(List<String> lines, char[] candidates, char excluded) {
	    String header = lines.get(0);
	    char best = excluded;
	    int bestScore = -1;
	    int bestHeaderCount = -1;

	    for (char sep : candidates) {
	        int headerCount = countOutsideQuotes(header, sep);
	        if (headerCount == 0) continue;

	        int consistent = 0;
	        for (int i = 1; i < lines.size(); i++) {
	            if (countOutsideQuotes(lines.get(i), sep) == headerCount) {
	                consistent++;
	            }
	        }

	        // Priorité : cohérence, puis nombre de colonnes
	        if (consistent > bestScore
	                || (consistent == bestScore && headerCount > bestHeaderCount)) {
	            bestScore = consistent;
	            bestHeaderCount = headerCount;
	            best = sep;
	        }
	    }
	    return best;
	}

	/**
	 * Compte les occurrences d'un caractère dans une ligne
	 * en ignorant les zones entre guillemets doubles (RFC 4180).
	 * Gère les guillemets échappés ("").
	 */
	private static int countOutsideQuotes(String line, char target) {
	    int count = 0;
	    boolean inQuotes = false;

	    for (int i = 0; i < line.length(); i++) {
	        char c = line.charAt(i);
	        if (c == '"') {
	            if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
	                i++; // guillemet échappé "", on saute
	            } else {
	                inQuotes = !inQuotes;
	            }
	        } else if (!inQuotes && c == target) {
	            count++;
	        }
	    }
	    return count;
	}
}
