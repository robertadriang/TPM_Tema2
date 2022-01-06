package Exercitiul_3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OptimisticListVersioned<T> {
    private static class Consumer extends Thread {
        private final OptimisticListVersioned<Integer> list;
        private final int baseNumber;

        public Consumer(OptimisticListVersioned<Integer> list, int baseNumber) {
            this.list = list;
            this.baseNumber = baseNumber;
        }

        @Override
        public void run() {
            for (int i = baseNumber; i * 2 < 25000; i += 4)
                list.remove(i * 2);
        }
    }

    private static class Producer extends Thread {
        private final OptimisticListVersioned<Integer> list;
        private final int baseNumber;
        private final boolean[] inited;

        public Producer(OptimisticListVersioned<Integer> list, int baseNumber, boolean[] inited) {
            this.list = list;
            this.baseNumber = baseNumber;
            this.inited = inited;
        }

        @Override
        public void run() {
            for (int i = baseNumber; i < 100000; i += 4) {
                list.add(i);
                if (!inited[baseNumber] && i >= 25000) {
                    inited[baseNumber] = true;
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        var expectedList = new ArrayList<Integer>();
        for (int i = 0; i < 100000; i++) {
            if (i >= 25000 || i % 2 == 1)
                expectedList.add(i);
        }
        for (int iteration = 0; iteration < 5; iteration++) {
            OptimisticListVersioned<Integer> list = new OptimisticListVersioned<>();
            boolean[] inited = new boolean[4];

            var producer1 = new OptimisticListVersioned.Producer(list, 0, inited);
            var producer2 = new OptimisticListVersioned.Producer(list, 1, inited);
            var producer3 = new OptimisticListVersioned.Producer(list, 2, inited);
            var producer4 = new OptimisticListVersioned.Producer(list, 3, inited);

            producer1.start();
            producer2.start();
            producer3.start();
            producer4.start();

            var consumer1 = new OptimisticListVersioned.Consumer(list, 0);
            var consumer2 = new OptimisticListVersioned.Consumer(list, 1);
            var consumer3 = new OptimisticListVersioned.Consumer(list, 2);
            var consumer4 = new OptimisticListVersioned.Consumer(list, 3);

            while (!inited[0] || !inited[1] || !inited[2] || !inited[3]) {
                Thread.sleep(100);
            }

            consumer1.start();
            consumer2.start();
            consumer3.start();
            consumer4.start();

            producer1.join();
            producer2.join();
            producer3.join();
            producer4.join();
            consumer1.join();
            consumer2.join();
            consumer3.join();
            consumer4.join();

            var finalList = list.getList();
            if (!finalList.equals(expectedList)) {
                System.err.println("Lists are not equal");
                System.exit(0);
            } else
                System.out.println("List ok");

            //testing contains times
            for (int i = 0; i < 100000; i++) {
                list.contains(i);
            }
        }
        OptimisticListVersioned.printTimes(addTimes, removeTimes, containsTimes, validationTimes);
    }

    private final Node head; //First list entry

    public OptimisticListVersioned() {
        this.head = new Node(Integer.MIN_VALUE);
        this.head.next = new Node(Integer.MAX_VALUE);
    }

    //Add an element.  @return true iff element was not there already
    public boolean add(T item) {
        long startTime = System.nanoTime();
        try {
            int key = item.hashCode();
            while (true) {
                Node pred = this.head;
                Node current = pred.next;
                while (current.key <= key) {
                    pred = current;
                    current = current.next;
                }
                var initialPredVersion = pred.version.get();
                var initialCurrentVersion = current.version.get();
                pred.lock();
                current.lock();
                try {
                    if (validate(pred, current, initialPredVersion, initialCurrentVersion)) {
                        if (current.key == key) { // present
                            return false;
                        } else {               // not present
                            pred.version.incrementAndGet();
                            current.version.incrementAndGet();

                            Node entry = new Node(item);
                            entry.next = current;
                            pred.next = entry;

                            pred.version.incrementAndGet();
                            current.version.incrementAndGet();

                            return true;
                        }
                    }
                } finally {                // always unlock
                    pred.unlock();
                    current.unlock();
                }
            }
        } finally {
            addTimes.add(System.nanoTime() - startTime);
        }
    }

    // Remove an element. @return true iff element was present
    public boolean remove(T item) {
        long startTime = System.nanoTime();
        try {
            int key = item.hashCode();
            while (true) {
                Node pred = this.head;
                Node current = pred.next;
                while (current.key < key) {
                    pred = current;
                    current = current.next;
                }
                var initialPredVersion = pred.version.get();
                var initialCurrentVersion = current.version.get();
                pred.lock();
                current.lock();
                try {
                    if (validate(pred, current, initialPredVersion, initialCurrentVersion)) {
                        if (current.key == key) { // present in list
                            pred.version.incrementAndGet();
                            current.version.incrementAndGet();

                            pred.next = current.next;

                            pred.version.incrementAndGet();
                            current.version.incrementAndGet();
                            return true;
                        } else {               // not present in list
                            return false;
                        }
                    }
                } finally {                // always unlock
                    pred.unlock();
                    current.unlock();
                }
            }
        } finally {
            removeTimes.add(System.nanoTime() - startTime);
        }
    }

    // Test whether element is present @return true iff element is present
    public boolean contains(T item) {
        long startTime = System.nanoTime();
        try {
            int key = item.hashCode();
            while (true) {
                Node pred = this.head; // sentinel node;
                Node current = pred.next;
                while (current.key < key) {
                    pred = current;
                    current = current.next;
                }
                long predInitialVersion = pred.version.get();
                var currentInitialVersion = current.version.get();
                pred.lock();
                current.lock();
                try {
                    if (validate(pred, current, predInitialVersion, currentInitialVersion)) {
                        return (current.key == key);
                    }
                } finally {                // always unlock
                    pred.unlock();
                    current.unlock();
                }
            }
        } finally {
            containsTimes.add(System.nanoTime() - startTime);
        }
    }

    /**
     * Check that prev and current are still in list and adjacent
     *
     * @param pred    predecessor node
     * @param current current node
     * @return whther predecessor and current have changed
     */
    private boolean validate(Node pred, Node current, long predInitial, long currentInitial) {
        long startTime = System.nanoTime();
        try {
            if (predInitial % 2 == 1 || currentInitial % 2 == 1)
                return false;

            if (predInitial == pred.version.get() && currentInitial == current.version.get() &&
                    pred.next == current)
                return true;

            Node entry = head;
            while (entry.key <= pred.key) {
                if (entry == pred)
                    return pred.next == current;
                entry = entry.next;
            }
            return false;
        } finally {
            validationTimes.add(System.nanoTime() - startTime);
        }
    }

    private class Node {
        AtomicLong version;     //for versioning
        T item;                 //actual item
        int key;                //item's hash code
        Node next;              //next node in list
        Lock lock; //Synchronizes node.

        /**
         * Constructor for usual node
         *
         * @param item element in list
         */
        Node(T item) {
            this.version = new AtomicLong(0);
            this.item = item;
            this.key = item.hashCode();
            lock = new ReentrantLock();

        }

        /**
         * Constructor for sentinel node
         *
         * @param key should be min or max int value
         */
        Node(int key) {
            this.version = new AtomicLong(0);
            this.key = key;
            lock = new ReentrantLock();
        }

        void lock() {
            lock.lock();
        } //Lock entry

        void unlock() {
            lock.unlock();
        } //Unlock entry
    }

    public List<T> getList() {
        var list = new ArrayList<T>();
        var node = head.next;
        while (node.key != Integer.MAX_VALUE) {
            list.add(node.item);
            node = node.next;
        }
        return list;
    }

    static List<Long> addTimes = Collections.synchronizedList(new ArrayList<>());
    static List<Long> removeTimes = Collections.synchronizedList(new ArrayList<>());
    static List<Long> containsTimes = Collections.synchronizedList(new ArrayList<>());
    static List<Long> validationTimes = Collections.synchronizedList(new ArrayList<>());

    static void printTimes(List<Long> addTimes, List<Long> removeTimes, List<Long> containsTimes, List<Long> validationTimes) {
        long sumAddTimes = 0;
        for (var time : addTimes) {
            sumAddTimes += time;
        }
        System.out.println("Avg add time: " + sumAddTimes / addTimes.size() + " ns");

        long sumRmTimes = 0;
        for (var time : removeTimes) {
            sumRmTimes += time;
        }
        System.out.println("Avg remove time: " + sumRmTimes / addTimes.size() + " ns");

        long sumVerifTimes = 0;
        for (var time : validationTimes) {
            sumVerifTimes += time;
        }
        System.out.println("Avg validtion time: " + sumVerifTimes / addTimes.size() + " ns");

        long sumContainsTimes = 0;
        for (var time : containsTimes) {
            sumContainsTimes += time;
        }
        System.out.println("Avg contains time: " + sumContainsTimes / addTimes.size() + " ns");
    }
}