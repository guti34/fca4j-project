/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.iset.std;

import java.util.Arrays;
import java.util.Iterator;

/**
 *
 * @author agutierr
 */
public class FixedIntArray implements Cloneable {

    int[] array;
    int count = 0;

    public FixedIntArray(int size) {
        array = new int[size];
    }

    public boolean contains(int num) {
        return Arrays.binarySearch(array, 0, count, num) >= 0;
    }

    public int getCount() {
        return count;
    }

    public void clear() {
        count = 0;
    }
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
    public void andNot(FixedIntArray set2) {
        for(Iterator<Integer> it=set2.iterator();it.hasNext();)
        {
            remove(it.next());
        }        
    }
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

    public boolean sorted() {
        for (int i = 1; i < count; i++) {
            if (array[i - 1] >= array[i]) {
                return false;
            }
        }
        return true;
    }

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

    public int getPosition(int num, int startIndex) {
        return Arrays.binarySearch(array, startIndex, count, num);
    }

    public void removeAt(int index) {
        if (index >= 0 && index < count) {
            System.arraycopy(array, index + 1, array, index, count - index - 1);
            count--;
        }
    }
    public void remove(int num) {
        int index = Arrays.binarySearch(array, 0, count, num);
        if (index >= 0 && index < count) {
            System.arraycopy(array, index + 1, array, index, count - index - 1);
            count--;
        }
    }

    public void fill(int size) {
        for (int i = 0; i < size; i++) {
            array[i] = i;
        }
        count = size;
    }

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

    int get(int pos) {
        if (pos >= count) {
            return -1;
        } else {
            return array[pos];
        }
    }

    int first() {
        return count == 0 ? -1 : array[0];
    }

    int last() {
        if (count == 0) {
            return -1;
        } else {
            return array[count - 1];
        }
    }
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

    @Override
    public FixedIntArray clone() {
        FixedIntArray clone = new FixedIntArray(array.length);
        clone.count = count;
        System.arraycopy(array, 0, clone.array, 0, count);
        return clone;
    }
}
