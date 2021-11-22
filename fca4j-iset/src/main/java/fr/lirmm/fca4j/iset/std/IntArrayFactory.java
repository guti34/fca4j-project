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
public class IntArrayFactory extends AbstractSetFactory {
    public IntArrayFactory() {
    }

    @Override
    public ISet createSet(){
        throw new UnsupportedOperationException("this kind of set has a fixed size");
    }

    @Override
    public ISet createSet(BitSet bitset) {
        return new SetWithIntArray(bitset);
    }
    @Override
    public ISet createSet(BitSet bs,int size) {
        return new SetWithIntArray(bs,size);
    }

    @Override
    public ISet createSet(int maxSize) {
        return new SetWithIntArray(maxSize);
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
		return "INT_ARRAY";
	}
    @Override
    public ISet clone(ISet to_clone) {
        FixedIntArray bs = ((SetWithIntArray) to_clone).fiArray.clone();
        return new SetWithIntArray(bs);
        
    }
    class SetWithIntArray extends AbstractOrderedSet{
        FixedIntArray fiArray;
        SetWithIntArray(int maxSize) {
            fiArray = new FixedIntArray(maxSize);
        }
        SetWithIntArray(FixedIntArray fiArray) {
            this.fiArray = fiArray;
        }

        SetWithIntArray(BitSet bitSet) {
            this(bitSet,bitSet.cardinality());
        }
        SetWithIntArray(BitSet bitSet,int size) {
            fiArray = new FixedIntArray(size);
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                add(i);
            }
        }
        private boolean equals(int[]a,int[]b,int count){
            for(int i=0;i<count;i++)
                if(a[i]!=b[i]) return false;
            return true;
        }
        @Override
        public boolean equals(Object other) {
            try{
                FixedIntArray fiArray2=((SetWithIntArray)other).fiArray;
                return fiArray.count==fiArray2.count  && equals(fiArray.array,fiArray2.array,fiArray.count);
            }catch(Exception e){
                return false;
            }
        }

        @Override
        public void add(int num) {
           fiArray.add(num);
        }

        @Override
        public void addAll(ISet anotherSet) {
            fiArray.addAll(((SetWithIntArray)anotherSet).fiArray);
        }

        @Override
        public boolean contains(int num) {
            return fiArray.contains(num);
        }

        @Override
         public boolean containsAll(ISet anotherSet) {
            FixedIntArray fiArray2=((SetWithIntArray)anotherSet).fiArray;
            int startIndex=0;
            for(int i=0;i<fiArray2.count;i++)
            {
                int pos=fiArray.getPosition(fiArray2.array[i],startIndex);
                if(pos<0)return false;
                else startIndex=pos+1;
            }
            return true;
        }
       public boolean containsAll2(ISet anotherSet) {
            FixedIntArray fiArray2=((SetWithIntArray)anotherSet).fiArray;
            boolean ret=fiArray2.isSubsetOf(fiArray);
            return ret;
        }

        @Override
        public int capacity() {
            return fiArray.array.length;
        }

        @Override
        public int cardinality() {
            return fiArray.getCount();
        }

        @Override
        public void fill(int size) {
            fiArray.fill(size);
        }

        @Override
        public void clear(int size) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void removeAll(ISet anotherSet) {
            fiArray.clear();
        }

        @Override
        public Iterator<Integer> iterator() {
            return fiArray.iterator();
        }

        @Override
        public boolean isEmpty() {
            return fiArray.getCount()==0;
        }

        @Override
        public ISet newIntersect(ISet anotherSet) {
            FixedIntArray clone=fiArray.clone();
            clone.and(((SetWithIntArray) anotherSet).fiArray);
            return new SetWithIntArray(clone);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            FixedIntArray clone=fiArray.clone();
            clone.andNot(((SetWithIntArray) anotherSet).fiArray);
            return new SetWithIntArray(clone);
        }

        @Override
        public void remove(int num) {
            fiArray.remove(num);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            fiArray.and(((SetWithIntArray) anotherSet).fiArray);            
        }
        @Override
        public int hashCode() {
            return fiArray.array.hashCode();
        }

        @Override
        public int first() {
            return fiArray.first();
        }
        @Override
        public int last() {
            return fiArray.last();
        }
        
    }
}
