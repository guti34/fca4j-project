/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.iset.hppc;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.cursors.IntCursor;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.AbstractSetFactory.AbstractSet;
import fr.lirmm.fca4j.iset.ISet;

/**
 *
 * @author agutierr
 */
public class HPPCFactory extends AbstractSetFactory {

    public HPPCFactory() {
    }

    @Override
    public ISet createSet() {
        return new SetWithHPPC();
    }

    @Override
    public ISet createSet(BitSet bitset) {
        return new SetWithHPPC(bitset);
    }

    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithHPPC(initialCapacity);
    }

	@Override
	public boolean ordered() {
		return false;
	}
	@Override
	public boolean fixedSize() {
		return false;
	}
	@Override
	public String name() {
		return "HPPC_HASHSET";
	}
    @Override
    public ISet clone(ISet to_clone) {
       IntHashSet bs = (IntHashSet) ((SetWithHPPC)to_clone).hashSet.clone();
        return new SetWithHPPC(bs);
    }
       class SetWithHPPC extends AbstractSet {
           private IntHashSet hashSet;
       SetWithHPPC() {
            hashSet = new IntHashSet();
        }

        SetWithHPPC(int initialCapacity) {
            hashSet = new IntHashSet(initialCapacity);
        }

        SetWithHPPC(IntHashSet hashSet) {
            this.hashSet = hashSet;
        }
        SetWithHPPC(BitSet bitSet) {

            this.hashSet = new IntHashSet();
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                add(i);
            }
        }

        @Override
        public void add(int num) {
            hashSet.add(num);
        }
        @Override
        public void addAll(ISet anotherSet) {
            hashSet.addAll (((SetWithHPPC)anotherSet).hashSet);
        }

        @Override
        public boolean contains(int num) {
            return hashSet.contains(num);
        }
        
        @Override
        public boolean containsAll(ISet anotherSet) {
            for(Iterator<IntCursor> it=((SetWithHPPC) anotherSet).hashSet.iterator();it.hasNext();){
                if(!hashSet.contains(it.next().value)) return false;
            }
            return true;
        }

        @Override
        public int capacity() {
            return hashSet.size();
        }

        @Override
        public int cardinality() {
            return hashSet.size();
        }

   public void fill(int size) {
        for (int i = 0; i < size; i++) {
            hashSet.add(i);
        }
    }


        @Override
    public void clear(int size) {
        for (int i = 0; i < size; i++) {
            hashSet.remove(i);
        }
    }
        @Override
        public void removeAll(ISet anotherSet) {
            hashSet.removeAll(((SetWithHPPC) anotherSet).hashSet);
        }

	public Iterator<Integer> iterator() {
		return new Iterator<Integer>(){
			Iterator<IntCursor> it= hashSet.iterator();
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public Integer next() {
				return it.next().value;
			}

			@Override
			public void remove() {
				it.remove();
			}};
	}

        @Override
        public boolean isEmpty() {
            return hashSet.isEmpty();
        }

        @Override
        public ISet newIntersect(ISet anotherSet) {
            IntHashSet bs = new IntHashSet(hashSet);
            bs.retainAll(((SetWithHPPC) anotherSet).hashSet);
            return new SetWithHPPC(bs);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            IntHashSet bs = new IntHashSet(hashSet);
            bs.removeAll(((SetWithHPPC) anotherSet).hashSet);
            return new SetWithHPPC(bs);
        }

        @Override
        public void remove(int num) {
            hashSet.remove(num);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            hashSet.retainAll(((SetWithHPPC)anotherSet).hashSet);
        }

        @Override
        public int first() {
            try{
            return hashSet.iterator().next().value;
            }catch(NoSuchElementException ex){
                return -1;
            }
        }

        @Override
        public int hashCode() {
            return hashSet.hashCode();
        }
    @Override
    public boolean equals(Object aSet) {
        try{
            return hashSet.equals(((SetWithHPPC)aSet).hashSet);
        }catch(Exception e){
            return false;
        }
    }
        
    }
 
}
