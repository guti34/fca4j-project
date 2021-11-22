/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.iset.std;

import java.util.BitSet;
import java.util.Iterator;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.AbstractSetFactory.AbstractOrderedSet;
import fr.lirmm.fca4j.iset.ISet;

/**
 *
 * @author agutierr
 */
public class BoolArrayFactory extends AbstractSetFactory {

    public BoolArrayFactory() {
    }

    @Override
    public ISet createSet() {
        throw new UnsupportedOperationException("this kind of set has a fixed size");
    }

    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithBoolArray((BitSet) bs.clone());
    }
    @Override
    public ISet createSet(BitSet bs,int size) {
        return new SetWithBoolArray(bs,size);
    }

    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithBoolArray(initialCapacity);
    }

	@Override
	public boolean ordered() {
		return true;
	}
	@Override
	public boolean fixedSize() {
		return true;
	}
	@Override
	public String name() {
		return "BOOL_ARRAY";
	}
    @Override
    public ISet clone(ISet b) {
        BoolArray bs = (BoolArray) ((SetWithBoolArray) b).boolArray.clone();
        return new SetWithBoolArray(bs);
    }

    class SetWithBoolArray extends AbstractOrderedSet {

        private BoolArray boolArray;

        SetWithBoolArray(int maxSize) {
            boolArray = new BoolArray(maxSize);
        }

        SetWithBoolArray(BoolArray boolArray) {
            this.boolArray = boolArray;
        }
        SetWithBoolArray(BitSet bitSet) {
            this(bitSet,bitSet.size());
        }
        SetWithBoolArray(BitSet bitSet,int size) {
            boolArray = new BoolArray(size);
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                add(i);
            }
        }

        @Override
        public void add(int num) {
            boolArray.add(num);
        }

        @Override
        public void addAll(ISet anotherSet) {
            boolArray.addAll(((SetWithBoolArray) anotherSet).boolArray);
        }

        @Override
        public boolean contains(int num) {
            return boolArray.contains(num);
        }

        @Override

        public boolean containsAll(ISet anotherSet) {
        	return boolArray.containsAll(((SetWithBoolArray) anotherSet).boolArray);
        }
        @Override
        public int capacity() {
            return boolArray.size();
        }

        @Override
        public int cardinality() {
            return boolArray.cardinality();
        }

        @Override
        public void fill(int size) {
            boolArray.fill(size);
        }

        @Override
        public void clear(int size) {
            boolArray.clear(size);
        }

        @Override
        public void removeAll(ISet anotherSet) {
            boolArray.andNot(((SetWithBoolArray) anotherSet).boolArray);
        }

        @Override
        public Iterator<Integer> iterator() {
            return  boolArray.iterator();
    }
        @Override
        public boolean isEmpty() {
            return boolArray.isEmpty();
        }

        @Override
        public ISet newIntersect(ISet anotherSet) {
            BoolArray bs = (BoolArray) boolArray.clone();
            bs.and(((SetWithBoolArray) anotherSet).boolArray);
            return new SetWithBoolArray(bs);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            BoolArray bs = (BoolArray) boolArray.clone();
            bs.andNot(((SetWithBoolArray) anotherSet).boolArray);
            return new SetWithBoolArray(bs);
        }

        @Override
        public void remove(int num) {
            boolArray.remove(num);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            boolArray.and(((SetWithBoolArray) anotherSet).boolArray);
        }

        @Override
        public int first() {
        		return boolArray.first();
        }

        @Override
        public int last() {
        	return boolArray.last;
        }

        @Override
        public int hashCode() {
            return boolArray.array.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            try{
                BoolArray boolArray2=((SetWithBoolArray)other).boolArray;
                if(boolArray.last!=boolArray2.last)
                	return false;
                for(int i=0;i<boolArray.last;i++){
                	if(boolArray.array[i]!=boolArray2.array[i])
                		return false;
                }
                return  true;
            }catch(Exception e){
                return false;
            }
        }
    }

}
