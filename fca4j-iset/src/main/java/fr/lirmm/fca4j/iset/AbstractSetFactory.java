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
 *
 * @author agutierr
 */
public abstract class AbstractSetFactory implements ISetFactory {


    @Override
    public abstract ISet clone(ISet to_clone);

    @Override
    public ISet createSet(BitSet bs, int size) {
        return createSet(bs);
    }

    public abstract class AbstractOrderedSet extends AbstractSet implements IOrderedSet{
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
    public abstract class AbstractSet implements ISet {

        @Override
        public abstract boolean equals(Object other);

        @Override
        public ISetFactory getFactory() {
            return AbstractSetFactory.this;
        }


        @Override
        public final ISet clone() {
            return AbstractSetFactory.this.clone(this);
        }

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
        @Override
        public BitSet toBitSet() {
            BitSet bs=new BitSet();
            for(Iterator<Integer> it=iterator();it.hasNext();)
                bs.set(it.next());
           return bs;
        }
        @Override
        public List<Integer> toList() {
            return toList(null);
        }
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
