/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import fr.lirmm.fca4j.algo.AOC_poset_Ares;
import fr.lirmm.fca4j.algo.AOC_poset_Ceres;
import fr.lirmm.fca4j.algo.AOC_poset_Hermes;
import fr.lirmm.fca4j.algo.AOC_poset_Pluton;
import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.cli.io.CSVUtilities;
import fr.lirmm.fca4j.cli.io.MyCSVReader;
import fr.lirmm.fca4j.cli.io.SLFReader;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.core.natif.FastAOCPosetAres;
import fr.lirmm.fca4j.core.natif.FastAOCPosetCeres;
import fr.lirmm.fca4j.core.natif.FastAOCPosetHermes;
import fr.lirmm.fca4j.core.natif.FastAOCPosetPluton;
import fr.lirmm.fca4j.core.natif.NativeBridge;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * Banc de mesure des constructeurs d'AOC-poset, <b>dans une seule JVM</b>.
 *
 * <p>Raison d'être : le banc en ligne de commande lance un processus par mesure,
 * si bien que chaque exécution paie l'interprétation du code avant compilation
 * JIT. Sur le corpus, ce coût fixe s'est révélé être d'environ 20 ms, quelle que
 * soit la taille du contexte — négligeable sur chess ou ord6magic04, mais un
 * tiers à 36 % du temps mesuré sur Plant et ProtSystem, qui en devenaient
 * inexploitables. Aucune passe de chauffe dans un fichier .bat ne peut corriger
 * cela, puisque la JVM meurt entre deux mesures.
 *
 * <p>Ici, chaque algorithme est exécuté {@code -w} fois pour chauffer puis
 * {@code -r} fois pour mesurer, dans le même processus. Les durées de chauffe
 * sont affichées : elles permettent de vérifier que la stabilisation a bien eu
 * lieu, plutôt que de la supposer. Si la dernière itération de chauffe est
 * encore nettement plus lente que la première mesure, il faut augmenter -w.
 *
 * <p>Le contexte est rechargé depuis le disque avant chaque itération, hors
 * chronomètre, parce que certains algorithmes clarifient et qu'on ne veut pas
 * qu'une itération hérite de l'état laissée par la précédente. L'option
 * {@code -noreload} désactive ce rechargement : plus rapide, mais à n'utiliser
 * qu'après avoir vérifié que les résultats restent identiques d'une itération à
 * l'autre.
 *
 * <p>Usage :
 * <pre>
 *   java -cp fca4j-app-light-....jar fr.lirmm.fca4j.main.AocBench \
 *        -w 5 -r 10 -m BITSET_PACKED -a ARES,HERMES,CERES,PLUTON \
 *        C:\projects\rules\chess\chess.slf C:\projects\monstre\Plant.slf
 * </pre>
 *
 * <p>L'option {@code -native} bascule les quatre algorithmes sur leur
 * implémentation C quand la bibliothèque est chargée. Comparer une colonne
 * native à une colonne Java n'a de sens que si on sait laquelle est laquelle,
 * d'où le moteur affiché pour chaque algorithme.
 */
public class AocBench {

    private static final String[] DEFAULT_ALGOS = {"ARES", "HERMES", "CERES", "PLUTON"};

    public static void main(String[] args) throws Exception {
        int warmup = 5;
        int reps = 10;
        String impl = "BITSET_PACKED";
        boolean reload = true;
        boolean useNative = false;
        String[] algos = DEFAULT_ALGOS;
        List<File> files = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("-w".equals(a)) {
                warmup = Integer.parseInt(args[++i]);
            } else if ("-r".equals(a)) {
                reps = Integer.parseInt(args[++i]);
            } else if ("-m".equals(a)) {
                impl = args[++i];
            } else if ("-a".equals(a)) {
                algos = args[++i].toUpperCase().split(",");
            } else if ("-noreload".equals(a)) {
                reload = false;
            } else if ("-native".equals(a)) {
                useNative = true;
            } else {
                files.add(new File(a));
            }
        }
        if (files.isEmpty()) {
            System.out.println("usage: AocBench [-w N] [-r N] [-m IMPL] [-a A,B,C] [-noreload] contexte...");
            return;
        }

