package dt.jclient;

import java.io.IOException;

//listens for incoming server data on the command socket
public class CmdListener implements Runnable
{
	private boolean inputValid = false;
	
	public CmdListener ()
	{
		inputValid = true;
	}
	
	@Override
	public void run()
	{
		while(inputValid)
		{
			//responses from the server command connection will always be in text format
			//timestamp|ring|notavailable|tried_to_call
			//timestamp|ring|available|tried_to_call
			//timestamp|ring|incoming|trying_to_call
			//timestamp|ring|busy|tried_to_call
			//timestamp|resp|lookup|exists?
			//timestamp|resp|login|sessionid
			//timestamp|call|start|with
			//timestamp|call|reject|by
			//timestamp|call|end|by
			//timestamp|call|drop|sessionid
			
			try
			{//the async magic here... it will patiently wait until something comes in
				
				byte[] responseRaw = new byte[Utils.FOURK];
				int length = Utils.cmd.getInputStream().read(responseRaw);
				String fromServer = new String(responseRaw, 0, length);
				
				String[] respContents = fromServer.split("\\|");
				System.out.println("Server response raw: " + fromServer);
				
				//check for properly formatted command
				if(respContents.length != 4)
				{
					System.out.println("invalid server response");
					continue;
				}
				
				//verify timestamp
				long ts = Long.valueOf(respContents[0]);
				if(!Utils.validTS(ts))
				{
					System.out.println("Rejecting server response for bad timestamp");
					continue;
				}
				
				//loook at what the server is telling the call simulator to do
				String serverCommand = respContents[1];
				if(serverCommand.equals("ring"))
				{
					String subCommand = respContents[2];
					String involved = respContents[3];
					if(subCommand.equals("notavailable"))
					{
						if(involved.equals(Utils.callWith))
						{
							synchronized(Utils.menu)
							{
								System.out.println(Utils.callWith + " isn't online to talk with right now");
								Utils.callWith = Utils.nobody;
								Utils.state = CallState.NONE;
								Utils.menu.notify(); //can't call. start the menu again.
							}
						}
						else
						{
							System.out.println("Erroneous user n/a for call from: " + involved + " instead of: " + Utils.callWith);
						}
					}
					else if(subCommand.equals("available"))
					{
						if(involved.equals(Utils.callWith))
						{
							System.out.println(Utils.callWith + " is online. Ringing him/her now");
							Utils.state = CallState.INIT;
						}
						else
						{
							System.out.println("Erroneous user available from: " + involved + " instead of: " + Utils.callWith);
						}
					}
					else if(subCommand.equals("incoming"))
					{
						Utils.callWith = involved;
						Utils.state = CallState.INIT;
						
						System.out.println("Incoming call from: " + involved);
						System.out.println("accept or reject?");
						String choice = Utils.kbBuffer.readLine();
						if(choice.equals("accept"))
						{
							System.out.println("Accepting call with " + involved);
							String acceptResp = Utils.getTimestamp() + "|accept|" + involved + "|" + Utils.sessionid;
							Utils.cmd.getOutputStream().write(acceptResp.getBytes());
						}
						else if(choice.equals("reject"))
						{
							System.out.println("Rejected call with " + involved);
							String rejectResp = Utils.getTimestamp() + "|reject|" + involved + "|" + Utils.sessionid;
							Utils.cmd.getOutputStream().write(rejectResp.getBytes());
						}
					}
					else if(subCommand.equals("busy"))
					{
						System.out.println(involved + " is already in a call");
						Utils.state = CallState.NONE;
						Utils.callWith = Utils.nobody;
						synchronized(Utils.menu)
						{
							Utils.menu.notify(); //go back to the menu. person can't talk to you
						}
					}
					else
					{
						System.out.println("Unknown server RING command: " + fromServer);
					}
				}
				else if (serverCommand.equals("call"))
				{
					String subCommand = respContents[2];
					String involved = respContents[3];
					if(subCommand.equals("start"))
					{
						if(involved.equals(Utils.callWith))
						{
							System.out.println(Utils.callWith + " picked up. Start talking.");
							Utils.state = CallState.INCALL;
							
							System.out.println("Starting media listener");
							MediaListener callRead = new MediaListener();
							Thread callReadThread = new Thread(callRead);
							callReadThread.start();
							
							System.out.println("Starting media wrtier (FILE MODE)");
							MediaWriterAudio audioWrite = new MediaWriterAudio();
							Thread audioWriteThread = new Thread(audioWrite);
							audioWriteThread.start();

						}
						else
						{
							System.out.println("Erroneous start call with: " + involved + " instead of: " + Utils.callWith);
						}
					}
					else if(subCommand.equals("reject") || subCommand.equals("end"))
					{
						if(involved.equals(Utils.callWith))
						{
							System.out.println(Utils.callWith + " reject your call. Try again later");
							Utils.callWith = Utils.nobody;
							Utils.state = CallState.NONE;
							synchronized(Utils.menu)
							{
								Utils.menu.notify(); //call rejected or done. start the menu again.
							}
						}
						else
						{
							System.out.println("Erroneous call rejected/end with: " + involved + " instead of " + Utils.callWith);
						}
						
						if(subCommand.equals("end"))
						{//need for force stop the media read thread if it's an end
							//there is no way to kill the thread but to stop the socket to cause an exception
							//	restart after the exception
							Utils.media.close();
							Utils.media = Utils.mkSocket("localhost", Utils.MEDIAPORT);
							String associateMedia = Utils.getTimestamp() + "|" + Utils.sessionid;
							Utils.media.getOutputStream().write(associateMedia.getBytes());
						}
					}
					else
					{
						System.out.println("Erroneous call command: " + fromServer);
					}
				}
				else if(serverCommand.equals("lookup"))
				{
					String account = respContents[2];
					String status = respContents[3];
					System.out.println("Status of account " + account + ": " + status);
				}
				else
				{
					System.out.println("Unknown command/response: " + fromServer);
				}

			} 
			catch (IOException e)
			{
				inputValid = false;
				System.out.println("Command socket closed... probably quit called??");
			}
			catch(NumberFormatException n)
			{
				n.printStackTrace();
			}
			catch(NullPointerException n)
			{
				System.out.println("Command socket terminated from the server");
				inputValid = false;
				System.out.println("Use the menu q option and restart");
			}
		}
	}

}
