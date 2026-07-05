package com.mediqueue.dsaLayer;

/*Generic array based min-heap
* Smallest element per the given comparator sits at index 0*/

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MinHeap<T> {
    private final List<T> heap=new ArrayList<>();
    private final Comparator<T> comparator;

    public MinHeap(Comparator<T> comparator)
    {
        this.comparator=comparator;
    }

    public void insert(T item)
    {
        heap.add(item);
        heapifyUp(heap.size()-1);
    }

    public T peak(){
        return heap.isEmpty() ? null : heap.get(0);
    }

    public T extractMin(){
        if(heap.isEmpty())
        {
            return null;
        }
        T min= heap.get(0);
        T last=heap.remove(heap.size()-1);
        if(!heap.isEmpty())
        {
            heap.set(0,last);
            heapifyDown(0);
        }
        return min;
    }

    public boolean isEmpty()
    {
        return heap.isEmpty();
    }
    public int size()
    {
        return heap.size();
    }

    private void heapifyUp(int i)
    {
        while (i > 0)
        {
            int parent=(i-1)/2;
            if(comparator.compare(heap.get(i), heap.get(parent)) < 0)
            {
                swap(i,parent);
                i=parent;
            }
            else {
                break;
            }
        }
    }

    private void heapifyDown(int i)
    {
        int size= heap.size();
        while(true)
        {
            int left=(2*i)+1;
            int right=(2*i)+2;
            int smallest=i;
            if(left < size && comparator.compare(heap.get(left), heap.get(smallest)) < 0)
            {
                smallest=left;
            }
            if(right < size && comparator.compare(heap.get(right), heap.get(smallest)) < 0)
            {
                smallest=right;
            }
            if(smallest==i)
            {
                break;
            }
            swap(i, smallest);
            i=smallest;
        }
    }

    private void swap(int i,int j)
    {
        T temp=heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j,temp);
    }
}

