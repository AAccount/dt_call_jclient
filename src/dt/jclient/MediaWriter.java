package dt.jclient;

import java.io.DataOutputStream;
import java.io.IOException;

//write simulated call data to media port.
//should stop itself when the call stop is issued
public class MediaWriter implements Runnable 
{

	private DataOutputStream oggOut;
	public MediaWriter()
	{
		try
		{
			oggOut = new DataOutputStream(Utils.media.getOutputStream());
		} 
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() 
	{
		while(Utils.state == CallState.INCALL) 
		{
			try
			{
				System.out.print("Say to " + Utils.callWith + ": ");
				String isay = Utils.kbBuffer.readLine();
				String sayWCap = Utils.cap + isay;
				oggOut.write(sayWCap.getBytes());
				oggOut.flush();
				
				//for simulation purposes the magic phrase "ENDITNOW" will simulate a call end
				//for call ending, it is the client's responsibility to set itself back into the
				//	"no call" state. if the client tells the server it's time to end the call ther
				//	server won't send anything back to you since there's nothing useful it could tell 
				//	you. it can't say "no don't end". you take care of the state change yourself. 
				if(sayWCap.contains("ENDITNOW"))
				{					
					//tell the server to end the call
					String endit = Utils.cap + Utils.getTimestamp() + "|end|" + Utils.callWith + "|" + Utils.sessionid;
					Utils.cmd.getOutputStream().write(endit.getBytes());
					
					//set internals to no call state
					System.out.println("Special jclient ENDITNOW received");
					Utils.callWith = Utils.nobody;
					Utils.state = CallState.NONE;
					
					//hacky workaround to stop the media threads
					Utils.media.close();
					Utils.media = Utils.mkSocket("localhost", 2001);
					String associateMedia = Utils.getTimestamp() + "|" + Utils.sessionid;
					Utils.media.getOutputStream().write(associateMedia.getBytes());
					
					//wake up the main menu thread after it's all done
					synchronized(Utils.menu)
					{
						Utils.menu.notify();
					}
				}				
			}
			catch (IOException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
				Utils.state = CallState.NONE; //lost the media port... dropped call
			}
		}
		System.out.println("Call finished. Media writer stopping.");
	}
}
