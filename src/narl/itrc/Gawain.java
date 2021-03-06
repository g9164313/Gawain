package narl.itrc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.stage.Stage;
import javafx.util.Duration;
import narl.itrc.init.Loader;
import narl.itrc.init.LogStream;
import narl.itrc.init.PanSplash;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class Gawain extends Application {
	
	private static final Properties propCache = new Properties();
	private static final String propName = "conf.properties";
	
	public static String getConfigName(){
		return pathSock+propName;
	}
	
	private static void propPrepare(){
		try {
			InputStream stm=null;
			File fs = new File(getConfigName());			
			if(fs.exists()==false){
				stm = Gawain.class.getResourceAsStream("/narl/itrc/res/"+propName);
			}else{
				stm = new FileInputStream(fs);
			}
			propCache.load(stm);
			stm.close();			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static Properties prop(){
		return propCache;
	}
	
	public static boolean propFlag(final String option) {
		String val = propCache.getProperty(option, "");
		if(val.length()==0) {
			return false;
		}
		val = val.trim().toLowerCase();
		if(val.charAt(0)=='t') {
			//true or 't' is okay~~
			return true;
		}
		return false;
	}
	
	private static void propRestore(){
		try {
			File fs = new File(getConfigName());
			if(fs.exists()==false){
				return;
			}
			//cache the old data
			ArrayList<String> lst =new ArrayList<String>();
			BufferedReader rd = new BufferedReader(new FileReader(fs));
			while(rd.ready()==true){
				String txt = rd.readLine();
				if(txt==null){
					break;
				}
				lst.add(txt);
			}
			rd.close();
			//check all options again~~~
			for(int i=0; i<lst.size(); i++){
				String txt = lst.get(i).trim();
				if(txt.length()==0 || txt.charAt(0)=='#'){
					continue;
				}
				String[] col = txt.split("=");				
				if(col.length<=1){
					continue;
				}
				col[0] = col[0].trim();
				String val = propCache
						.getProperty(col[0],null)
						.replace("\\", "\\\\");
				if(val==null){
					continue;//is this possible???
				}
				lst.set(i, col[0]+"="+val);
				propCache.remove(col[0]);
			}
			//add the new options...
			for(Object key:propCache.keySet()){
				String val = ((String)propCache.get(key))
					.replace("\\", "\\\\");
				lst.add(String.format("%s = %s", (String)key, (String)val));
			}
			//dump data and option!!!
			BufferedWriter wr = new BufferedWriter(new FileWriter(fs));
			for(String txt:lst){				
				wr.write(txt+"\r\n");
			}
			wr.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	//--------------------------------------------//
	
	private static void liceWrite(byte[] dat){
		if(jarName.length()==0){
			return;//no!!! we don't have a jar file~~
		}
		try {
			RandomAccessFile fs = new RandomAccessFile(jarName,"rw");	
			final byte[] buf = {0,0,0,0};
			for(long pos = fs.length()-4; pos>0; --pos){				
				fs.seek(pos);
				fs.read(buf,0,4);
				if(
					buf[3]==0x06 && 
					buf[2]==0x05 && 
					buf[1]==0x4b && 
					buf[0]==0x50
				){
					//reach to EOCD
					fs.seek(pos+20);
					short len = (short)(dat.length*2);
					buf[0] = (byte)((len&0x00FF)   );
					buf[1] = (byte)((len&0xFF00)>>8);
					fs.write(buf,0,2);
					fs.write(Misc.hex2txt(dat).getBytes());
					break;
				}
			}						
			fs.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(-202);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-203);
		}
	}
		
	private static final String LICE_CIPHER="AES";
	private static final String LICE_SECKEY=Gawain.class.toString();	
	private static float liceDay = 1.f;//unit is days
	private static SecretKeySpec liceKey = null;
	private static Cipher liceCip = null;
	
	private static boolean isBorn(){
		if(jarName.length()==0){
			return false;//no!!! we don't have a jar file~~
		}
		try {
			BasicFileAttributes attr = Files.readAttributes(
				Paths.get(jarName),
				BasicFileAttributes.class
			);
			FileTime t1 = attr.lastAccessTime();
			FileTime t2 = attr.lastModifiedTime();
			long diff = t1.toMillis() - t2.toMillis();
			if(diff<60000){
				return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}		
		return false;
	}
		
	private static EventHandler<ActionEvent> eventPeekLice = new EventHandler<ActionEvent>(){
		@Override
		public void handle(ActionEvent event) {
			//check license key per hour 
			if(liceDay<0.f){
				Misc.logv("License is expired!!!");
				System.exit(0);
				return;
			}			
			liceDay = liceDay - (1.f/24.f);
			String txt = String.format("%.2f",liceDay);			
			try {
				liceCip.init(Cipher.ENCRYPT_MODE,liceKey);
				liceWrite(liceCip.doFinal(txt.getBytes()));
			} catch (InvalidKeyException e) {
				e.printStackTrace();
				System.exit(-11);
			} catch (IllegalBlockSizeException e) {
				e.printStackTrace();
				System.exit(-12);
			} catch (BadPaddingException e) {
				e.printStackTrace();
				System.exit(-13);
			}
			Misc.logv("check licence - %.2fday",liceDay);
		}
	};
		
	@SuppressWarnings("unused")
	private static void liceBind(){
		
		if(jarName.length()==0){
			return;//no!!! we don't have a jar file~~
		}
		try {
			liceKey = new SecretKeySpec(
				LICE_SECKEY.getBytes(),
				LICE_CIPHER
			);
			liceCip = Cipher.getInstance(LICE_CIPHER);			
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
			System.exit(-11);
		} catch (NoSuchPaddingException e1) {
			e1.printStackTrace();
			System.exit(-12);
		}
		try {			
			final JarFile jj = new JarFile(jarName);
			String txt = jj.getComment();
			if(txt==null){				
				if(isBorn()==false){
					Misc.loge("It is Fail,This is not a birthday!!");
					System.exit(-204);
				}
				//first,bind a license~~
				txt = propCache.getProperty("LICENCE","1");//default is one day~~~
				liceDay = Float.valueOf(txt);
				liceCip.init(Cipher.ENCRYPT_MODE,liceKey);
				liceWrite(liceCip.doFinal(txt.getBytes()));
			}else{
				//check whether license is valid~~
				liceCip.init(Cipher.DECRYPT_MODE,liceKey);	
				txt = new String(liceCip.doFinal(Misc.txt2hex(txt)));
				liceDay = Float.valueOf(txt);
				if(liceDay<0.f){
					Misc.logv("License is expired!!!");
					System.exit(0);
				}
			}
			jj.close();
			
			Timeline timer = new Timeline(new KeyFrame(
				Duration.hours(1),
				eventPeekLice
			));
			timer.setCycleCount(Timeline.INDEFINITE);
			timer.play();
		} catch (IOException e) {			
			e.printStackTrace();
			System.exit(-201);
		} catch (InvalidKeyException e) {			
			e.printStackTrace();
			System.exit(-11);
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
			System.exit(-11);
		} catch (BadPaddingException e) {
			e.printStackTrace();
			System.exit(-11);
		} catch (NumberFormatException e){
			//someone want to modify license!!!
			System.exit(0);
		}
	}
	//--------------------------------------------//
		
	public static PanBase mainPanel = null;
	
	public static final AtomicBoolean isKill = new AtomicBoolean(false);
	
	@Override
	public void start(Stage stg) throws Exception {
		long tick = System.currentTimeMillis();
		try {
			String name = Gawain.prop().getProperty("LAUNCH","");
			mainPanel = (PanBase)Class.forName(name)
				//.getConstructor()
				//.newInstance();
				.getConstructor(Stage.class)
				.newInstance(stg);
			mainPanel.initLayout();
			mainPanel.appear();
		} catch (
			InstantiationException | 
			IllegalAccessException | 
			IllegalArgumentException | 
			InvocationTargetException | 
			NoSuchMethodException |
			SecurityException | 
			ClassNotFoundException e
		) {
			e.printStackTrace();
			Misc.loge("啟動失敗!!");
			mainPanel = LogStream.getInstance().showConsole(stg);
		}
		tick = System.currentTimeMillis()-tick;		
		Misc.logv("啟動時間: %dms",tick);	
		PanSplash.done();
	}	
	@Override
	public void stop() throws Exception {
		isKill.set(true);
	}
	
	public static void main(String[] argv) {		
		propPrepare();		
		PanSplash.spawn(argv);		
		Loader.start();
		LogStream.getInstance();
		//liceBind();
		launch(argv);
		//End of Application
		//restore property and waiting daemon thread		
		if(jarName.length()!=0){
			//This is the jar file, try to wait release resource.
			try {
				//let daemon thread have chance to escape its loop.
				Misc.logv("waiting to shutdown...");
				System.gc();
				Thread.sleep(3000);
			}catch(InterruptedException e) { 
			}
		}
		propRestore();
		LogStream.getInstance().close();
	}
	//--------------------------------------------//
	//In this section, we initialize all global variables~~
	public static final String sheet = Gawain.class.
		getResource("res/styles.css").
		toExternalForm() ;
		
	public static final boolean isPOSIX;
	public static final String pathRoot;//Working directory.
	public static final String pathHome;//User home directory.
	public static final String pathSock;//Data or setting directory.
		
	public static final File dirRoot;//Working directory.
	public static final File dirHome;//User home directory.
	public static final File dirSock;//Data or setting directory.
	
	public static final String jarName;
	
	static {
		String txt = null;
		//check operation system....
		txt = System.getProperty("os.name").toLowerCase();
		if(txt.indexOf("win")>=0){
			isPOSIX = false;
		}else{
			isPOSIX = true;
		}
		//check working path
		pathRoot= new File(".").getAbsolutePath() + File.separatorChar;
		dirRoot = new File(pathRoot);
		//check home path, user store data in this directory.
		if(isPOSIX==true){
			txt = System.getenv("HOME");
		}else{
			txt = System.getenv("HOMEPATH");
			txt = "C:"+txt;
		}
		if(txt==null){
			txt = ".";
		}
		pathHome = txt + File.separatorChar;		
		dirHome = new File(pathHome);	
		//check whether self is a jar file or not~~~		
		//cascade sock directory.
		pathSock = pathHome + ".gawain" + File.separatorChar;		
		dirSock = new File(pathSock);
		if(dirSock.exists()==false){
			if(dirSock.mkdirs()==false){
				System.err.printf("we can't create sock-->%s!!\n",pathSock);
				System.exit(-2);
			}
		}
		try {
			File fs = new File(
				Gawain.class.getProtectionDomain()
				.getCodeSource()
				.getLocation()
				.toURI()
			);
			if(fs.isFile()==true) {
				txt = fs.getAbsolutePath();
			}else {
				txt = "";
			}
		} catch (URISyntaxException e) {
			txt = "";
		}
		if(txt.length()>0) {
			jarName = txt;
		}else {
			jarName = "";
		}
	}
}




