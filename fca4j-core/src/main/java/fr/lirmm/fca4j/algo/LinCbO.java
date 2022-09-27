/*
BSD 3-Clause License

Copyright (c) 2022 LIRMM
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class LinCbO.
 *
 * @author agutierr
 */
// compute duquenne guigues rules
public class LinCbO extends AbstractLinCbo {

    
    /**
     * Instantiates a new lin cb O.
     *
     * @param binCtx the bin ctx
     * @param chrono the chrono
     * @param computeIntExtStrategy the compute int ext strategy
     * @param clarify the clarify
     */
    public LinCbO(IBinaryContext binCtx, Chrono chrono,ClosureStrategy computeIntExtStrategy,boolean clarify) {
        super(binCtx,chrono,computeIntExtStrategy,clarify);
    }

    /**
     * Instantiates a new lin cb O.
     *
     * @param binCtx the bin ctx
     */
    public LinCbO(IBinaryContext binCtx) {
        this(binCtx, null,new ClosureDirect(binCtx),false);
    }

    /**
     * Inits the.
     */
    @Override
    protected void init() {
        implications = new ArrayList<>();
        defaultConclusion = null;
        list = new ArrayList<>(matrix.getAttributeCount());
    }


    /**
     * Lin cb O.
     *
     * @throws InterruptedException the interrupted exception
     */
    protected void _LinCbO()  throws InterruptedException{
        for (int attr = 0; attr < matrix.getAttributeCount(); attr++) {
            list.add(new ArrayList(matrix.getObjectCount()));
        }
        List<Integer> count = new ArrayList<>();
        _LinCbOStep(factory.createSet(matrix.getAttributeCount()), -1, factory.createSet(matrix.getAttributeCount()), count,null,null);
    }

    /**
     * Lin cb O step.
     *
     * @param B the b
     * @param y the y
     * @param Z the z
     * @param prevCount the prev count
     * @param lastAttrSet the last attr set
     * @param lastExtent the last extent
     * @throws InterruptedException the interrupted exception
     */
    protected void _LinCbOStep(ISet B, int y, ISet Z, List<Integer> prevCount, ISet lastAttrSet,ISet lastExtent) throws InterruptedException{
                if (Thread.interrupted())  throw new InterruptedException("interrupted by user");

        Pair<List<Integer>,ISet> retClosureRC = _LinClosureRC(B, y, Z, prevCount);
        if (retClosureRC == null) {
            return;
        }
        List<Integer> count = retClosureRC.left;
        ISet Bo = retClosureRC.right;
        ISet Bclosure=factory.createSet(matrix.getAttributeCount());
        ISet support= closure(Bclosure,Bo,lastAttrSet,lastExtent);
        if (!Bo.equals(Bclosure)) {
            int num_implication = addImplication(Bo, Bclosure, support);
            for (Iterator<Integer> it = Bo.iterator(); it.hasNext();) {
                int numattr = it.next();
                list.get(numattr).add(num_implication);
            }
            if (equalsUntil(Bo, Bclosure, y)) {
                ISet Zp = Bclosure.newDifference(Bo);
                _LinCbOStep(Bclosure, y, Zp, count,lastAttrSet,lastExtent);
            }
        } else {
            for (int i = matrix.getAttributeCount() - 1; i > y; i--) {
                if (!Bo.contains(i)) {
                    ISet Bp = factory.clone(Bo);
                    Bp.add(i);
                    ISet Zp = factory.createSet(matrix.getAttributeCount());
                    Zp.add(i);
                    _LinCbOStep(Bp, i, Zp, count, Bclosure,support);
                }
            }
        }
    }
    
    /**
     * Lin closure RC.
     *
     * @param B the b
     * @param y the y
     * @param Z the z
     * @param prevCount the prev count
     * @return the pair
     */
    protected Pair<List<Integer>,ISet> _LinClosureRC(ISet B, int y, ISet Z, List<Integer> prevCount) {
        ISet D = factory.clone(B);
        if (defaultConclusion != null) {
            D.addAll(defaultConclusion);
        }
        List<Integer> count = new ArrayList<>(prevCount);
        for (int num_implication = 0; num_implication < implications.size(); num_implication++) {
            if (count.size() == num_implication) {
                Implication implication = implications.get(num_implication);
                count.add(implication.getPremise().newDifference(B).cardinality());
            }
        }
        while (!Z.isEmpty()) {
            int m = min(Z);
            Z.remove(m);
            for (Iterator<Integer> it = list.get(m).iterator(); it.hasNext();) {
                int num_implication = it.next();
                Implication implication = implications.get(num_implication);
                count.set(num_implication, count.get(num_implication) - 1);
                if (count.get(num_implication) == 0) {
                    if(min(implication.getConclusion())<y && min(D)>=y)
                        return null;
                    ISet add = implication.getConclusion().newDifference(D);
                    if (min(add) < y) {
                        return null;
                    }
                    D.addAll(add);
                    Z.addAll(add);
                }
            }
        }
        return new Pair(count, D);
    }
    
    /**
     * Adds the implication.
     *
     * @param premise the premise
     * @param conclusion the conclusion
     * @param support the support
     * @return the int
     */
    protected int addImplication(ISet premise, ISet conclusion, ISet support) {
        Implication newImplication = new Implication(premise, conclusion, support);
        int num_implication = implications.size();
        implications.add(newImplication);
        if (newImplication.getPremise().isEmpty()) {
            if (defaultConclusion == null) {
                defaultConclusion = factory.createSet(matrix.getAttributeCount());
            }
            defaultConclusion.addAll(newImplication.getConclusion());
        }
        computeIntExt.notify(newImplication);
        return num_implication;
    }
    
    /**
     * Gets the description.
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "LinCbO";
    }
    
    /**
     * The Class Pair.
     *
     * @param <L> the generic type
     * @param <R> the generic type
     */
    public class Pair<L,R>{

        L left;
        R right;

        /**
         * Instantiates a new pair.
         *
         * @param left the left
         * @param right the right
         */
        public Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }

    }

}
