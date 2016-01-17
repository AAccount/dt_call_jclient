package dt.jclient;

import java.io.IOException;

//listens for simulated call data
//should stop itself
public class MediaListener implements Runnable
{

	@Override
	public void run()
	{
		while(Utils.state == CallState.INCALL)
		{
			try
			{//the async magic here... it will patiently wait until something comes in
				byte[] mediaRaw = new byte[1024];
				Utils.media.getInputStream().read(mediaRaw);				
				String fromServer = new String(mediaRaw);
				
				//for cosmetic purposes don't show the G cap workaround
				char char1 = fromServer.charAt(1);				
				int char1val = Character.getNumericValue(char1);
				
				//cheap workaround for G cap
				if(Utils.state == CallState.INCALL && char1val > -1)
				{
					System.out.println(Utils.callWith + " says: " + fromServer);
				}
				
			} 
			catch (IOException e)
			{
				System.out.println("Media connection dead");
				Utils.state = CallState.NONE; //media connection broke.... dropped call
			}
		}
		System.out.println("Call finished. Media listener stopping.");
	}
}
