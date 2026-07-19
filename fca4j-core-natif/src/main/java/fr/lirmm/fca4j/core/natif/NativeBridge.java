/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Responsable du chargement de la bibliothèque native fca4j_dbasis.
 */
public final class NativeBridge {

    private static final Logger LOG = Logger.getLogger(NativeBridge.class.getName());
    private static final boolean AVAILABLE;

    static {
        AVAILABLE = tryLoad();
    }

    private NativeBridge() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    // ── Déclarations JNI ────────────────────────────────────────────────────

    /**
     * Pipeline DBasis complet en C — utilisé en mode MONO.
     * Retourne un JSON contenant toutes les implications.
     *
     * @param nObjects    nombre d'objets du contexte original
     * @param nAttributes nombre d'attributs du contexte original
     * @param matrix      matrice binaire aplatie row-major
     * @param attrNames   noms des attributs (peut être null)
     * @param minSupport  seuil de support (0 = pas de filtre)
     * @param maxThreads  1 = mono, 0 = auto, >1 = fixé (pthread côté C)
     * @return            JSON de la forme {"algorithm":"DBase","basis":[...]}
     */
    public static native String runDbasis(
            int nObjects, int nAttributes,
            byte[] matrix,
            String[] attrNames,
            int minSupport,
            int maxThreads);
    /**
     * Pipeline DBasis complet en C — variante rapide.
     *
     * <p>Renvoie un tableau d'entiers plat auto-descriptif (indices uniquement,
     * aucun nom, aucune allocation de String côté Java) :
     * <pre>
     *   [0]                      M = nombre d'implications
     *   puis pour chaque implication :
     *       [cardP] p0 p1 ... p(cardP-1)
     *       [cardC] c0 c1 ... c(cardC-1)
     *       [support]
     * </pre>
     * Indices exprimés dans le contexte ORIGINAL (déjà réécrits côté C).
     *
     * @param nObjects    nombre d'objets
     * @param nAttributes nombre d'attributs
     * @param matrix      matrice binaire aplatie row-major
     * @param minSupport  seuil de support (0 = pas de filtre)
     * @param maxThreads  1 = mono, 0 = auto, >1 = fixé (pthread côté C)
     * @return            tableau plat (voir format ci-dessus)
     */
    public static native int[] runDbasisFlat(
            int nObjects, int nAttributes,
            byte[] matrix,
            int minSupport,
            int maxThreads);
    /**
     * Calcule les transversaux minimaux pour un attribut cible — mode MULTITHREAD.
     * Appelé par attribut depuis les threads workers Java.
     *
     * @param nObjects       nombre d'objets
     * @param nAttributes    nombre d'attributs
     * @param contextMatrix  matrice aplatie row-major
     * @param target         attribut cible b
     * @param binaryPremises prémisses binaires de b (peut être null)
     * @return               transversaux minimaux en int[][]
     */
    public static native int[][] computeMinimalGenerators(
            int nObjects,
            int nAttributes,
            byte[] contextMatrix,
            int target,
            int[] binaryPremises);

    /**
     * AOC-poset Hermes en C.
     *
     * @param nObjects    nombre d'objets
     * @param nAttributes nombre d'attributs
     * @param matrix      matrice binaire aplatie row-major
     * @param attrNames   noms des attributs (peut être null)
     * @return            JSON ConceptOrder (co_to_json)
     */
    public static native String runHermes(
            int nObjects, int nAttributes,
            byte[] matrix,
            String[] attrNames);

    /**
     * AOC-poset Hermes en C — variante rapide.
     *
     * <p>Renvoie un tableau d'entiers plat auto-descriptif (mêmes conventions
     * que {@link #runAddExtentFlat}) : aucun nom, indices uniquement, consommé
     * directement par {@code ConceptOrder.populate}.
     *
     * @param nObjects    nombre d'objets
     * @param nAttributes nombre d'attributs
     * @param matrix      matrice binaire aplatie row-major
     * @return            tableau plat (cf. format runAddExtentFlat)
     */
    public static native int[] runHermesFlat(
            int nObjects, int nAttributes,
            byte[] matrix);
    /**
     * AOC-poset Pluton en C.
     *
     * @param nObjects    nombre d'objets
     * @param nAttributes nombre d'attributs
     * @param matrix      matrice binaire aplatie row-major
     * @param attrNames   noms des attributs (peut être null)
     * @return            JSON ConceptOrder (co_to_json)
     */
    public static native String runPluton(
            int nObjects, int nAttributes,
            byte[] matrix,
            String[] attrNames);

    /**
     * AOC-poset Pluton en C — variante rapide.
     *
     * <p>Renvoie un tableau d'entiers plat auto-descriptif (mêmes conventions
     * que {@link #runHermesFlat}) : aucun nom, indices uniquement, consommé
     * directement par {@code ConceptOrder.populate}. La clarification et la
     * substitution sont faites côté C : les indices sont déjà exprimés dans le
     * contexte ORIGINAL.
     *
     * @param nObjects    nombre d'objets
     * @param nAttributes nombre d'attributs
     * @param matrix      matrice binaire aplatie row-major
     * @return            tableau plat (cf. format runAddExtentFlat)
     */
    public static native int[] runPlutonFlat(
            int nObjects, int nAttributes,
            byte[] matrix);

