/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Engendre des familles de contextes SLF à nombre d'objets croissant, pour
 * distinguer un écart algorithmique d'un coût d'implémentation.
 *
 * <p><b>La question posée.</b> Sur ord5bikesharing (717 objets) Hermes met 3,0
 * fois le temps de Ceres ; sur ord6magic04 (19020 objets) il en met 12,3 fois,
 * pour un résultat identique — mêmes concepts, mêmes arêtes. Un algorithme qui
 * fait simplement plus de travail donnerait un facteur à peu près STABLE. Un
 * facteur qui croît avec le nombre d'objets désigne autre chose : un coût par
 * objet là où il ne devrait pas y en avoir.
 *
 * <p>C'est exactement la signature des deux défauts trouvés pendant la campagne
 * Ceres : {@code getUpperCover} allouait un ensemble de |G|+|A| bits pour n'en
 * lire que la cardinalité, et la sérialisation testait |G| bits par concept pour
 * n'en trouver qu'une poignée. Les deux vivent dans l'infrastructure partagée,
 * donc Hermes et Pluton peuvent parfaitement en payer autant.
 *
 * <p><b>Le principe.</b> Faire varier |G| en gardant le reste constant. Si le
 * nombre de concepts croît en même temps que le nombre d'objets, on ne saura pas
 * attribuer la pente observée. D'où deux familles :
 *
 * <ul>
 *   <li><b>dup</b> — un jeu de lignes de base RÉPLIQUÉ jusqu'à la taille voulue.
 *       Le nombre de concepts et d'arêtes reste rigoureusement identique d'une
 *       taille à l'autre : seuls les extents grossissent. Toute pente observée
 *       ici est du coût par objet, jamais du travail supplémentaire. C'est la
 *       famille qui tranche.</li>
 *   <li><b>rnd</b> — contextes aléatoires à densité et |A| fixes, |G| croissant.
 *       Le nombre de concepts croît aussi. Sert de contrôle, pour vérifier qu'on
 *       ne conclut pas sur un artefact de la duplication.</li>
 * </ul>
 *
 * <p>Les lignes de base de la famille dupliquée sont deux à deux distinctes :
 * deux objets de même intension seraient fusionnés par la clarification interne
 * de Hermes et de Pluton, mais pas par Ceres ni Ares, et la comparaison
 * porterait alors sur des quantités de travail différentes.
 *
 * <p>Usage :
 * <pre>
 *   java -cp ...jar fr.lirmm.fca4j.main.ScalingGen &lt;dossier&gt; \
 *        [-attrs 52] [-density 0.30] [-sizes 1000,2000,4000,8000,16000] \
 *        [-base 250] [-seed 20260812]
 * </pre>
 */
public class ScalingGen {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("usage: ScalingGen <dossier> [-attrs N] [-density D]"
                    + " [-sizes a,b,c] [-base N] [-seed N]");
            return;
        }
        File outDir = new File(args[0]);
        int attrs = 52;
        double density = 0.30;
        int baseRows = 250;
        long seed = 20260812L;
        int[] sizes = {1000, 2000, 4000, 8000, 16000};

        for (int i = 1; i < args.length - 1; i++) {
            if ("-attrs".equals(args[i])) {
                attrs = Integer.parseInt(args[++i]);
            } else if ("-density".equals(args[i])) {
                density = Double.parseDouble(args[++i]);
            } else if ("-base".equals(args[i])) {
                baseRows = Integer.parseInt(args[++i]);
            } else if ("-seed".equals(args[i])) {
                seed = Long.parseLong(args[++i]);
            } else if ("-sizes".equals(args[i])) {
                String[] parts = args[++i].split(",");
                sizes = new int[parts.length];
                for (int k = 0; k < parts.length; k++) {
                    sizes[k] = Integer.parseInt(parts[k].trim());
                }
            }
        }

        if (!outDir.exists() && !outDir.mkdirs()) {
            System.out.println("impossible de creer " + outDir);
            return;
        }
        Random rng = new Random(seed);

        // ── lignes de base, deux à deux distinctes ──────────────────────────
        Set<String> seen = new HashSet<>();
        boolean[][] base = new boolean[baseRows][];
        int got = 0;
        long guard = 0;
        while (got < baseRows && guard < (long) baseRows * 1000L) {
            guard++;
            boolean[] row = new boolean[attrs];
            StringBuilder key = new StringBuilder(attrs);
            for (int j = 0; j < attrs; j++) {
                row[j] = rng.nextDouble() < density;
                key.append(row[j] ? '1' : '0');
            }
            if (seen.add(key.toString())) {
                base[got++] = row;
            }
        }
        if (got < baseRows) {
            System.out.println("attention : seulement " + got + " lignes distinctes obtenues"
                    + " (augmenter -attrs ou baisser -base)");
            boolean[][] shrunk = new boolean[got][];
            System.arraycopy(base, 0, shrunk, 0, got);
            base = shrunk;
            baseRows = got;
        }

        StringBuilder listDup = new StringBuilder();
        StringBuilder listRnd = new StringBuilder();

        for (int n : sizes) {
            boolean[][] rows = new boolean[n][];
            for (int i = 0; i < n; i++) {
                rows[i] = base[i % baseRows];
            }
            File f = new File(outDir, "dup_" + n + "x" + attrs + ".slf");
            writeSlf(f, rows, attrs);
            listDup.append('"').append(f.getAbsolutePath()).append("\" ");
            System.out.println("dup  " + n + " x " + attrs + " -> " + f.getName());
        }

        for (int n : sizes) {
            boolean[][] rows = new boolean[n][];
            for (int i = 0; i < n; i++) {
                boolean[] row = new boolean[attrs];
                for (int j = 0; j < attrs; j++) {
                    row[j] = rng.nextDouble() < density;
                }
                rows[i] = row;
            }
            File f = new File(outDir, "rnd_" + n + "x" + attrs + ".slf");
            writeSlf(f, rows, attrs);
            listRnd.append('"').append(f.getAbsolutePath()).append("\" ");
            System.out.println("rnd  " + n + " x " + attrs + " -> " + f.getName());
        }

        writeText(new File(outDir, "liste_dup.txt"), listDup.toString().trim());
        writeText(new File(outDir, "liste_rnd.txt"), listRnd.toString().trim());
        System.out.println("\nlistes ecrites : liste_dup.txt, liste_rnd.txt");
    }

    /** Format SLF tel que le lit SLFReader. */
    private static void writeSlf(File f, boolean[][] rows, int attrs) throws IOException {
        BufferedWriter w = new BufferedWriter(new FileWriter(f), 1 << 20);
        w.write("[Lattice]\n");
        w.write(rows.length + "\n");
        w.write(attrs + "\n");
        w.write("[Objects]\n");
        for (int i = 0; i < rows.length; i++) {
            w.write("o" + i + "\n");
        }
        w.write("[Attributes]\n");
        for (int j = 0; j < attrs; j++) {
            w.write("a" + j + "\n");
        }
        w.write("[relation]\n");
        for (boolean[] row : rows) {
            for (int j = 0; j < attrs; j++) {
                w.write(row[j] ? '1' : '0');
                w.write(' ');
            }
            w.write('\n');
        }
        w.flush();
        w.close();
    }

    private static void writeText(File f, String s) throws IOException {
        PrintWriter pw = new PrintWriter(new FileWriter(f));
        pw.print(s);
        pw.close();
    }
}
