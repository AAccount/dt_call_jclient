package dt.jclient;

import java.io.IOException;

//listens for simulated call data
//should stop itself
public class MediaListener implements Runnable
{

	private int counter = 1;
	
	@Override
	public void run()
	{
		while(Utils.state == CallState.INCALL)
		{
			try
			{//the async magic here... it will patiently wait until something comes in
				byte[] mediaRaw = new byte[1024];
				Utils.media.getInputStream().read(mediaRaw);				
				System.out.println("Received media; counter: " + counter++);				
			} 
			catch (IOException e)
			{
				System.out.println("Media connection dead");
				Utils.state = CallState.NONE; //media connection broke.... dropped call
			}
		}
		System.out.println("Media listener stopping.");
	}
}
