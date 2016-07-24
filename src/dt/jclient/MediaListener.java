package dt.jclient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

//listens for simulated call data
//should stop itself
public class MediaListener implements Runnable
{

	private int counter = 1;
	
	@Override
	public void run()
	{
		//file writing variables
		FileOutputStream amrOut = null;
		byte[] mediaRaw = new byte[1024];
		int read;

		//create the new file based on the current timestamp
		String fileName = Utils.recordPath + "amrOut-" + Utils.getTimestamp() + ".amr";
		File amrFile = new File(fileName);
		try 
		{
			amrFile.createNewFile();
			amrOut = new FileOutputStream(amrFile);
			amrOut.write(Utils.amrHeader);
		} 
		catch (IOException e1) 
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		while(Utils.state == CallState.INCALL)
		{
			try
			{//the async magic here... it will patiently wait until something comes in
				read = Utils.media.getInputStream().read(mediaRaw);
				amrOut.write(mediaRaw, 0, read);
				System.out.println("Wrote output to file; counter: " + counter++);				
			} 
			catch (IOException e)
			{
				System.out.println("Media connection dead");
				Utils.state = CallState.NONE; //media connection broke.... dropped call
			}
		}
		
		//close the file output
		try 
		{
			amrOut.flush();
			amrOut.close();
		} 
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Media listener stopping.");
	}
}
