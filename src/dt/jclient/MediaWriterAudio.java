package dt.jclient;

import java.io.FileInputStream;
import java.io.IOException;

public class MediaWriterAudio implements Runnable
{
	private byte[] byteBuffer = new byte[Utils.mediaChunkSize];
	private int counter = 0;

	@Override
	public void run()
	{		
		if(Utils.audioFile == null)
		{//you're only going to test either network read or write.
			//if not writing then don't do this thread.
			return;
		}
		
		try
		{
			System.out.println("Throwing out the amr header");
			FileInputStream audioFile = new FileInputStream(Utils.audioFile);
			audioFile.read(new byte[6], 0, 6);
			
			System.out.println("Start sending audio");
			while(Utils.state == CallState.INCALL)
			{
				
				int read = audioFile.read(byteBuffer, 0, Utils.mediaChunkSize);
				Utils.media.getOutputStream().write(byteBuffer, 0, read);
				System.out.println("wrote audio " + counter++);
				Thread.sleep(20); //simulate live audio encoding which will have the bytes only as fast as the bitrate
				if(read < Utils.mediaChunkSize) //not enough bytes in the file to fill the buffer... must be the end of the file
				{
					System.out.println("End of the file");
					System.out.print("Type quit to end the call: ");
					String quit = Utils.kbBuffer.readLine();
					if(quit.equalsIgnoreCase("quit"))
					{
						//tell the server to end the call
						String endit = Utils.getTimestamp() + "|end|" + Utils.callWith + "|" + Utils.sessionid;
						Utils.cmd.getOutputStream().write(endit.getBytes());
							
						//set internals to no call state
						Utils.callWith = Utils.nobody;
						Utils.state = CallState.NONE;
							
						//kill the media listener
						Utils.media.close();
						Utils.media = Utils.mkSocket("localhost", Utils.MEDIAPORT);
						String associateMedia = Utils.getTimestamp() + "|" + Utils.sessionid;
						Utils.media.getOutputStream().write(associateMedia.getBytes());
						
						//wake up the main menu thread after it's all done
						synchronized(Utils.menu)
						{
							Utils.menu.notify();
						}
					
					}
						
				}
	
			}
			System.out.println("Media writer stopping");
			audioFile.close();
		} 
		catch (IOException | InterruptedException e)
		{
			System.out.println("=========== SOMETHING BAD HAPPENED ==========");
			e.printStackTrace();
		}
	}
}
