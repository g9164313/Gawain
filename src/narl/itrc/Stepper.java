package narl.itrc;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Optional;

import com.jfoenix.controls.JFXButton;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;

public abstract class Stepper extends HBox {
	
	private static final Image v_id_arrow = Misc.getIconImage("arrow-right.png");
	private static final Image v_edit_pen = Misc.getIconImage("pen.png");
	private static final Image v_tash_can = Misc.getIconImage("trash-can.png");	
	private static final Image v_cube_out = Misc.getIconImage("cube-outline.png");

	public final ImageView imgBeacon = new ImageView(v_id_arrow);

	private JFXButton btnEdit = new JFXButton();
	private JFXButton btnDrop = new JFXButton();
	
	public void prepare() { }
	public abstract Node getContent();
	//let user pop a panel to edit parameter~~~
	public abstract void eventEdit();
	public abstract String flatten();
	public abstract void expand(String txt);

	public Stepper(){
		
		imgBeacon.setVisible(false);
		
		setOnDragDetected(e->{
			//Misc.logv("setOnDragDetected");
			//'Drag-board' must have content!!!
			Dragboard db = startDragAndDrop(TransferMode.MOVE);
			ClipboardContent content = new ClipboardContent();
			content.putString(Stepper.this.toString());
			db.setDragView(v_cube_out);
            db.setContent(content);
            
            e.consume();
		});
		setOnDragOver(e->{
			//'getGestureSource()' is equal to object in 'TransferMode.MOVE'
			//'getSource()' is equal to object when mouse over
			if (e.getGestureSource() != this) {
            	//Misc.logv(e.getGestureSource().toString()+" over "+e.getSource().toString());
            	e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
		});
		setOnDragDropped(e->{
			if(ladder.isPresent()==false) {
				return;
			}
			Stepper dst = (Stepper)e.getSource();
			Stepper src = (Stepper)e.getGestureSource();
			if(src==null) {
				e.consume();
				return;
			}
			//Misc.logv(src.toString() + " dropped in " + dst.toString());			
			ObservableList<Stepper> lst = ladder.get().recipe.getItems();
			lst.remove(src);
			lst.add(lst.indexOf(dst)+1, src);
            e.setDropCompleted(true);
            e.consume();
		});
		setOnDragDone(DragEvent::consume);	
	}
	
	public Stepper doLayout(){
		
		imgBeacon.setVisible(false);
		
		Node cntx = getContent();
		if(cntx==null) {
			cntx = new Label(this.toString());//dummy~~~
		}
		HBox.setHgrow(cntx, Priority.ALWAYS);
		
		btnEdit.setGraphic(new ImageView(v_edit_pen));
		btnEdit.setOnAction(e->eventEdit());
		
		btnDrop.setGraphic(new ImageView(v_tash_can));		
		btnDrop.setOnAction(e->{
			if(ladder.isPresent()==false) {
				return;
			}
			ladder.get().recipe.getItems().remove(this);
		});
	
		setAlignment(Pos.CENTER_LEFT);
		getChildren().addAll(imgBeacon,cntx,btnEdit,btnDrop);
		return this;
	}

	//example from StackOverflow:
	//Stream<?> lst1 = Arrays.stream(works.get());
	//Stream<?> lst2 = Arrays.stream(runs);
	//Stream.concat(lst1,lst2).toArray(Runnable[]::new)	
	//-----------------------------------//

	public Optional<Timeline> tailcall = Optional.empty();
	
	public Optional<Ladder> ladder = Optional.empty();
	
	private Optional<Runnable> fst_work = Optional.empty();
	
	private final LinkedList<Runnable> lst_work = new LinkedList<Runnable>();
	
	protected Stepper first_work(Runnable run) {
		fst_work = Optional.of(run);
		return this;
	}	
	protected Stepper chain(Runnable... run) {
		if(fst_work.isPresent()==false) {
			first_work(run[0]);
		}
		lst_work.addAll(Arrays.asList(run));
		return this;
	}
	/**
	 * re-arrange and circle queue until meeting the first work 
	 * @param fst - the head of queue
	 */
	private void cycle_queue(final Runnable fst,int idx) {
		if(lst_work.contains(fst)==false) {
			return;
		}
		if(idx<0) {
			idx = lst_work.size() - idx;
		}
		if(idx>=lst_work.size()) {
			idx = idx % lst_work.size();
		}
		while(lst_work.indexOf(fst)!=idx) {
			lst_work.addLast(lst_work.pollFirst());
		}
	}
	protected void next_from(final Runnable base) {
		cycle_queue(base,-1);
		next();
	}
	protected void next_to(final Runnable base) {
		cycle_queue(base,0);
		next();
	}
	protected void next() {
		if(lst_work.isEmpty()==true) {
			System.err.printf("[Stepper][WARNING] no works\n%s",Misc.get_trace());
			jump();//no work in list, just jump to next stepper~~~~
		}else {
			final Runnable run = lst_work.pollFirst();
			trig(run);
			lst_work.addLast(run);
		}		
	}
	
	public void first_trig() {
		imgBeacon.setVisible(true);		
		if(lst_work.isEmpty()==true || fst_work.isPresent()==false) {
			System.err.printf("[Stepper][WARNING] queue hs no work\n%s",Misc.get_trace());
			jump();
		}else {
			next_to(fst_work.get());
		}
	}
	
	/**
	 * trigger or launch a runnable work.(internally, inside, within self-stepper).<p>
	 * @param delay
	 * @param event
	 */
	protected void trig(
		final Duration delay,		
		final Runnable event		
	) {
		if(ladder.isPresent()==false) {
			System.err.printf("[Stepper][WARNING] no ladder!!!\n%s",Misc.get_trace());
			return;
		}
		imgBeacon.setVisible(true);
		Timeline tc = new Timeline(new KeyFrame(
			delay,
			e->{
				cycle_queue(event,-1);
				event.run();
			}
		));//tail call!!!!
		tc.setCycleCount(1);
		tc.setDelay(Duration.millis(100));
		tc.playFromStart();
		tailcall = Optional.of(tc);
	}
	protected void trig(final Runnable event) {
		if(event==null) {
			jump();
		}else {
			trig_millis(100,event);//default delay trigger~~~
		}
	}
	protected void trig_millis(final int val,final Runnable event) {
		trig(Duration.millis(val),event);
	}
	protected void trig_second(final int val,final Runnable event) {
		trig(Duration.seconds(val),event);
	}

	/**
	 * jump to the next 'stepper'!!!
	 * @param stepper
	 */
	protected void jump(final Stepper stepper) { trig(()->{
		//reset old(current) tail-call and indicator
		imgBeacon.setVisible(false);
		tailcall = Optional.empty();
		//jump to next stepper!!!
		if(stepper==null) {
			//end of all step!!!!			
			ladder.get().abort();
		}else {
			stepper.first_trig();
		}
	});}
	protected void jump(final int off) {
		jump(ladder.get().find_from(this,off));
	}	
	protected void jump() {
		jump(ladder.get().find_next(this));
	}	
	protected void abort() { jump(null); }
	
	//-----------------------------------//

	private long tick = -1L;	
	protected long waiting_time(long msec){
		if(tick<=0L){
			tick = System.currentTimeMillis();
		}
		long pass = System.currentTimeMillis() - tick;
		if(pass>=msec){
			reset_waiting();//reset for next turn~~~
			//next_step();
		}else{
			//hold_step();
		}
		long rem = msec - pass;
		return (rem>0)?(rem):(0);
	}
	protected void reset_waiting() {
		tick = -1L;
	}
	protected long waiting_time(final String time){
		return waiting_time(Misc.text2tick(time));
	}
	public Runnable run_waiting(
		final long msec,
		final Label mesg
	) {
		final Runnable obj = ()->{
			final long rem = waiting_time(msec);
			if(mesg!=null) {
				mesg.setText(Misc.tick2text(rem, true));
			}			
		};
		return obj;
	}
	public Runnable run_waiting(
		final String time,
		final Label mesg
	) {
		return run_waiting(Misc.text2tick(time), mesg);
	}
	
	//Misc.tick2text(rem, true)
	//Misc.text2tick(time)
	
	public void wait_async() {}
	public void notify_async() {}
	//-----------------------------------//
	
	public String control2text(Object... lst) {
		String txt = "";
		int idx = 0;
		for(Object obj:lst) {
			String val = "";
			if(obj instanceof TextField) {
				val = ((TextField)obj).getText();
			}else if(obj instanceof ComboBox<?>) {
				val = ""+((ComboBox<?>)obj)
					.getSelectionModel()
					.getSelectedIndex();
			}else if(obj instanceof CheckBox) {
				val = (((CheckBox)obj).isSelected())?("T"):("F");
			}else if(obj instanceof RadioButton) {
				val = (((RadioButton)obj).isSelected())?("T"):("F");
			}else {
				Misc.loge("[FLATTEN] ?? %s", obj.getClass().getName());
				continue;
			}
			txt = txt + String.format("arg%d=%s, ", idx, val);
			idx+=1;
		}
		//remove last dot~~~
		txt = txt.trim();
		if(txt.lastIndexOf(',')==(txt.length()-1)) {
			txt = txt.substring(0,txt.length()-1);
		}
		return txt;
	}
	protected void text2control(String txt, Object... lst) {
		String[] col = txt.trim().replace("\\s", "").split(", ");
		if(col.length==0) {
			return;
		}
		//remove last dot~~~
		String tmp = col[col.length-1];
		if(tmp.charAt(tmp.length()-1)==',') {
			col[col.length-1] = tmp.substring(0,tmp.length()-1);
		}
		//fill data~~~~
		final int cnt = (col.length>=lst.length)?(lst.length):(col.length);
		for(int i=0; i<cnt; i++) {
			final String[] map = col[i].split("=");
			//final String key = arg[0];
			if(map.length==1) {
				continue;//just empty
			}
			final String val = map[1];
			final Object obj = lst[i];
			if(obj instanceof TextField) {
				((TextField)obj).setText(val);
			}else if(obj instanceof ComboBox<?>) {
				((ComboBox<?>)obj)
					.getSelectionModel()
					.select(Integer.parseInt(val));
			}else if(obj instanceof CheckBox) {
				if(val.charAt(0)=='T') {
					((CheckBox)obj).setSelected(true);
				}else if(val.charAt(0)=='F') {
					((CheckBox)obj).setSelected(false);
				}else {
					Misc.loge("[UNFLATEN:CheckBox] %s ? boolean", col[i]);
				}
			}else if(obj instanceof RadioButton) {
				if(val.charAt(0)=='T') {
					((RadioButton)obj).setSelected(true);
				}else if(val.charAt(0)=='F') {
					((RadioButton)obj).setSelected(false);
				}else {
					Misc.loge("[UNFLATEN:RadioButton] %s ? boolean", col[i]);
				}
			}else {
				Misc.loge("[UNFLATEN] ?? %s", obj.getClass().getName());
				continue;
			}
		}
	}	

	//--------below lines are common stepper for ladder--------//
	
	public static class Counter extends Stepper {
		int off = 0;
		int cnt = 0; 
		final Label txt_arg1 = new Label();
		final Label txt_arg2 = new Label();
		final TextField arg1 = new TextField("1");//jump back or forward to....
		final TextField arg2 = new TextField("1");//repeat counter
		
		public Counter() {
			chain(jump);//it must be atomic operation!!
		}
		private void update_msg() {
			txt_arg1.setText(String.format("回跳(%02d)", off));
			txt_arg2.setText(String.format("重複(%02d)", cnt));
		}
		@Override
		public void prepare() { 
			try {
				System.out.printf("~counter:prepare~");
				off = Integer.valueOf(arg1.getText());
				cnt = Integer.valueOf(arg2.getText());				
			}catch(NumberFormatException e) {
				txt_arg1.setText("");
				txt_arg2.setText("");
			}
		}
		final Runnable jump = ()->{			
			cnt-=1;
			if(cnt>=0) {				
				jump(-1*off);//jump backward
			}else {
				jump();//jump to next stepper
			}			
			update_msg();
		};
		@Override
		public Node getContent(){
			update_msg();
			arg1.setPrefWidth(60);
			arg2.setPrefWidth(60);
			//GridPane lay = new GridPane();
			//lay.getStyleClass().addAll("box-pad");
			//lay.add(new Separator(Orientation.VERTICAL), 1, 0, 1, 2);
			//lay.addRow(0, txt_arg1, arg1);
			//lay.addRow(1, txt_arg2, arg2);
			HBox lay = new HBox(txt_arg1, arg1, txt_arg2, arg2);
			lay.getStyleClass().addAll("box-pad");
			lay.setAlignment(Pos.BASELINE_CENTER);
			return lay;
		}
		@Override
		public void eventEdit(){
		}
		@Override
		public String flatten() {
			return control2text(arg1,arg2);
		}
		@Override
		public void expand(String txt) {
			text2control(txt,arg1,arg2);
		}
	};
	//--------------------------------------------//
	
	private static String default_stick_text = "";
	
	public static class Sticker extends Stepper {
		private final Label msg = new Label();
		
		public Sticker setValues(String txt) {
			msg.setText(txt);
			return this;
		}		
		@Override
		public Node getContent(){
			chain(()->{
				String txt = msg.getText();
				if(txt.length()==0){
					txt = "STICKER";
				}
				Misc.logv(">> %s <<", txt);
				jump();
			});
			msg.getStyleClass().add("font-size3");
			Separator ss1 = new Separator();
			Separator ss2 = new Separator();
			HBox.setHgrow(ss1, Priority.ALWAYS);
			HBox.setHgrow(ss2, Priority.ALWAYS);
			HBox lay = new HBox(ss1,msg,ss2);
			lay.setAlignment(Pos.CENTER);
			return lay;
		}
		@Override
		public void eventEdit(){
			String init_text = msg.getText();
			if(init_text.length()==0) {
				init_text = default_stick_text;
			}
			TextInputDialog dia = new TextInputDialog(init_text);
			//dia.setTitle("Text Input Dialog");
			//dia.setHeaderText("Look, a Text Input Dialog");
			dia.setContentText("內容:");
			Optional<String> res = dia.showAndWait();
			if (res.isPresent()){
				default_stick_text = res.get();
				msg.setText(default_stick_text);			   
			}
		}
		public Sticker editValue(final String txt){
			msg.setText(txt);	
			return this;
		}
		@Override
		public String flatten() {
			final String txt = msg.getText();
			if(txt.length()==0){
				return "";
			}
			return String.format("msg:%s",txt);
		}
		@Override
		public void expand(String txt) {
			if(txt.matches("([^:,\\s]+[:][^:,]+[,]?[\\s]*)+")==false){
				return;
			}
			String[] arg = txt.split(":|,");
			msg.setText(arg[1]);
		}
	};	
}
