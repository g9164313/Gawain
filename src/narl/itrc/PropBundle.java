package narl.itrc;

import java.util.HashMap;
import java.util.Map;

import com.sun.glass.ui.Application;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;

public class PropBundle extends HashMap<String, PropBundle.ObjPack>{

	public enum ObjType {
		TYPE_BOOL, TYPE_INTEGER, TYPE_FLOAT,		
		TYPE_LADDER,
		TYPE_RUNNABLE,
	};
	public static class ObjPack {
		final ObjType type;
		final Object  self;
		public ObjPack(Object argx){
			type= ObjType.TYPE_BOOL;
			self= argx;
		}
		public ObjPack(ObjType arg0, Object arg1){
			type= arg0;
			self= arg1;
		}
	};
	
	public PropBundle(){
	}

	public PropBundle(Object... args){
		int cnt = args.length;
		cnt = cnt - cnt%2;
		for(int i=0; i<cnt; i+=2){
			final Object oa = args[i+0];
			final Object ob = args[i+1];
			if(oa instanceof String){
				final String name = (String)oa;
				if(ob instanceof BooleanProperty){
					pack(name,(BooleanProperty)ob);
				}else if(ob instanceof IntegerProperty){
					pack(name,(IntegerProperty)ob);
				}else if(ob instanceof FloatProperty){
					pack(name,(FloatProperty)ob);
				}else if(ob instanceof Ladder){
					pack(name,(Ladder)ob);
				}else if(ob instanceof Runnable){
					pack(name,(Runnable)ob);
				}else{
					System.out.printf("Unknow object: %s @ %s\n", name, ob.toString());
				}
			}
		}
	}

	public PropBundle pack(final String name, final BooleanProperty obj){
		put(name, new ObjPack(ObjType.TYPE_BOOL,obj));
		return this;
	}
	public PropBundle pack(final String name, final IntegerProperty obj){
		put(name, new ObjPack(ObjType.TYPE_INTEGER,obj));
		return this;
	}
	public PropBundle pack(final String name, final FloatProperty obj){
		put(name, new ObjPack(ObjType.TYPE_FLOAT,obj));
		return this;
	}
	public PropBundle pack(final String name, final Ladder obj){
		put(name, new ObjPack(ObjType.TYPE_LADDER,obj));
		return this;
	}
	public PropBundle pack(final String name, final Runnable obj){
		put(name, new ObjPack(ObjType.TYPE_RUNNABLE,obj));
		return this;
	}

	private String last_json;

	private String find_value(
		final String name, 
		final String txt_json
	){
		if(txt_json==null){
			return "";
		}
		if(txt_json.length()==0){
			return "";
		}
		final int aa = txt_json.indexOf(name);
		if(aa<0){
			return "";
		}
		final int bb = txt_json.indexOf(":", aa);
		if(bb<0){
			return "";
		}
		int cc = txt_json.indexOf(",", bb);
		if(cc<0){
			cc = txt_json.indexOf("}", bb);
		}
		return txt_json.substring(bb+1, cc).trim();
	}

	public String reflect_json(String ref_txt){
		last_json = "{ ";
		for(Map.Entry<String,ObjPack> ee:this.entrySet()){
			final String  name = ee.getKey();
			final ObjPack pack = ee.getValue();
			final String 	tref = find_value(name,ref_txt);

			final Runnable event_reflect = ()->{
				String exp = " ";
				switch(pack.type){
				case TYPE_BOOL:
					BooleanProperty obj1 = (BooleanProperty)(pack.self);
					if(tref.length()!=0 && obj1.isBound()==false){
						obj1.set(Boolean.parseBoolean(tref));
					}
					exp = String.format("\"%s\":%s,", name, obj1.getValue().toString());
					break;
				case TYPE_INTEGER:
					IntegerProperty obj2 = (IntegerProperty)(pack.self);
					if(tref.length()!=0 && obj2.isBound()==false){
						obj2.set(Integer.valueOf(tref));
					}
					exp = String.format("\"%s\":%d,", name, obj2.get());
					break;
				case TYPE_FLOAT:
					FloatProperty obj3 = (FloatProperty)(pack.self);
					if(tref.length()!=0 && obj3.isBound()==false){
						obj3.set(Float.valueOf(tref));
					}
					exp = String.format("\"%s\":%f,", name, obj3.get());
					break;
				case TYPE_LADDER:
					Ladder obj4 = (Ladder)(pack.self);
					if(tref.length()!=0){
						String act = tref.replace('"', ' ').trim().toLowerCase();
						if(act.equals("run") || act.equals("play") || act.equals("start")){
							obj4.start();
						}else if(act.equals("pause")){
							obj4.pause();
						}else if(act.equals("stop") || act.equals("abort")){
							obj4.abort();
						}
					}
					exp = String.format("\"%s\":\"%s\",", name, obj4.getStatusText());
					break;
				case TYPE_RUNNABLE:
					Runnable obj5 = (Runnable)(pack.self);
					if(tref.length()!=0){ obj5.run(); }//only launch task, no readable status!!!
					break;
				}
				last_json = last_json + exp;
			};	
			if(Application.isEventThread()==false){
				Application.invokeAndWait(event_reflect);
			}else{
				event_reflect.run();
			}
		}
		if(last_json.endsWith(",")==true){
			last_json = last_json.substring(0,last_json.length()-1);
		}
		last_json = last_json + " } ";
		return last_json;
	}
}