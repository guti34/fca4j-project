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
package fr.lirmm.fca4j.iset.std;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.AbstractSetFactory.AbstractOrderedSet;
import fr.lirmm.fca4j.iset.ISet;

/**
 *
 * @author agutierr
 */
public class BitSetFactory extends AbstractSetFactory {

    public BitSetFactory() {
    }
    @Override
    public ISet createSet() {
        return new SetWithBitSet();
    }

    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithBitSet((BitSet) bs.clone());
    }

    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithBitSet(initialCapacity);
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
		return "BITSET";
	}
    @Override
    public ISet clone(ISet b) {
        BitSet bs = (BitSet) ((SetWithBitSet) b).bitSet.clone();
        return new SetWithBitSet(bs);
    }

    public class SetWithBitSet extends AbstractOrderedSet {

        private BitSet bitSet;

        SetWithBitSet() {
            bitSet = new BitSet();
        }

        SetWithBitSet(int initialCapacity) {
            bitSet = new BitSet(initialCapacity);
        }

        SetWithBitSet(BitSet bitSet) {
            this.bitSet = bitSet;
        }

        @Override
        public void add(int num) {
            bitSet.set(num);
        }

        @Override
        public void addAll(ISet anotherSet) {
            bitSet.or(((SetWithBitSet) anotherSet).bitSet);
        }

        @Override
        public boolean contains(int num) {
            return bitSet.get(num);
        }

        public boolean containsAll2(ISet anotherSet) {
            BitSet other = ((SetWithBitSet) anotherSet).bitSet;
            for (int i = other.nextSetBit(0); i >= 0; i = other.nextSetBit(i + 1)) {
                if(!bitSet.get(i)) return false;
            }
            return true;
        }
        @Override

        public boolean containsAll(ISet anotherSet) {
            BitSet union = (BitSet) bitSet.clone();
            union.or(((SetWithBitSet) anotherSet).bitSet);
            return union.cardinality() == cardinality();
        }

        public boolean containsAll3(ISet anotherSet) {
            BitSet intersect = (BitSet) bitSet.clone();
            intersect.and(((SetWithBitSet) anotherSet).bitSet);
            return intersect.cardinality() == anotherSet.cardinality();
        }

        @Override
        public int capacity() {
            return bitSet.size();
        }

        @Override
        public int cardinality() {
            return bitSet.cardinality();
        }

        @Override
        public void fill(int size) {
            bitSet.set(0, size);
        }

        @Override
        public void clear(int size) {
            bitSet.clear(0, size);
        }

        @Override
        public void removeAll(ISet anotherSet) {
            bitSet.andNot(((SetWithBitSet) anotherSet).bitSet);
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
                int index = -2;

                @Override
                public boolean hasNext() {
                    if (index == -1) {
                        return false;
                    }
                    if (index == -2) {
                        return bitSet.nextSetBit(0) >= 0;
                    } else {
                        return bitSet.nextSetBit(index + 1) >= 0;
                    }
                }

                @Override
                public Integer next() {
                    if (index == -1) {
                        throw new NoSuchElementException();
                    }
                    if (index == -2) {
                        index = bitSet.nextSetBit(0);
                    } else {
                        index = bitSet.nextSetBit(index + 1);
                    }
                    if (index == -1) {
                        throw new NoSuchElementException();
                    } else {
                        return index;
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();

                }
            };
        }

        @Override
        public boolean isEmpty() {
            return bitSet.isEmpty();
        }

        @Override
        public ISet newIntersect(ISet anotherSet) {
            BitSet bs = (BitSet) bitSet.clone();
            bs.and(((SetWithBitSet) anotherSet).bitSet);
            return new SetWithBitSet(bs);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            BitSet bs = (BitSet) bitSet.clone();
            bs.andNot(((SetWithBitSet) anotherSet).bitSet);
            return new SetWithBitSet(bs);
        }

        @Override
        public void remove(int num) {
            bitSet.set(num, false);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            bitSet.and(((SetWithBitSet) anotherSet).bitSet);
        }

        @Override
        public int first() {
            return bitSet.nextSetBit(0);
        }

        @Override
        public int last() {
            int length = bitSet.length();
            if (length == 0) {
                return -1;
            } else {
                return bitSet.previousSetBit(length - 1);
            }
        }

        @Override
        public int hashCode() {
            return bitSet.hashCode();
        }

        @Override
        public boolean equals(Object aSet) {
            try {
                return bitSet.equals(((SetWithBitSet) aSet).bitSet);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public BitSet toBitSet() {
            return (BitSet) bitSet.clone();
        }

    }

}
