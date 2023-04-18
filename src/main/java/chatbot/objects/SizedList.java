package chatbot.objects;

import java.util.Collection;
import java.util.LinkedList;

public class SizedList<T> extends LinkedList<T> { // maybe a stack or queue is better? idk
    private int maxSize;

    public SizedList(int maxSize) {
        this.maxSize = maxSize;
    }

    // when items are added, if the list is too big, remove the oldest item
    @Override
    public boolean add(T t) {
        if (size() >= maxSize) {
            removeFirst();
        }
        return super.add(t);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        while (size() + c.size() > maxSize) {
            removeFirst();
        }
        return super.addAll(index, c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        while (size() + c.size() > maxSize) {
            removeFirst();
        }
        return super.addAll(c);
    }
}
