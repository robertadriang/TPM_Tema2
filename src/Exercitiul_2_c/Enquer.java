package Exercitiul_2_c;

public class Enquer extends Thread
{
    public final BoundedQueue<Integer> queue;

    public Enquer(BoundedQueue<Integer> queue)
    {
        this.queue = queue;
    }

    @Override
    public void run()
    {
        for (int i=0;i<200000;i++)
        {
            try
            {
                queue.enq(i);
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

    }
}