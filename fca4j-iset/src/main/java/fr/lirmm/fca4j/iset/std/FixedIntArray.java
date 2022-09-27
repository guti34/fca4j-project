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

import java.util.Arrays;
import java.util.Iterator;

/**
 * The Class FixedIntArray.
 *
 * @author agutierr
 */
public class FixedIntArray implements Cloneable {

    /** The array. */
    int[] array;
    
    /** The count. */
    int count = 0;

    /**
     * Instantiates a new fixed int array.
     *
     * @param size the size
     */
    public FixedIntArray(int size) {
        array = new int[size];
    }

    /**
     * Contains.
     *
     * @param num the num
     * @return true, if successful
     */
    public boolean contains(int num) {
        return Arrays.binarySearch(array, 0, count, num) >= 0;
    }

    /**
     * Gets the count.
     *
     * @return the count
     */
    public int getCount() {
        return count;
    }

    /**
     * Clear.
     */
    public void clear() {
        count = 0;
    }
    
    /**
     * And.
     *
     * @param second the second
     */
    public void and(FixedIntArray second){
        int newCount=0;
        for(int index2=0,index1=0;index2<second.count&& index1<count;)
        {
            int comp=Integer.compare(array[index1], second.array[index2]);
            if(comp==0){
                array[newCount++]=second.array[index2];
                index1++;
                index2++;
            }else if(comp<0){
                index1++;
            }else{
                index2++;
            }
        }
        count=newCount;
    }
    
    /**
     * And not.
     *
     * @param set2 the set 2
     */
    public void andNot(FixedIntArray set2) {
        for(Iterator<Integer> it=set2.iterator();it.hasNext();)
        {
            remove(it.next());
        }        
    }
    
    /**
     * And not 2.
     *
     * @param set2 the set 2
     */
    public void andNot2(FixedIntArray set2) {
        for(int index2=0,index1=0;index2<set2.count&& index1<count;)
        {
            int comp=Integer.compare(array[index1], set2.array[index2]);
            if(comp==0){ 
            	removeAt(index1);
                 index2++;
            }else if(comp<0){
                index1++;
            }else{
                index2++;
            }
        }
    }
    
    /**
     * Adds the all.
     *
     * @param second the second
     */
    public void addAll(FixedIntArray second){
        int[] first=new int[count];
        System.arraycopy(array, 0, first, 0, count);
        int index=0,indexFirst=0, indexSecond=0;
        while(indexFirst<count || indexSecond<second.count)
        {
            int comp;
            if(indexFirst>=count) comp=1;
            else if(indexSecond>=second.count) comp=-1;
            else comp=Integer.compare(first[indexFirst], second.array[indexSecond]);
            if(comp==0){
                array[index++]=first[indexFirst];
                indexFirst++;
                indexSecond++;
        }
            else if(comp<0){
                array[index++]=first[indexFirst];
                indexFirst++;
            }else{
                array[index++]=second.array[indexSecond];
                indexSecond++;
            }
        }     
        count=index;
    }
    
    /**
     * Adds the.
     *
     * @param num the num
     * @return the int
     */
    public int add(int num) {
        int pos = getPosition(num, 0);
        if (pos < 0) // insertion point
        {
            int insertion_point = -pos - 1;
            System.arraycopy(array, insertion_point, array, insertion_point+1, count - insertion_point);
            array[insertion_point] = num;
            count++;
            return insertion_point;
        } else {
            return pos; // found
        }
    }

    /**
     * Sorted.
     *
     * @return true, if successful
     */
    public boolean sorted() {
        for (int i = 1; i < count; i++) {
            if (array[i - 1] >= array[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the position 2.
     *
     * @param num the num
     * @param startIndex the start index
     * @return the position 2
     */
    public int getPosition2(int num, int startIndex) {
        for (int i = startIndex; i < count ; i++) {
            if (array[i] == num) {
                return i;
            }else if(array[i] > num){
                return -i-1;
            }
        }
        return -count-1;
    }

    /**
     * Gets the position.
     *
     * @param num the num
     * @param startIndex the start index
     * @return the position
     */
    public int getPosition(int num, int startIndex) {
        return Arrays.binarySearch(array, startIndex, count, num);
    }

    /**
     * Removes the at.
     *
     * @param index the index
     */
    public void removeAt(int index) {
        if (index >= 0 && index < count) {
            System.arraycopy(array, index + 1, array, index, count - index - 1);
            count--;
        }
    }
    
    /**
     * Removes the.
     *
     * @param num the num
     */
    public void remove(int num) {
        int index = Arrays.binarySearch(array, 0, count, num);
        if (index >= 0 && index < count) {
            System.arraycopy(array, index + 1, array, index, count - index - 1);
            count--;
        }
    }

    /**
     * Fill.
     *
     * @param size the size
     */
    public void fill(int size) {
        for (int i = 0; i < size; i++) {
            array[i] = i;
        }
        count = size;
    }

    /**
     * Iterator.
     *
     * @return the iterator
     */
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            int counter = 0;

            @Override
            public boolean hasNext() {
                return counter < count;
            }

            @Override
            public Integer next() {
                return array[counter++];
            }
        };
    }

    /**
     * Gets the.
     *
     * @param pos the pos
     * @return the int
     */
    int get(int pos) {
        if (pos >= count) {
            return -1;
        } else {
            return array[pos];
        }
    }

    /**
     * First.
     *
     * @return the int
     */
    int first() {
        return count == 0 ? -1 : array[0];
    }

    /**
     * Last.
     *
     * @return the int
     */
    int last() {
        if (count == 0) {
            return -1;
        } else {
            return array[count - 1];
        }
    }
   
   /**
    * Checks if is subset of.
    *
    * @param other the other
    * @return true, if is subset of
    */
   public boolean isSubsetOf( FixedIntArray other)
    {
        int thisIndex = 0, otherIndex = 0; 
        if (count > other.count)
            return false; 
        while (otherIndex < other.count && thisIndex < count) {
            if (array[thisIndex] > other.array[otherIndex])
                otherIndex++;
            else if (array[thisIndex] == other.array[otherIndex]) {
                thisIndex++;
                otherIndex++;
            }
            else if (array[thisIndex] < other.array[otherIndex])
                return false;
        }
 
        return (thisIndex == count);
    }

    /**
     * Clone.
     *
     * @return the fixed int array
     */
    @Override
    public FixedIntArray clone() {
        FixedIntArray clone = new FixedIntArray(array.length);
        clone.count = count;
        System.arraycopy(array, 0, clone.array, 0, count);
        return clone;
    }
}
