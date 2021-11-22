/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.iset.std;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.ISet;

/**
 *
 * @author agutierr
 */
public class ArrayListSetFactory extends AbstractSetFactory {

    public ArrayListSetFactory() {
    }

    @Override
    public ISet createSet() {
        return new SetWithArrayList();
    }

    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithArrayList((BitSet) bs.clone());
    }

    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithArrayList(initialCapacity);
    }

	@Override
	public boolean ordered() {
		return true;
	}
	@Override
	public boolean fixedSize() {
		return false;
	}
	@Override
	public String name() {
		return "ARRAYLIST";
	}
    @Override
    public ISet clone(ISet b) {
        ArrayList<Integer> clone = new ArrayList<>();
        clone.addAll(((SetWithArrayList) b).collection);
        return new SetWithArrayList(clone);
    }

    class SetWithArrayList extends AbstractOrderedSet {

        private ArrayList<Integer> collection;

        SetWithArrayList() {
            collection = new ArrayList<>();
        }

        SetWithArrayList(int initialCapacity) {
            collection = new ArrayList<>(initialCapacity);
        }

        SetWithArrayList(ArrayList<Integer> coll) {
            this.collection = coll;
            Collections.sort(collection);
        }

        SetWithArrayList(BitSet bitSet) {

            this.collection = new ArrayList<>(bitSet.cardinality());
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                add(i);
            }
        }

        @Override
        public void add(int num) {
            int index = Collections.binarySearch(collection, num);
            if (index < 0) {
                collection.add(-index - 1, num);
            }
        }
        
        @Override
        public void addAll(ISet anotherSet) {
            for (Integer toAdd : ((SetWithArrayList) anotherSet).collection) {
                int index = Collections.binarySearch(collection, toAdd);
                if (index < 0) {
                    collection.add(-index - 1, toAdd);
                }
            }
        }

        @Override
        public boolean contains(int num) {
            return collection.contains(num);
        }

        @Override
        public boolean containsAll(ISet anotherSet) {
            ArrayList<Integer> collection2 = ((SetWithArrayList) anotherSet).collection;
            int index1 = 0, index2 = 0;
            while(collection2.size() != index2&&collection.size() != index1) {                
                    int comp=Integer.compare(collection.get(index1), collection2.get(index2)) ;
                    if(comp==0){
                        index1++;
                        index2++;
                    }
                    else if(comp<0){
                        index1++;
                    }
                    else return false;
            }
            return (collection2.size() == index2);
        }

        @Override
        public int capacity() {
            return collection.size();
        }

        @Override
        public int cardinality() {
            return collection.size();
        }

        public void fill(int size) {
            for (int i = 0; i < size; i++) {
                collection.add(i);
            }
        }

        @Override
        public void clear(int size) {
            for (int i = 0; i < size; i++) {
                collection.remove(i);
            }
        }

        @Override
        public void removeAll(ISet anotherSet) {
            collection.removeAll(((SetWithArrayList) anotherSet).collection);
        }

        @Override
        public Iterator<Integer> iterator() {
            return collection.iterator();
        }

        @Override
        public boolean isEmpty() {
            return collection.isEmpty();
        }
        private void and(ArrayList<Integer> set1, ArrayList<Integer> set2) {
            int count=0;
            for(int index2=0,index1=0;index2<set2.size()&& index1<set1.size();)
            {
                int comp=Integer.compare(set1.get(index1), set2.get(index2));
                if(comp==0){
                    set1.set(count++, set2.get(index2));
                    index1++;
                    index2++;
                }else if(comp<0){
                    index1++;
                }else{
                    index2++;
                }
            }
            for(int i=set1.size()-1;i>=count;i--)
                set1.remove(i);
        }

        @Override
        public ISet newIntersect(ISet anotherSet) {
            ArrayList<Integer> clone = new ArrayList<>(collection.size());
            clone.addAll(collection);
            and(clone, ((SetWithArrayList) anotherSet).collection);
            return new SetWithArrayList(clone);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            ArrayList<Integer> clone = new ArrayList<>(collection.size());
            clone.addAll(collection);
            clone.removeAll(((SetWithArrayList) anotherSet).collection);
            return new SetWithArrayList(clone);
        }

        @Override
        public void remove(int num) {
            collection.remove((Integer) num);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            and(collection, ((SetWithArrayList) anotherSet).collection);
        }

        @Override
        public int hashCode() {
            return collection.hashCode();
        }

        @Override
        public BitSet toBitSet() {
            BitSet bs = new BitSet();
            for (Iterator<Integer> it = collection.iterator(); it.hasNext();) {
                bs.set(it.next());
            }
            return bs;
        }

        @Override
        public boolean equals(Object aSet) {
            try {
                return collection.equals(((SetWithArrayList) aSet).collection);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public int first() {
            if (collection.isEmpty()) {
                return -1;
            } else {
                return collection.get(0);
            }
        }

        @Override
        public int last() {
            if (collection.isEmpty()) {
                return -1;
            } else {
                return collection.get(collection.size() - 1);
            }

        }
    }

}
