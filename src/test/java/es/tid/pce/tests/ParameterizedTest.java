package es.tid.pce.tests;

import static org.junit.Assert.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;

import es.tid.pce.client.QuickClientObj;
import es.tid.pce.pcep.constructs.Request;
import es.tid.pce.pcep.messages.PCEPMessage;
import es.tid.pce.pcep.messages.PCEPRequest;
import es.tid.pce.pcep.messages.PCEPResponse;
import es.tid.pce.pcep.objects.Bandwidth;
import es.tid.pce.pcep.objects.ExplicitRouteObject;
import es.tid.pce.pcep.objects.NoPath;
import es.tid.pce.server.PCEServer;
import es.tid.rsvp.objects.subobjects.IPv4prefixEROSubobject;
import es.tid.rsvp.objects.subobjects.UnnumberIfIDEROSubobject;

@RunWith(org.junit.runners.Parameterized.class)
public class ParameterizedTest {
	
	private String fileConf;
	private String[] msgSend;
	private String[] checkRes;
	private Thread pceServer;
	
	private static class RunPCEServer implements Runnable{
		private String file;
		public void run() {
	    	String[] args= new String[]{this.file, "1"};
			PCEServer.main(args);
	    }
		public RunPCEServer(String file){
			this.file=file;
		}
		
	}
	//@Parameters
		@Parameters()
	    public static Collection configs() {
	    	Object[][] objects={
	    			{"src/test/resources/PCEServerConfiguration_SSON_Line.xml", "localhost 4189 192.168.1.2 192.168.1.5 -g -of 1002 -rgbw 2", "-nopath"},
	    			{"src/test/resources/PCEServerConfiguration_SSON_Line.xml", "localhost 4189 192.168.1.1 192.168.1.2 -g -rgbw 2", "-ero 192.168.1.1:1,192.168.1.2/32"},
	    			{"src/test/resources/PCEServerConfiguration_SSON_Line.xml", "localhost 4189 192.168.1.1 192.168.1.3 -g -rgbw 2", "-ero 192.168.1.1:1,192.168.1.2:2,192.168.1.3/32"},
	    			{"src/test/resources/PCEServerConfiguration_SSON_Line.xml", "localhost 4189 192.168.1.3 192.168.1.1 -rgbw 2", "-ero 192.168.1.3:2,192.168.1.2:1,192.168.1.1/32"},
	    			{"src/test/resources/PCEServerConfiguration_SSON_Triangle.xml", "localhost 4189 192.168.1.1 192.168.1.3 -g -rgbw 2", "-ero 192.168.1.1:2,192.168.1.3/32"},
	    			{"src/test/resources/PCEServerConfiguration_SSON_Triangle.xml", "localhost 4189 192.168.1.3 192.168.1.2 -rgbw 2", "-ero 192.168.1.3:2,192.168.1.2/32"},	
	    			{"src/test/resources/PCEServerConfiguration_SSON_Triangle.xml", "localhost 4189 192.168.1.4 192.168.1.2 -rgbw 2", "-nopath"},	
	    	};
			return Arrays.asList(objects);
	    }
		
	public ParameterizedTest(String fileConf, String msgSend, String checkRes) {
		this.fileConf = fileConf;
		this.msgSend = msgSend.split(" ");
		this.checkRes = checkRes.split(" ");
	}
		
	@Before
	public void initialize(){
		System.out.println(">>>>>TEST ["+this.msgSend.toString()+"]");
		System.out.println("Inicando PCE Server");
		//Run PCE server
		this.pceServer = (new Thread(new RunPCEServer(this.fileConf)));
		pceServer.start();
		try {
			Thread.sleep(300);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	@Test
    public void test (){
		
		
		//Run PCC QuickClient
		try{
			
			if(msgSend.length < 4){
				assertTrue("Invalid test arguments number (>4)", false);
			}
    		CommandLine optReq = QuickClientObj.getLineOptions(this.msgSend);
    		Logger log =Logger.getLogger("PCCClient");
			QuickClientObj qcObj = new QuickClientObj(log, msgSend[0], Integer.valueOf(msgSend[1]).intValue());
			qcObj.start();	
			Request req = qcObj.createReqMessage(msgSend[2],msgSend[3], optReq);
			PCEPRequest p_r = new PCEPRequest();
			p_r.addRequest(req);
			LinkedList<PCEPMessage> messageList=new LinkedList<PCEPMessage>();
			PCEPResponse res = qcObj.sendReqMessage(p_r, messageList);
			this.parseAndCheckRes(res);
			
    	}catch(ParseException ex){
    		assertTrue("Invalid test arguments", false);
    	}
		
		System.out.println("Finalizando PCE Server");
		
	
		
	}
	
	@After
	public void finalize(){
		this.pceServer.interrupt();
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	



	private void parseAndCheckRes(PCEPResponse res) {
		
		
		Option noPathOpt= new Option("nopath", "NoPath object present");
		Option eroOpt= OptionBuilder.withArgName( "value" ).hasArg().withDescription(  "Explicit Route Object present" ).create( "ero" );
		Option bwOpt= OptionBuilder.withArgName( "value" ).hasArg().withDescription(  "Bandwidth Object present" ).create( "bandwidth" );
		Options options = new Options();
		options.addOption(eroOpt);
		options.addOption(bwOpt);
		options.addOption(noPathOpt);
		CommandLineParser parser = new DefaultParser();
		
		try {
			CommandLine line=parser.parse( options, this.checkRes );
			if(line.hasOption("nopath")){
				assertTrue("NOPATH object no present",res.getResponse(0).getNoPath()!=null);
			}else{
				assertTrue("NOPATH object present",res.getResponse(0).getNoPath()==null);
			}
			
			if(line.hasOption("ero")){
				ExplicitRouteObject ero = new ExplicitRouteObject();
				ExplicitRouteObject eroRes = res.getResponseList().getFirst().getPath(0).geteRO();
				System.out.println("ERO en respuesta: "+eroRes);
				
				String argEro = line.getOptionValue("ero");
				String[] argsEro = new String[]{argEro} ;
				if(argEro.contains(",")){
					argsEro=argEro.split(",");
				}
				try {
					for(String s : argsEro){
						if(s.contains(":")){ //UnnumberIfIDEROSubobject
							String[] part=s.split(":");
							Inet4Address ipHost = (Inet4Address)Inet4Address.getByName(part[0]);
							UnnumberIfIDEROSubobject ipIfSub= new UnnumberIfIDEROSubobject();
							ipIfSub.setRouterID(ipHost);
							ipIfSub.setInterfaceID(Long.parseLong(part[1]));
							ipIfSub.encode();
							ero.addEROSubobject(ipIfSub);
						}else if(s.contains("/")){ //IPv4prefixEROSubobject  
							String[] part=s.split("/");
							IPv4prefixEROSubobject ip4Sub = new IPv4prefixEROSubobject();
							Inet4Address ipHost = (Inet4Address)Inet4Address.getByName(part[0]);
							ip4Sub.setIpv4address(ipHost);
							ip4Sub.setPrefix(Integer.parseInt(part[1]));
							ip4Sub.encode();
							ero.addEROSubobject(ip4Sub);
						}
					}
					ero.encode();
					boolean check = eroRes.equals(ero);
					assertTrue("ERO wrong ["+ero.toString()+"] VS ["+eroRes.toString()+"]", check);
				} catch (UnknownHostException e) {
					e.printStackTrace();
					assertTrue("Unknow error", false);
				}
			
			}
		} catch (ParseException e) {
			assertTrue("Invalid test response parse arguments", false);
		}
	}
}
