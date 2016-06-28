package dt.jclient;

import java.io.IOException;

public class MediaWriterAudio implements Runnable
{
	private byte[] byteBuffer = new byte[Utils.bufferSize];
	private int counter = 0;
	
	public MediaWriterAudio()
	{

	}
	
	@Override
	public void run()
	{		
		System.out.println("Start sending audio");
		while(Utils.state == CallState.INCALL)
		{
			try
			{
				int read = Utils.audioFile.read(byteBuffer, 0, Utils.bufferSize);
				Utils.media.getOutputStream().write(byteBuffer, 0, read);
				System.out.println("wrote audio " + counter++);
				Thread.sleep(1000); //simulate live audio encoding which will have the bytes only as fast as the bitrate
				if(read < Utils.bufferSize) //not enough bytes in the file to fill the buffer... must be the end of the file
				{
					System.out.println("End of the file");
					System.out.print("Type quit to end the call");
					String quit = Utils.kbBuffer.readLine();
					if(quit.equalsIgnoreCase("quit"))
					{
						//tell the server to end the call
						String endit = Utils.cap + Utils.getTimestamp() + "|end|" + Utils.callWith + "|" + Utils.sessionid;
						Utils.cmd.getOutputStream().write(endit.getBytes());
						
						//set internals to no call state
						Utils.callWith = Utils.nobody;
						Utils.state = CallState.NONE;
						
						//wake up the main menu thread after it's all done
						synchronized(Utils.menu)
						{
							Utils.menu.notify();
						}
					
					}
					
				}
			} 
			catch (IOException | InterruptedException e)
			{
				System.out.println("=========== SOMETHING BAD HAPPENED ==========");
				e.printStackTrace();
			}
		}
		System.out.println("Media writer stopping");
	}
}