    /**
     * LinCbO en C.
     *
     * @param nObjects    nombre d'objets
     * @param nAttributes nombre d'attributs
     * @param matrix      matrice binaire aplatie row-major
     * @param attrNames   noms des attributs (peut être null)
     * @return            JSON ConceptOrder (co_to_json)
     */
    public static native String runLincbo(
            int nObjects, int nAttributes,
            byte[] matrix,
            String[] attrNames);

    /**
     * Treillis complet AddExtent en C.
     *
     * @param nObjects    nombre d'objets
     * @param nAttributes nombre d'attributs
     * @param matrix      matrice binaire aplatie row-major
     * @param attrNames   noms des attributs (peut être null)
     * @return            JSON ConceptOrder (co_to_json)
     */
    public static native String runAddExtent(
            int nObjects, int nAttributes,
            byte[] matrix,
            String[] attrNames);

    /**
     * Treillis complet Lattice_ParallelCbO en C (Close-by-One sur les extents).
     *
     * @param nObjects    nombre d'objets
     * @param nAttributes nombre d'attributs
     * @param matrix      matrice binaire aplatie row-major
     * @param attrNames   noms des attributs (peut être null)
     * @return            JSON ConceptOrder (co_to_json)
     */
    public static native String runLatticeCbO(
            int nObjects, int nAttributes,
            byte[] matrix,
            String[] attrNames);

    /**
     * Treillis complet Lattice_ParallelCbO en C — variante rapide (int[] plat,
     * même format que {@link #runAddExtentFlat}).
     *
     * @param nObjects    nombre d'objets
     * @param nAttributes nombre d'attributs
     * @param matrix      matrice binaire aplatie row-major
     * @return            tableau plat (cf. format runAddExtentFlat)
     */
    public static native int[] runLatticeCbOFlat(
            int nObjects, int nAttributes,
            byte[] matrix);
    
    /**
     * Treillis complet Lattice_ParallelCbO en C — variante packée/CSR.
     *
     * <p>Même tableau plat que {@link #runLatticeCbOFlat} (consommé à l'identique
     * par {@code CsrConceptOrder.populate}), mais le ConceptOrder n'est jamais
     * matérialisé en roaring côté C : extents packés, aucun intent complet,
     * adjacence CSR. Sélectionné via la propriété {@code fca4j.native.lattice.csr}.
     *
     * @param nObjects    nombre d'objets
     * @param nAttributes nombre d'attributs
     * @param matrix      matrice binaire aplatie row-major
     * @return            tableau plat (cf. format runAddExtentFlat)
     */
    public static native int[] runLatticeCbOCsrFlat(
            int nObjects, int nAttributes,
            byte[] matrix);

    /**
     * Treillis complet AddExtent en C — variante rapide.
     *
     * <p>Renvoie un tableau d'entiers plat auto-descriptif (indices uniquement,
     * aucun nom), bien plus rapide à consommer côté Java que le JSON.
     * Format :
     * <pre>
     *   [0]            N      = nombre de concepts
     *   [1]            E      = nombre d'arêtes
     *   [2 .. 2+2E-1]  edges  = E paires (child, parent)
     *   puis pour chaque concept 0..N-1 :
     *       [card_rextent, o0, o1, ...]
     *       [card_rintent, a0, a1, ...]
     * </pre>
     *
     * @param nObjects    nombre d'objets
     * @param nAttributes nombre d'attributs
     * @param matrix      matrice binaire aplatie row-major
     * @return            tableau plat (voir format ci-dessus)
     */
    public static native int[] runAddExtentFlat(
            int nObjects, int nAttributes,
            byte[] matrix);

    // ── Chargement ──────────────────────────────────────────────────────────

    private static boolean tryLoad() {
        String platform = detectPlatform();
        if (platform == null) {
            LOG.info("fca4j-core-natif : plateforme non reconnue — fallback Java activé.");
            return false;
        }
        String libName      = System.mapLibraryName("fca4j_dbasis");
        String resourcePath = "/native/" + platform + "/" + libName;
        try {
            InputStream in = NativeBridge.class.getResourceAsStream(resourcePath);
            if (in == null) {
                LOG.info("fca4j-core-natif : lib absente (" + resourcePath + ") — fallback activé.");
                return false;
            }
            try {
                Path tmp = Files.createTempFile("fca4j_dbasis_", libName);
                tmp.toFile().deleteOnExit();
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                System.load(tmp.toAbsolutePath().toString());
                LOG.info("fca4j-core-natif : lib native chargée (" + resourcePath + ").");
                return true;
            } finally {
                in.close();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "fca4j-core-natif : erreur I/O — fallback activé.", e);
            return false;
        } catch (UnsatisfiedLinkError e) {
            LOG.log(Level.WARNING, "fca4j-core-natif : lien JNI cassé — fallback activé.", e);
            return false;
        }
    }

    private static String detectPlatform() {
        String os   = System.getProperty("os.name",  "").toLowerCase();
        String arch = System.getProperty("os.arch",  "").toLowerCase();
        String osKey;
        if      (os.contains("linux"))   osKey = "linux";
        else if (os.contains("mac"))     osKey = "macos";
        else if (os.contains("windows")) osKey = "windows";
        else                             return null;
        String archKey;
        if      (arch.equals("amd64")   || arch.equals("x86_64"))  archKey = "x86_64";
        else if (arch.equals("aarch64") || arch.equals("arm64"))    archKey = "aarch64";
        else                                                         return null;
        return osKey + "-" + archKey;
    }
}
