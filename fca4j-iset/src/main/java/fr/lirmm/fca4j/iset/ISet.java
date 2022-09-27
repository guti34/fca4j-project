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


import java.util.BitSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * The Interface ISet describes a collection of positive integer that contains
 * no duplicate elements
 *
 * @author Alain
 */
public interface ISet extends Cloneable {

    /**
     * Adds the specified integer to this set if it is not already present
     * (optional operation).
     *
     * @param num integer to be added to this set
     */
    public void add(int num);

    /**
     * Adds all of the integers in the specified collection to this set if
     * they're not already present (optional operation).
     *
     * @param anotherSet set containing integers to be added to this set
     * @see #add(int)
     */
    public void addAll(ISet anotherSet);

    /**
     * Returns true if this set contains the specified integer.
     *
     * @param num integer whose presence in this set is to be tested
     * @return true if this set contains the specified integer
     */
    public boolean contains(int num);

    /**
     * Returns true if this set contains all of the integer of the
     * specified set.
     *
     * @param anotherSet collection to be checked for containment in this set
     * @return true if this set contains all of the integer of the
     * specified collection
     * @see #contains(int)
     */
    public boolean containsAll(ISet anotherSet);

    /**
     * Capacity of the collection
     *
     * @return the capacity of this set
     */
    public int capacity();

    /**
     * Returns the number of elements in this set (its cardinality).
     *
     * @return the number of elements in this set (its cardinality)
     */
    public int cardinality();

    /**
     * Fill this set with integers from 0 to size-1.
     *
     * @param size the number of integers to add
     */
    public void fill(int size);

    /**
     * Removes the first specified number of integers from this set.
     *
     * @param size the number of integers to remove
     */
    public void clear(int size);

    /**
     * Removes the specified integer from this set if it is present (optional
     * operation).
     * 
     * @param num the integer value to remove
     */
    public void remove(int num);

    /**
     * Removes from this set all of its integers that are contained in the
     * specified collection (optional operation). This operation effectively
     * modifies this set so that its value is the <i>difference</i> of the two
     * sets.
     *
     * @param anotherSet set containing integers to be removed from this set
     * @see #remove(int)
     * @see #contains(int)
     */
    public void removeAll(ISet anotherSet);

    /**
     * Retains only the integers in this set that are contained in the specified
     * collection (optional operation). In other words, removes from this set
     * all of its integers that are not contained in the specified collection.
     * This operation modifies this set so that its value is the
     *  <i>intersection</i> of the two sets.
     *
     * @param anotherSet collection containing integers to be retained in this
     * set
     * @see #remove(int)
     */
    public void retainAll(ISet anotherSet);

    /**
     * Iterator.
     *
     * @return the iterator
     */
    public Iterator<Integer> iterator();

    /**
     * Returns true if this set contains no elements.
     *
     * @return true if this set contains no elements
     */
    public boolean isEmpty();

    /**
     * Equals.
     *
     * @param aSet the a set
     * @return true, if successful
     */
    @Override
    public boolean equals(Object aSet);

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString();

    /**
     * New intersection between this set and the set specified as parameter.
     * Unlike retainAll the set remains unchanged, a copy of the results is
     * returned
     *
     * @param anotherSet the another set
     * @return the result of the intersection
     */
    public ISet newIntersect(ISet anotherSet);

    /**
     * New difference between this set and the set specified as parameter.
     * Unlike removeAll the set remains unchanged, a copy of the results is
     * returned
     *
     * @param anotherSet the another set
     * @return the result of the difference
     */
    public ISet newDifference(ISet anotherSet);

    /**
     *
     * @return a clone
     */
    public ISet clone();

    /**
     * Returns the factory which creates this set
     *
     * @return the factory
     */
    public ISetFactory getFactory();

    /**
     * create a BitSet copy of the set
     *
     * @return a BitSet
     */
    public BitSet toBitSet();

    /**
     * Copy integers contained in this set in a List
     * Only IOrderedSet guarantees that the list is ordered in natural order
     *
     * @return a List of integers
     */
    public List<Integer> toList();

    /**
     * Copy integers contained in this set in a List sorted with the specified
     * comparator
     *
     * @param comparator
     * @return a sorted List of integers
     */
    public List<Integer> toList(Comparator<Integer> comparator);

    /**
     * First element
     *
     * @return first integer in set or {@code -1} if there is no such element
     *
     */
    public int first();

}
