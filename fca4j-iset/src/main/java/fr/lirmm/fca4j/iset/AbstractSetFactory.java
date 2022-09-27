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
package fr.lirmm.fca4j.iset;
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * A factory for creating AbstractSet objects.
 *
 * @author agutierr
 */
public abstract class AbstractSetFactory implements ISetFactory {


    /**
     * Clone.
     *
     * @param to_clone the set to clone
     * @return the cloned set
     */
    @Override
    public abstract ISet clone(ISet to_clone);

    /**
     * Creates a new AbstractSet object.
     *
     * @param bs the bitset
     * @param size the size
     * @return the new set
     */
    @Override
    public ISet createSet(BitSet bs, int size) {
        return createSet(bs);
    }

    /**
     * The Class AbstractOrderedSet.
     */
    public abstract class AbstractOrderedSet extends AbstractSet implements IOrderedSet{
        
        /**
         * Last.
         *
         * @return the last element
         */
        @Override
        public int last() {
            Iterator<Integer> it = iterator();
            int last = -1;
            while (it.hasNext()) {
                int n = it.next();
                if(n>last) last=n;
            }
            return last;
        }        
    }
    
    /**
     * The Class AbstractSet.
     */
    public abstract class AbstractSet implements ISet {

        /**
         * Equals.
         *
         * @param other the other
         * @return true, if successful
         */
        @Override
        public abstract boolean equals(Object other);

        /**
         * Gets the factory.
         *
         * @return the factory
         */
        @Override
        public ISetFactory getFactory() {
            return AbstractSetFactory.this;
        }


        /**
         * Clone.
         *
         * @return the cloned set
         */
        @Override
        public final ISet clone() {
            return AbstractSetFactory.this.clone(this);
        }

        /**
         * To string.
         *
         * @return the string
         */
        @Override
        public final String toString() {
            String s = null;
            for (Iterator<Integer> it = iterator(); it.hasNext();) {
                if (s == null) {
                    s = "[" + it.next();
                } else {
                    s += "," + it.next();
                }
            }
            return s == null ? "[]" : s + "]";
        }
        
        /**
         * convert to bitset.
         *
         * @return the bit set
         */
        @Override
        public BitSet toBitSet() {
            BitSet bs=new BitSet();
            for(Iterator<Integer> it=iterator();it.hasNext();)
                bs.set(it.next());
           return bs;
        }
        
        /**
         * convert to list.
         *
         * @return the list
         */
        @Override
        public List<Integer> toList() {
            return toList(null);
        }
        
        /**
         * convert to sorted list.
         *
         * @param comparator the comparator
         * @return the list
         */
        @Override
        public List<Integer> toList(Comparator<Integer> comparator) {
            List<Integer> list=new ArrayList<>(cardinality());
            for(Iterator<Integer> it=iterator();it.hasNext();)
                list.add(it.next());
            if(comparator!=null)
                list.sort(comparator);
            return list;
        }

    }
}