        ISetContext setContext = new SetContextLight();
        ISetFactory factory;
        try {
            factory = setContext.getFactory(impl.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.out.println("implementation inconnue : " + impl
                    + ", repli sur " + setContext.getDefaultImplementation());
            factory = setContext.getDefaultFactory();
        }
        System.out.println("AocBench  implementation=" + factory.name()
                + "  chauffe=" + warmup + "  mesures=" + reps
                + "  rechargement=" + reload
                + "  natif=" + useNative
                + (useNative ? " (bibliotheque " + (NativeBridge.isAvailable() ? "chargee" : "ABSENTE, repli Java") + ")" : ""));
        System.out.println("JVM " + System.getProperty("java.version")
                + "  " + System.getProperty("java.vm.name"));

        for (File f : files) {
            if (!f.exists()) {
                System.out.println();
                System.out.println("contexte introuvable, ignore : " + f);
                continue;
            }
            benchOne(f, factory, algos, warmup, reps, reload, useNative);
        }
    }

    private static void benchOne(File file, ISetFactory factory, String[] algos,
            int warmup, int reps, boolean reload, boolean useNative) throws Exception {

        IBinaryContext shared = load(file, factory);
        System.out.println();
        System.out.println("============================================================");
        System.out.println(" " + file.getName() + "   "
                + shared.getObjectCount() + " x " + shared.getAttributeCount());
        System.out.println("============================================================");

        int refConcepts = -1;
        int refEdges = -1;
        boolean mismatch = false;

        for (String algo : algos) {
            double[] measured = new double[reps];
            int concepts = -1;
            int edges = -1;
            StringBuilder warm = new StringBuilder();

            for (int i = 0; i < warmup + reps; i++) {
                IBinaryContext ctx = reload ? load(file, factory) : shared;
                AbstractAlgo<IConceptOrder> algorithm = create(algo, ctx, useNative);
                long t0 = System.nanoTime();
                algorithm.run();
                long dt = System.nanoTime() - t0;
                IConceptOrder res = algorithm.getResult();
                concepts = res.getConceptCount();
                edges = res.getEdgeCount();
                if (i < warmup) {
                    if (warm.length() > 0) {
                        warm.append(" ");
                    }
                    warm.append(String.format("%.0f", dt / 1e6));
                } else {
                    measured[i - warmup] = dt / 1e6;
                }
            }

            double[] sorted = Arrays.copyOf(measured, measured.length);
            Arrays.sort(sorted);
            double min = sorted[0];
            double med = sorted[sorted.length / 2];
            double max = sorted[sorted.length - 1];

            if (refConcepts < 0) {
                refConcepts = concepts;
                refEdges = edges;
            } else if (concepts != refConcepts || edges != refEdges) {
                mismatch = true;
            }

            System.out.println(String.format("  %-8s [%-6s] min %8.1f  med %8.1f  max %8.1f ms"
                    + "   concepts %d  aretes %d",
                    algo, engineOf(algo, useNative), min, med, max, concepts, edges));
            System.out.println("           chauffe : " + warm + " ms");
        }
        if (mismatch) {
            System.out.println("  ALERTE : concepts ou aretes different selon l'algorithme.");
            System.out.println("           Les durees ne veulent rien dire tant que ce n'est pas resolu.");
        }
    }

    private static IBinaryContext load(File f, ISetFactory factory) throws Exception {
        String name = f.getName().toLowerCase();
        if (name.endsWith(".csv")) {
            char sep = CSVUtilities.detectSeparator(f);
            return MyCSVReader.read(f, sep, factory);
        }
        return SLFReader.read(f, factory);
    }

    @SuppressWarnings("unchecked")
    private static AbstractAlgo<IConceptOrder> create(String algo, IBinaryContext ctx,
            boolean useNative) {
        Chrono chrono = new Chrono("bench");
        if ("ARES".equals(algo)) {
            return useNative ? (AbstractAlgo<IConceptOrder>) FastAOCPosetAres.create(ctx)
                    : new AOC_poset_Ares(ctx, chrono, null, true, true);
        }
        if ("HERMES".equals(algo)) {
            return useNative ? (AbstractAlgo<IConceptOrder>) FastAOCPosetHermes.create(ctx)
                    : new AOC_poset_Hermes(ctx, chrono);
        }
        if ("CERES".equals(algo)) {
            return useNative ? (AbstractAlgo<IConceptOrder>) FastAOCPosetCeres.create(ctx)
                    : new AOC_poset_Ceres(ctx, chrono);
        }
        if ("PLUTON".equals(algo)) {
            return useNative ? (AbstractAlgo<IConceptOrder>) FastAOCPosetPluton.create(ctx)
                    : new AOC_poset_Pluton(ctx, chrono);
        }
        throw new IllegalArgumentException("algorithme inconnu : " + algo);
    }

    /** Moteur réellement utilisé, à afficher : une comparaison n'a de sens que
     * si on sait quelle colonne tourne en C. */
    private static String engineOf(String algo, boolean useNative) {
        if (!useNative) {
            return "java";
        }
        return NativeBridge.isAvailable() ? "natif C" : "java";
    }
}
