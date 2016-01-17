package dt.jclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
			Utils.cmd = Utils.mkSocket("localhost", 1991);
			String login = Utils.getTimestamp() + "|login|" + uname + "|" + password;
			Utils.cmd.getOutputStream().write(login.getBytes());
	
			
			//read response
			InputStream cmdin = Utils.cmd.getInputStream();
			BufferedReader cmdTxtIn = new BufferedReader(new InputStreamReader(cmdin));
			String loginresp = cmdTxtIn.readLine();
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
			Utils.media = Utils.mkSocket("localhost", 2001);
			String associateMedia = Utils.getTimestamp() + "|" + Utils.sessionid;
			Utils.media.getOutputStream().write(associateMedia.getBytes());
			Utils.media.getOutputStream().write(Utils.cap.getBytes()); //sometimes java socket craps out
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
				System.out.println("mk: Make call");
				System.out.println("l: Lookup user");
				System.out.println("q: quit");
				System.out.println("d: direct command");
				System.out.println("xx: send suicide command to test memory leak");
				System.out.println("****************************");
				System.out.print("> ");
				String choice = Utils.kbBuffer.readLine();
				
				if(choice.equals("mk"))
				{
					System.out.print("Call who? ");
					String who = Utils.kbBuffer.readLine();
					String request = Utils.cap + Utils.getTimestamp() + "|call|" + who + "|" + Utils.sessionid;
					System.out.println("Call request: " + request);
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
				}
				else if (choice.equals("l"))
				{
					System.out.print("Lookup user: ");
					String who = Utils.kbBuffer.readLine();
					String request = Utils.cap + Utils.getTimestamp() + "|lookup|" + who + "|" + Utils.sessionid;
					System.out.println("Lookup request: " + request);
					Utils.cmd.getOutputStream().write(request.getBytes());
				}
				else if (choice.equals("q"))
				{
					quit = true;
					//cause all socket reading threads to die
					Utils.cmd.close();
					Utils.media.close();
				}
				else if (choice.equals("d"))
				{
					System.out.println("Current timestamp: " + Utils.getTimestamp());
					System.out.println("Current session id: " + Utils.sessionid);
					System.out.println("> ");
					String raw = Utils.kbBuffer.readLine();
					Utils.cmd.getOutputStream().write((Utils.cap + raw).getBytes());
				}
				else if (choice.equals("xx"))
				{
					System.out.println("Shutting down the call operator");
					String stop = Utils.cap + Utils.getTimestamp() + "|suicide|a|b";
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
}
