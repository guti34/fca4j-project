/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif.impl;

import java.util.BitSet;
import java.util.Iterator;

import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.natif.NativeBridge;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * Implémentation native de Lattice_ParallelCbO : délègue au C via
 * {@link NativeBridge#runLatticeCbOFlat}, puis remplit le ConceptOrder à partir
 * du tableau plat (même format et même consommateur que NativeLatticeAddExtent).
 */
public class NativeLatticeCbO implements AbstractAlgo {

    private final IBinaryContext matrix;
    private final ISetFactory    factory;
    private ConceptOrder         order;

    public NativeLatticeCbO(IBinaryContext matrix) {
        this.matrix  = matrix;
        this.factory = matrix.getFactory();
    }

    @Override
    public String getDescription() {
        return "ParallelCbO";
    }

    @Override
    public ConceptOrder getResult() {
        return order;
    }

    @Override
    public void run() {
        int nObj  = matrix.getObjectCount();
        int nAttr = matrix.getAttributeCount();
        Chrono chrono=new Chrono("nativepcbo");
        chrono.start("buildMatrix");
        byte[] mat  = buildMatrix(matrix);
        chrono.stop("buildMatrix");
         chrono.start("runLatticeCbOFlat");
       int[]  flat = NativeBridge.runLatticeCbOFlat(nObj, nAttr, mat);
         chrono.stop("runLatticeCbOFlat");

        chrono.start("populateFromFlat");
         order = new ConceptOrder("LatticeWithParallelCbO", matrix, getDescription());
        populateFromFlat(order, flat);
        chrono.stop("populateFromFlat");
        for(String serie: chrono.getSerieNames())
        	System.out.println(serie+": "+chrono.getResult(serie));
    }

    private void populateFromFlat(ConceptOrder co, int[] flat) {
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