/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif.impl;

import java.util.BitSet;
import java.util.Iterator;

import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.core.CsrConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.natif.NativeBridge;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * Implémentation native de Lattice_ParallelCbO : délègue au C via
 * {@link NativeBridge#runLatticeCbOFlat} (ordre roaring) ou
 * {@link NativeBridge#runLatticeCbOCsrFlat} (ordre packé/CSR), puis remplit le
 * ConceptOrder à partir du tableau plat (format identique, même consommateur).
 *
 * <p>Le choix d'orientation est fait <b>côté Java</b>, autour du noyau C qui
 * reste inchangé : l'alphabet de Close-by-One étant les attributs du contexte de
 * travail, le coût de canonicité croît avec le nombre d'attributs. Si le contexte
 * a plus d'attributs que d'objets, on énumère sur sa transposée (axe d'attributs
 * plus court) puis on dualise l'ordre obtenu via {@link IConceptOrder#dual} —
 * échange extents/intents et ensembles réduits, inversion du diagramme de Hasse.
 * Le treillis produit est identique ; seul le temps d'énumération change (souvent
 * d'un ordre de grandeur sur les contextes très déséquilibrés).
 *
 * <p>Noyau natif par défaut : packé/CSR (pas d'aller-retour roaring par concept,
 * pas d'intents complets, adjacence CSR). Bascule via la propriété système
 * {@code fca4j.native.lattice.csr} :
 * <ul>
 *   <li>absente ou {@code true} → noyau packé/CSR (défaut) ;</li>
 *   <li>{@code false} → noyau roaring historique (filet de sécurité comparatif).</li>
 * </ul>
 * Pour revenir au roaring : {@code -Dfca4j.native.lattice.csr=false}.
 */
public class NativeLatticeCbO implements AbstractAlgo {

    /** Propriété système ({@code true} par défaut) : {@code false} force le noyau roaring. */
    public static final String PROP_CSR = "fca4j.native.lattice.csr";

    private final IBinaryContext matrix;
    private final ISetFactory    factory;
    private IConceptOrder         order;

    public NativeLatticeCbO(IBinaryContext matrix) {
        this.matrix  = matrix;
        this.factory = matrix.getFactory();
    }

    @Override
    public String getDescription() {
        return "ParallelCbO";
    }

    @Override
    public IConceptOrder getResult() {
        return order;
    }

    @Override
    public void run() {
        IBinaryContext original = matrix;

        // Énumérer sur l'axe d'attributs le plus court. Si attributs > objets, on
        // travaille sur la transposée et on dualise l'ordre à la fin. Sans effet
        // sur le treillis, mais évite l'explosion de la canonicité côté natif.
        boolean transposed = original.getAttributeCount() > original.getObjectCount();
        IBinaryContext work = transposed ? original.transpose() : original;

        int nObj  = work.getObjectCount();
        int nAttr = work.getAttributeCount();
        boolean csr = Boolean.parseBoolean(System.getProperty(PROP_CSR, "true"));

        Chrono chrono = new Chrono("nativepcbo");
        chrono.start("buildMatrix");
        byte[] mat = buildMatrix(work);
        chrono.stop("buildMatrix");

        chrono.start("runLatticeCbOFlat");
        int[] flat = csr
                ? NativeBridge.runLatticeCbOCsrFlat(nObj, nAttr, mat)
                : NativeBridge.runLatticeCbOFlat(nObj, nAttr, mat);
        chrono.stop("runLatticeCbOFlat");

        chrono.start("populateFromFlat");
        // Build the order in the working orientation; dual() remaps it back below.
        order = new CsrConceptOrder("LatticeWithParallelCbO", work, getDescription());
        populateFromFlat(order, flat);
        chrono.stop("populateFromFlat");

        if (transposed) {
            chrono.start("dual");
            order.dual(original);
            chrono.stop("dual");
        }

        System.out.println("native lattice kernel: " + (csr ? "CSR/packed" : "roaring")
                + (transposed ? " (transposed: " + nObj + " x " + nAttr + ")" : ""));
        for (String serie : chrono.getSerieNames())
            System.out.println(serie + ": " + chrono.getResult(serie));
    }

    private void populateFromFlat(IConceptOrder co, int[] flat) {
        if (flat == null || flat.length < 2) return;

        int p = 0;
        int N = flat[p++];
        int E = flat[p++];
        if (N <= 0) return;

        int[] concepts = new int[N];
        for (int i = 0; i < N; i++) concepts[i] = i;

        int[] edges = new int[2 * E];
        for (int i = 0; i < 2 * E; i++) edges[i] = flat[p++];

        BitSet[] bitsets = new BitSet[N * 2];
        for (int c = 0; c < N; c++) {
            BitSet rext = new BitSet();
            int cardRe = flat[p++];
            for (int k = 0; k < cardRe; k++) rext.set(flat[p++]);
            bitsets[c * 2] = rext;
            BitSet rint = new BitSet();
            int cardRi = flat[p++];
            for (int k = 0; k < cardRi; k++) rint.set(flat[p++]);
            bitsets[c * 2 + 1] = rint;
        }

        co.populate(concepts, edges, bitsets);
    }

    static byte[] buildMatrix(IBinaryContext ctx) {
        int nObj  = ctx.getObjectCount();
        int nAttr = ctx.getAttributeCount();
        byte[] m  = new byte[nObj * nAttr];
        for (int o = 0; o < nObj; o++) {
            ISet intent = ctx.getIntent(o);
            for (Iterator it = intent.iterator(); it.hasNext();) {
                int a = ((Integer) it.next()).intValue();
                m[o * nAttr + a] = 1;
            }
        }
        return m;
    }
}
