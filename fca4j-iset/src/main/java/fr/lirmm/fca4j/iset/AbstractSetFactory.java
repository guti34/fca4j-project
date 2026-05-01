/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
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
