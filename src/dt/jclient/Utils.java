package dt.jclient;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Utils
{	
	//constants
	public static final String nobody = "(nobody)";
	public static final String certPath = "/home/Daniel/Documents/untitled_folder/public.pem";
	public static final String recordPath = "/tmp/";
	public static final byte[] amrHeader = new byte[] {0x23, 0x21, 0x41, 0x4D, 0x52, 0x0A}; //#!AMR(0x0A)
	public static final int FOURK = 4000;
	
	public static int mediaChunkSize = FOURK;
	
	//ports
	public static final int COMMANDPORT = 1991;
	public static final int MEDIAPORT = 2014;
	
	//session information
	public static String sessionid;
	public static int retry = 0;
	//session sockets
	public static Socket cmd = null;
	public static Socket media = null;
	//session call variables
	public static CallState state= CallState.NONE;
	public static String callWith = nobody;
	public static String audioFile = null;
	public static Main menu;
	public static BufferedReader kbBuffer = new BufferedReader(new InputStreamReader(System.in));

	public static long getTimestamp()
	{
		return System.currentTimeMillis()/1000L;
	}
	
	public static boolean validTS(long ts)
	{
		long now = getTimestamp();
		long fivemins = 60*5;
		long diff = now-ts;
		if(diff < 0)
		{
			diff = -1*diff;
		}
		if(diff > fivemins)
		{
			return false;
		}
		return true;
	}
	
	public static Socket mkSocket(String host, int port)
	{
		//get rid of this when going for real or print the certificate information or load
		// the server certificate from a file
		TrustManager[] trustOnlyServerCert = new TrustManager[]
		{new X509TrustManager()
			{
				@Override
				public void checkClientTrusted(X509Certificate[] chain, String alg)
				{
				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String alg) throws CertificateException
				{
					//Only accept the server's certificate no matter the issuer/chain.
					//It is your job to make sure you download the right expected server certificate
					try
					{
						FileInputStream certFile = new FileInputStream(certPath);
						X509Certificate expectedCert = (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(certFile);
						if(!chain[0].equals(expectedCert))
						{
							throw new CertificateException("Server certificate does not match expected one.");
						}
					} 
					catch (FileNotFoundException e)
					{
						throw new CertificateException("Expected certificate can't be found");
					} 
				}

				@Override
				public X509Certificate[] getAcceptedIssuers()
				{
					// TODO Auto-generated method stub
					return null;
				}
					
			}			
		};
		try
		{
			SSLContext context;
			context = SSLContext.getInstance("TLSv1.2");
			context.init(new KeyManager[0], trustOnlyServerCert, new SecureRandom());
			SSLSocketFactory mkssl = context.getSocketFactory();
			return mkssl.createSocket(host, port);
		} 
		catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
			return null;
		} 
		catch (KeyManagementException e)
		{
			e.printStackTrace();
			return null;
		} 
		catch (UnknownHostException e)
		{
			e.printStackTrace();
			return null;
		} 
		catch (IOException e)
		{
			e.printStackTrace();
			return null;
		}
	}
}
