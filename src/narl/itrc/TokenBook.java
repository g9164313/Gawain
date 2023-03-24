package narl.itrc;

import java.util.ArrayList;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * PLC node management. node syntax is like "X0010" or "X0080~X0083".<p>
 * "M0088" can be a bit or word(16-bit) device(register).<p>
 * This is a common object to monitor PLC node and generate control panel.<p>  
 * @author qq
 *
 * @param <T>
 */
public abstract class TokenBook {

	/**
	 * map for token....<p>
	 */
	public static class Wrapper {
		Wrapper(String name){
			key = name;
		}
		public String key = "";
		public final SimpleStringProperty  valTxt = new SimpleStringProperty("????");
		public final SimpleBooleanProperty valFlg = new SimpleBooleanProperty();
		public final SimpleIntegerProperty valInt = new SimpleIntegerProperty();
		public final SimpleFloatProperty   valNum = new SimpleFloatProperty();
		
		public final String getKey() { return key; }
		public final StringProperty valTxtProperty(){ return valTxt; }
	};
	
	public static class Token {
		public Object user_data = null;//this is dependent on protocol
		
		public String name = "";
		public int addr = -1;
		public Wrapper[] wrap = null;//When user need size, take this array length.
		
		public int[]     valInt = null;//boolean as integer
		public float[]   valNum = null;//float value~~~
		
		/**
		 * 
		 * @param radix- index for 10 or 16-base
		 * @param args - node name, start index, last index
		 * @return
		 */
		public Token assign(
			final int radix, 
			String[] args
		) {
			final String fmt = "%s%0"+args[1].length()+"d";			
			name = args[0];
			addr = Integer.valueOf(args[1], radix);
			int cnt = 1;
			if(args[2]!=null) {
				cnt = Integer.valueOf(args[2], radix) - addr + 1;
			}
			wrap = new Wrapper[cnt];
			for(int i=0; i<cnt; i++) {
				wrap[i] = new Wrapper(String.format(fmt, args[0], addr+i));
			}
			return this;
		}
		Token assign10(String[] args) { return assign(10,args); }
		Token assign16(String[] args) { return assign(16,args); }
	};

	public final ArrayList<Token> lst = new ArrayList<Token>();
	
	public abstract Token parseToken(final Token tkn, final String txt);
	
	public abstract void writeBit(String name,final boolean flag);
	
	public String[] split_txt(final String txt) {
		if(txt.matches("[a-zA-Z*]{1,2}\\d+([-~]\\d+)?")==false) {
			return null;
		}
		String[] args = {
			null,//name 
			null,//start address
			null //end of address
		};
		//find the first digital
		int i = 0;
		while (i<txt.length() && !Character.isDigit(txt.charAt(i))) i++;		
		args[0] = txt.substring(0,i);
		//find or check separator
		int j = Math.abs(txt.indexOf('-') * txt.indexOf('~'));
		if(j==1) {
			args[1] = txt.substring(i);//no separator 
		}else {
			args[1] = txt.substring(i,j);//the first address
			args[2] = txt.substring(j+1);
		}		
		return args;
	}
	
	public TokenBook mirror(final String... args) {
		for(String txt:args) {
			String[] cols = split_txt(txt);//node name, start index, last index
			if(cols==null) {
				Misc.logw("[NodeBook] invalid node - %s", txt);
				continue;
			}
			Token tkn = new Token().assign10(cols);
			tkn = parseToken(tkn,txt);
			if(tkn==null) {
				Misc.logw("[NodeBook] reject node - %s", txt);
				continue;
			}
			lst.add(tkn);
		}
		return this;
	}
	
	/**
	 * get property from mirror node, just search one node~
	 * @param arg
	 * @return
	 */
	public SimpleStringProperty searchTxt(final String txt) {
		Wrapper node = search(txt);
		//TODO:!!!!
		return node.valTxt;
	}	
	public SimpleIntegerProperty searchInt(final String txt) {
		Wrapper node = search(txt);
		//TODO:!!!!
		return node.valInt;
	}	
	private Wrapper search(final String txt) {
		String[] cols = split_txt(txt);//node name, start index
		Wrapper dst = null;
		for(Token tkn:lst) {
			if(tkn.name.equals(cols[0])==false) {
				continue;
			}
			final int idx = Integer.valueOf(cols[1]);
			final int beg = tkn.addr;
			final int end = beg + tkn.wrap.length;
			if(beg<=idx && idx<end) {
				dst = tkn.wrap[idx-beg];
				break; 
			}
		}
		return dst;
	}
	
	public final Runnable refresh_property_vent = ()->{
		for(Token nn:lst) {
			for(int i=0; i<nn.wrap.length; i++) {
				String v_txt = "???";
				if(nn.valInt!=null) {
					v_txt = String.format("%06d", nn.valInt[i]);
					nn.wrap[i].valFlg.set(nn.valInt[i]!=0);
					nn.wrap[i].valInt.set(nn.valInt[i]);					
				}else if(nn.valNum!=null) {
					v_txt = String.format("%6.2f", nn.valNum[i]);
					nn.wrap[i].valNum.set(nn.valNum[i]);
				}
				nn.wrap[i].valTxt.set(v_txt);
			}
		}
	};

	@SuppressWarnings("unchecked")
	public Node genCtrlPanel() {
				
		final TableColumn<Wrapper,String> col0 = new TableColumn<>("名稱");
		final TableColumn<Wrapper,String> col1 = new TableColumn<>("數值");
		
		col0.setCellValueFactory(new PropertyValueFactory<Wrapper,String>("key"));
		col0.setMinWidth(50);		
		col1.setCellValueFactory(new PropertyValueFactory<Wrapper,String>("valTxt"));
		col1.setMinWidth(200);
		
		final TableView<Wrapper> lay1 = new TableView<Wrapper>();		
		lay1.getStyleClass().addAll("font-console");
		lay1.setEditable(false);
		lay1.getColumns().addAll(col0,col1);
		
		
		final MenuItem m_itm1 = new MenuItem("active '0'");
		m_itm1.setOnAction(e->{
			Wrapper ww = lay1.getSelectionModel().getSelectedItem();
			writeBit(ww.key,false);
		});
		final MenuItem m_itm2 = new MenuItem("active '1'");
		m_itm2.setOnAction(e->{
			Wrapper ww = lay1.getSelectionModel().getSelectedItem();
			writeBit(ww.key,true);
		});
		final MenuItem m_itm3 = new MenuItem("set value");
		lay1.setContextMenu(new ContextMenu(m_itm1,m_itm2,m_itm3));
		
		//flatten token list
		ObservableList<Wrapper> lst_node = FXCollections.observableArrayList();
		for(Token tkn:lst) {
			lst_node.addAll(tkn.wrap);
		}		
		lay1.setItems(lst_node);
		return lay1; 
	}
}
