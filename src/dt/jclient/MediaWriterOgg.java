package dt.jclient;

import java.io.BufferedOutputStream;
import java.io.IOException;

public class MediaWriterOgg implements Runnable
{
	private BufferedOutputStream buff;
	private byte[] byteBuffer = new byte[Utils.bufferSize];
	
	public MediaWriterOgg()
	{
		try
		{
			buff = new BufferedOutputStream(Utils.media.getOutputStream());
		} 
		catch (IOException e)
		{
			//it's a cell phone simulator. doesn't need fancy error handling. easy to restart
			System.out.println("=========== SOMETHING BAD HAPPENED ==========");
			e.printStackTrace();
		}
	}
	
	@Override
	public void run()
	{
		System.out.println("Start sending ogg audio");
		while(Utils.state == CallState.INCALL)
		{
			try
			{
				int read = Utils.ogg.read(byteBuffer, 0, Utils.bufferSize);
				buff.write(byteBuffer, 0, read);
				buff.flush();
				if(read < Utils.bufferSize) //not enough bytes in the file to fill the buffer... must be the end of the file
				{
					System.out.print("Type anything to end the call");
					Utils.kbBuffer.readLine();
					System.out.println("");
					//tell the server to end the call
					String endit = Utils.cap + Utils.getTimestamp() + "|end|" + Utils.callWith + "|" + Utils.sessionid;
					Utils.cmd.getOutputStream().write(endit.getBytes());
					
					//set internals to no call state
					System.out.println("End of the file. Hanging up.");
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
				System.out.println("=========== SOMETHING BAD HAPPENED ==========");
				e.printStackTrace();
			}
		}	
	}
}
