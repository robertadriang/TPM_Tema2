package Exercitiul_2_c;

public class Dequer extends Thread
{
    public final BoundedQueue<Integer> queue;

    public Dequer(BoundedQueue<Integer> queue)
    {
        this.queue = queue;
    }

    @Override
    public void run()
    {
        for (int i=0; i<100000;i++)
        {
            try
            {
               queue.deq();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }
}