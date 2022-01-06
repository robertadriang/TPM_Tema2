package Exercitiul_2_c;

public class Main
{
    public static void main(String[] args) throws InterruptedException
    {
        BoundedQueue<Integer> queue = new BoundedQueue<>(5);
        var enq1 = new Enquer(queue);
        enq1.setName("enq");
        var deq1 = new Dequer(queue);
        deq1.setName("deq1");
        var deq2 = new Dequer(queue);
        deq2.setName("deq2");
        enq1.start();
        deq1.start();
        deq2.start();
        enq1.join();
        deq1.join();
        deq2.join();
    }
}