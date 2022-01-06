package Exercitiul_2_c;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BoundedQueue<T> {
    ReentrantLock enqLock, deqLock;
    AtomicInteger size;
    Node head, tail;
    int capacity;
    Condition notFullCondition, notEmptyCondition;

    public BoundedQueue(int capacity) {
        this.capacity = capacity;
        this.head = new Node(null);
        this.tail = head;
        this.size = new AtomicInteger(0);
        this.enqLock = new ReentrantLock();
        this.notFullCondition = enqLock.newCondition();
        this.deqLock = new ReentrantLock();
        this.notEmptyCondition = deqLock.newCondition();
    }


    public void enq(T x) throws InterruptedException
    {
        boolean mustWakeDequeuers = false;

        enqLock.lock();
        try {
            while (size.get() == capacity) {
                notFullCondition.await();
            }
            Node e = new Node(x);
            tail.next = e;
            tail = tail.next;
            System.out.println("Added to queue value " + x);
            if (size.getAndIncrement() == 0) {
                mustWakeDequeuers = true;
            }
        } finally {
            enqLock.unlock();
        }

        if (mustWakeDequeuers) {
            System.out.println("I need to notify dequers");
            deqLock.lock();
            try {
                notEmptyCondition.signalAll();
                System.out.println("Managed to notify dequers") ;
            } finally {
                deqLock.unlock();
            }
        }
    }

    public T deq() throws InterruptedException
    {
        boolean mustWakeEnqueuers = false;
        T v;

        deqLock.lock();
        try {
            if (head.next == null) {
                System.out.println(Thread.currentThread().getName() + " blocked in if");
                notEmptyCondition.await();
            }

            System.out.println(Thread.currentThread().getName() + " is between if and while with size " + size.get());
            boolean went_in_while = false;
            while (size.get() == 0)
            {
                went_in_while = true;
                //System.out.println("Am intrat :iondezastru:");
            }; //spinning

            if (went_in_while)
                System.out.println(Thread.currentThread().getName() + " out of while with size " + size.get());
            v = head.next.value;
            head = head.next;
            if (size.getAndDecrement() == capacity) {
                mustWakeEnqueuers = true;
            }
        } finally {
            deqLock.unlock();
        }

        if (mustWakeEnqueuers) {
            enqLock.lock();
            try {
                notFullCondition.signalAll();
            } finally {
                enqLock.unlock();
            }
        }
        return v;

    }

    protected class Node {

        public T value;
        public Node next;

        public Node(T x) {
            value = x;
            next = null;
        }
    }

}