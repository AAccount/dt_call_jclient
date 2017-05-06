package dt.jclient;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class Main implements Runnable
{

	public static void main(String[] args) 
	{
		String uname, password;
		try
		{
			System.out.print("User name: ");
			uname = Utils.kbBuffer.readLine();
			System.out.print("Password: ");
			password = Utils.kbBuffer.readLine();

		} 
		catch (IOException e2)
		{
			e2.printStackTrace();
			return;
		}
		
		try
		{
			//send login command
			Utils.cmd = Utils.mkSocket("localhost", Utils.COMMANDPORT);
			String login = Utils.getTimestamp() + "|login|" + uname + "|" + password;
			Utils.cmd.getOutputStream().write(login.getBytes());
	
			
			//read response
			InputStream cmdin = Utils.cmd.getInputStream();
			byte[] responseRaw = new byte[Utils.bufferSize];
			int length = cmdin.read(responseRaw);
			String loginresp = new String(responseRaw, 0, length);
			System.out.println(loginresp);
			
			//process login response
			String[] respContents = loginresp.split("\\|");
			if(respContents.length != 4)
			{
				System.out.println("Server response imporoperly formatted");
				return; //not a legitimate server response
			}
			if(!(respContents[1].equals("resp") && respContents[2].equals("login")))
			{
				System.out.println("Server response CONTENTS imporperly formateed");
				return; //server response doesn't make sense
			}
			long ts = Long.valueOf(respContents[0]);
			if(!Utils.validTS(ts))
			{
				System.out.println("Server had an unacceptable timestamp");
				return;
			}
			Utils.sessionid = Long.valueOf(respContents[3]);
			System.out.println("Established command socket with sessionid: " + Utils.sessionid);
			
			//establish media socket
			Utils.media = Utils.mkSocket("localhost", Utils.MEDIAPORT);
			String associateMedia = Utils.getTimestamp() + "|" + Utils.sessionid;
			Utils.media.getOutputStream().write(associateMedia.getBytes());
		}
		catch(IOException i)
		{
			i.printStackTrace();
			return;
		}
		catch (NullPointerException n)
		{
			System.out.println("Server kicked jclient out.");
			return;
		}
			
		//start listening for server responses on the command thread
		CmdListener cmdListener = new CmdListener();
		Thread cmdListenerThread = new Thread(cmdListener);
		cmdListenerThread.start();
			
		//start menu thread
		Utils.menu = new Main();
		Thread menuThread = new Thread(Utils.menu);
		menuThread.start();

	}
	
	@Override
	public void run() 
	{
		boolean quit = false;
		try
		{
			while(!quit)
			{
				while(Utils.state == CallState.INCALL)
				{//don't show the menu if there's a call
					synchronized(this)
					{
						wait();
					}
				}
				
				System.out.println("****************************");
				System.out.println("Call simulator options");
				System.out.println("a: Simulate a call using an audio file as voice data");
				System.out.println("p: preset audio fill for incoming calls");
				System.out.println("l: Lookup user");
				System.out.println("q: quit");
				System.out.println("d: direct command");
				System.out.println("xx: send suicide command to test memory leak");
				System.out.println("****************************");
				System.out.print("> ");
				String choice = Utils.kbBuffer.readLine();
				
				if (choice.equalsIgnoreCase("a"))
				{//makes things easier that i don't have to always talk to myself to generate audio
					
					//generate call request
					System.out.print("Call who? ");
					String who = Utils.kbBuffer.readLine();
					String request = Utils.getTimestamp() + "|call|" + who + "|" + Utils.sessionid;
					System.out.println("Call request: " + request);

					if(setAudioFile())
					{
						Utils.cmd.getOutputStream().write(request.getBytes());
						Utils.callWith = who;
						Utils.state = CallState.INIT;	
							
						while(Utils.state != CallState.NONE)
						{//don't show the menu if there's a call
							System.out.println("Calling...");
							synchronized(this)
							{
								wait();
							}					
						}
						Utils.audioFile.close();
						Utils.audioFile = null; //needed for CmdListener to distinguish which media writer... see CmdListener
					}

				}
				else if (choice.equalsIgnoreCase("p"))
				{
					if(setAudioFile())
					{
						System.out.println("set audio file");
					}
					else
					{
						System.out.println("problem setting audio file");
					}
				}
				else if (choice.equalsIgnoreCase("l"))
				{
					System.out.print("Lookup user: ");
					String who = Utils.kbBuffer.readLine();
					String request = Utils.getTimestamp() + "|lookup|" + who + "|" + Utils.sessionid;
					System.out.println("Lookup request: " + request);
					Utils.cmd.getOutputStream().write(request.getBytes());
				}
				else if (choice.equalsIgnoreCase("q"))
				{
					quit = true;
					//cause all socket reading threads to die
					Utils.cmd.close();
					Utils.media.close();
				}
				else if (choice.equalsIgnoreCase("d"))
				{
					System.out.println("Current timestamp: " + Utils.getTimestamp());
					System.out.println("Current session id: " + Utils.sessionid);
					System.out.println("> ");
					String raw = Utils.kbBuffer.readLine();
					Utils.cmd.getOutputStream().write(raw.getBytes());
				}
				else if (choice.equalsIgnoreCase("xx"))
				{
					System.out.println("Shutting down the call operator");
					String stop = Utils.getTimestamp() + "|suicide|a|b";
					System.out.println(stop);
					Utils.cmd.getOutputStream().write(stop.getBytes());
				}
				else
				{
					System.out.println("Invalid option: " + choice + ". Try again");
				}
			}
		}
		catch(InterruptedException i)
		{
			i.printStackTrace();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	private boolean setAudioFile()
	{
		
		int kbps = 0;
		String filepath = "";
		try
		{
			//get audio file and bitrate to simulate realistic* voice data send rate
			System.out.print("Audio file: ");
			filepath = Utils.kbBuffer.readLine();
			filepath = filepath.replace("'", "").trim();
			System.out.print("Audio file bit rate (kbps) to simulate more realistic data send rate: ");
			String bitrate = Utils.kbBuffer.readLine();
			kbps = Integer.valueOf(bitrate);
		}
		catch(Exception n)
		{
			n.printStackTrace();
			return false;
		}
		Utils.bufferSize = (kbps+1)*1024 / 8; //a bit over is ok;
		
		//check for a realistic bitrate. if not realistic then the simulated data send rate is meaningless
		if(kbps < 4 || kbps > 4096)
		{
			System.out.println("Bitrate is not realistic");
			return false;
		}
		else
		{
			try
			{
				System.out.println("Using file: " + filepath + " for call audio");
				Utils.audioFile = new FileInputStream(filepath);
				return true;
			}
			catch (FileNotFoundException  | SecurityException ex)
			{
				System.out.println("Can't read the file because it's not there or not allowed to");
				return false;
			}
		}
	}
}
