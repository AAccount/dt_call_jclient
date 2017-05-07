package dt.jclient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.crypto.Cipher;

import com.sun.org.apache.xml.internal.security.utils.Base64;

public class Main implements Runnable
{

	public static void main(String[] args) 
	{
		String uname, privateKeyPath;
		PrivateKey privateKey = null;
		try
		{
			System.out.println("Java Client \"jClient\" for dtoperator");
			System.out.print("User name: ");
			uname = Utils.kbBuffer.readLine();
			System.out.print("Private Key: ");
			privateKeyPath = Utils.kbBuffer.readLine();

			//read the private key and convert to a string
			File privateKeyFile = new File(privateKeyPath);
			FileInputStream privateKeyStream = new FileInputStream(privateKeyFile);
			byte[] keyBytes = new byte[(int)privateKeyFile.length()];
			privateKeyStream.read(keyBytes);
			privateKeyStream.close();
			
			//chop of header and footer
			String keyString = new String(keyBytes);
			int beforeTrimLength = keyString.length();
			keyString = keyString.replace("-----BEGIN PRIVATE KEY-----\n", "");
			keyString = keyString.replace("-----END PRIVATE KEY-----", "");
			if(keyString.length() == beforeTrimLength)
			{//make sure the private key is extracted out of the openssl genrsa output
				System.err.println("Private key is improperly formatted.");
				System.err.println("Make sure you did: openssl pkcs8 -topk8 -nocrypt -in righthand.pem -out righthand_private.pem");
				return;
			}
			
			//actually turn the file into a key
			com.sun.org.apache.xml.internal.security.Init.init();
			byte[] keyDecoded = Base64.decode(keyString);
			KeyFactory kf = KeyFactory.getInstance("RSA");
			privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(keyDecoded));
		} 
		catch (Exception e2)
		{
			e2.printStackTrace();
			return;
		}
		
		try
		{
			//request login challenge
			Utils.cmd = Utils.mkSocket("localhost", Utils.COMMANDPORT);
			String login = Utils.getTimestamp() + "|login1|" + uname;
			Utils.cmd.getOutputStream().write(login.getBytes());
			
			//read in login challenge
			InputStream cmdin = Utils.cmd.getInputStream();
			byte[] loginChallengeBuffer = new byte[Utils.bufferSize];
			int length = cmdin.read(loginChallengeBuffer);
			String loginChallenge = new String(loginChallengeBuffer, 0, length);
			System.out.println(loginChallenge);
			
			//process login challenge response
			String[] loginChallengeContents = loginChallenge.split("\\|");
			if(loginChallengeContents.length != 4)
			{
				System.out.println("Server response imporoperly formatted");
				return; //not a legitimate server response
			}
			if(!(loginChallengeContents[1].equals("resp") && loginChallengeContents[2].equals("login1")))
			{
				System.out.println("Server response CONTENTS imporperly formateed");
				return; //server response doesn't make sense
			}
			
			//get the challenge
			String challenge = loginChallengeContents[3];
			System.out.println("This time's challenge: " + challenge);
			
			//the the challenge string of #s into actual #s
			byte[] challengeNumbers = destringify(challenge);
			
			//answer the challenge
			Cipher rsa = Cipher.getInstance("RSA"); //using default padding mode in server to keep java happy
			rsa.init(Cipher.DECRYPT_MODE, privateKey);
			byte[] decrypted = rsa.doFinal(challengeNumbers);
			String challengeDec = new String(decrypted, "UTF8");
			System.out.println("Today's challenge turns into this gibberish: " + challengeDec);
			String loginChallengeResponse = Utils.getTimestamp() + "|login2|" + uname + "|" + challengeDec;
			Utils.cmd.getOutputStream().write(loginChallengeResponse.getBytes());
			
			//see if the server liked the challenge response
			byte[] answerResponseBuffer = new byte[Utils.bufferSize];
			length = cmdin.read(answerResponseBuffer);
			String answerResponse = new String(answerResponseBuffer, 0, length);
			System.out.println(answerResponse);
			
			//check reaction response
			String[] answerResponseContents = answerResponse.split("\\|");
			if(answerResponseContents.length != 4)
			{
				System.out.println("Server response imporoperly formatted");
				return; //not a legitimate server response
			}
			if(!(answerResponseContents[1].equals("resp") && answerResponseContents[2].equals("login2")))
			{
				System.out.println("Server response CONTENTS imporperly formateed");
				return; //server response doesn't make sense
			}
			Utils.sessionid = Long.valueOf(answerResponseContents[3]);
			System.out.println("Established command socket with sessionid: " + Utils.sessionid);
			
			//establish media socket
			Utils.media = Utils.mkSocket("localhost", Utils.MEDIAPORT);
			String associateMedia = Utils.getTimestamp() + "|" + Utils.sessionid;
			Utils.media.getOutputStream().write(associateMedia.getBytes());
		}
		catch (NullPointerException n)
		{
			System.out.println("Server kicked jclient out.");
			return;
		}
		catch(Exception i)
		{
			i.printStackTrace();
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
				FileInputStream test = new FileInputStream(filepath);
				test.close();
				Utils.audioFile = filepath;
				
				return true;
			}
			catch (SecurityException | IOException ex)
			{
				System.out.println("Can't read the file because it's not there or not allowed to");
				return false;
			}
		}
	}
	
	//turn a string of #s into actual #s assuming the string is a bunch of
	//	3 digit #s glued to each other. also turned unsigned #s into signed #s
	static private byte[] destringify(String numbers)
	{
		byte[] result = new byte[(int)(numbers.length()/3)];
		for(int i=0; i<numbers.length(); i=i+3)
		{
			String digit = numbers.substring(i, i+3);
			result[(int)(i/3)] = (byte)(0xff & Integer.valueOf(digit));
		}
		return result;
	}
}
