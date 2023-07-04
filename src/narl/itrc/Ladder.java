package narl.itrc;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.Scanner;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.sun.glass.ui.Application;

import javafx.animation.Animation;
import javafx.animation.Timeline;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A panel for executing steps one by one by GUI thread.<p>
 * User can arrange step sequence.<p>
 * @author qq
 *
 */
public class Ladder extends BorderPane {
	
	public Ladder(final Orientation dir){

		final JFXButton[] btn = new JFXButton[6];
		for(int i=0; i<btn.length; i++) {
			JFXButton obj = new JFXButton();
			obj.setMaxWidth(Double.MAX_VALUE);
			btn[i] = obj;
		}
		//Import procedure
		btn[0].setText("匯入");
		btn[0].getStyleClass().add("btn-raised-1");
		btn[0].setGraphic(Misc.getIconView("database-import.png"));
		btn[0].disableProperty().bind(sta.isNotEqualTo(0));
		btn[0].setOnAction(e->import_step());
		//Export procedure
		btn[1].setText("匯出");
		btn[1].getStyleClass().add("btn-raised-1");
		btn[1].setGraphic(Misc.getIconView("database-export.png"));
		btn[1].disableProperty().bind(sta.isNotEqualTo(0));
		btn[1].setOnAction(e->export_step());
		//clear all items
		btn[2].setText("清除");
		btn[2].getStyleClass().add("btn-raised-1");
		btn[2].setGraphic(Misc.getIconView("trash-can.png"));
		btn[2].disableProperty().bind(sta.isNotEqualTo(0));
		btn[2].setOnAction(e->recipe.getItems().clear());		
		//Run all steps
		btn[3].setText("執行");
		btn[3].getStyleClass().add("btn-raised-2");
		btn[3].setGraphic(Misc.getIconView("run.png"));
		btn[3].disableProperty().bind(sta.isEqualTo(1));
		btn[3].setOnAction(e->start());
		//Pause the current step~~
		btn[4].setText("暫停");
		btn[4].getStyleClass().add("btn-raised-2");
		btn[4].setGraphic(Misc.getIconView("pause.png"));
		btn[4].disableProperty().bind(sta.isNotEqualTo(1));
		btn[4].setOnAction(e->pause());
		//Stop immediately
		btn[5].setText("停止");
		btn[5].getStyleClass().add("btn-raised-0");
		btn[5].setGraphic(Misc.getIconView("pan_tool.png"));
		btn[5].disableProperty().bind(sta.isEqualTo(0));
		btn[5].setOnAction(e->{abort(); user_abort();});
		
		final double min_w = 200.;
		
		recipe.getStyleClass().addAll("box-pad");
		recipe.setMinWidth(min_w);
		
		final Pane lay_main, lay_step;
		if(dir==Orientation.VERTICAL) {
			lay_main = new VBox(btn);
			lay_step = new VBox();		

			final Accordion accr = new Accordion(
				new TitledPane("操作",lay_main),
				new TitledPane("步驟",lay_step)
			);
			accr.setExpandedPane(accr.getPanes().get(1));
			setLeft(accr);

		}else if(dir==Orientation.HORIZONTAL) {
			lay_main = new HBox(btn);
			lay_step = new FlowPane();			
			
			final Button btn_s = new Button();
			btn_s.setGraphic(Misc.getIconView("skip-next.png"));
			btn_s.setPrefWidth(64.);
			btn_s.setMaxHeight(Double.MAX_VALUE);
			btn_s.setOnAction(e->{
				if(lay_main.visibleProperty().get()==true) {
					lay_main.setVisible(false);
					lay_step.setVisible(true);
				}else {
					lay_main.setVisible(true);
					lay_step.setVisible(false);
				}
			});
			lay_main.setVisible(false);
			
			//final ScrollPane lay0 = new ScrollPane(lay_step);
			//lay0.setMinWidth(min_w);
			//lay0.setPrefHeight(87.);
			setBottom(new HBox(
				btn_s,
				new StackPane(lay_main,lay_step)
			));
		}else {
			return;
		}
		lay_main.getStyleClass().addAll("box-pad");
		lay_step.getStyleClass().addAll("box-pad");
		step_kits = lay_step;
		
		setCenter(recipe);
	}
	public Ladder(){
		this(Orientation.VERTICAL);
	}
	//--------------------------------//
	
	private Pane step_kits;	
	
	private static class ChunkData {
		//final String name;
		final Class<?> clzz;
		final Object[] args;
		public ChunkData(String title, Class<?> clazz, Object[] argument) {
			//name = title;
			clzz = clazz;
			args = argument;
		}
		public Stepper instance(){
			Stepper obj = null;
			try {				
				if(args.length==0){
					obj = (Stepper)clzz.newInstance();
				}else{
					for(Constructor<?> cnst:clzz.getConstructors()){
						if(cnst.getParameterCount()==args.length){
							obj = (Stepper) cnst.newInstance(args);
							break;
						}
					}
				}
				obj = obj.doLayout();
			} catch (InstantiationException | IllegalAccessException e1) {
				e1.printStackTrace();
			} catch (IllegalArgumentException e1) {
				e1.printStackTrace();
			} catch (InvocationTargetException e1) {
				e1.printStackTrace();
			}
			return obj;
		}
	};
	
	public Ladder addStep(
		final String title,
		final Class<?> clazz,
		final Object... argument
	){
		if(step_kits==null) {
			Misc.loge("[Ladder] no step_kits??");
			return this;
		}
		final JFXButton btn = new JFXButton(title);
		btn.getStyleClass().add("btn-raised-3");
		btn.setMaxWidth(Double.MAX_VALUE);
		btn.setUserData(new ChunkData(title,clazz,argument));
		btn.disableProperty().bind(sta.isNotEqualTo(0));		
		btn.setOnAction(e->{
			final JFXButton self = ((JFXButton)e.getSource());
			final ChunkData chuk = (ChunkData)self.getUserData();
			final Stepper step = chuk.instance();
			step.ladder = Optional.of(this);
			recipe.getItems().add(step);
			recipe.scrollTo(step);
		});		
		step_kits.getChildren().add(btn);
		return this;
	}

	public Stepper genStep(final String name){
		if(step_kits==null) {
			return null;
		}
		for(Node nod:step_kits.getChildren()){
			final ChunkData chuk = (ChunkData)nod.getUserData();
			if(chuk.clzz.getName().equals(name)==true){
				return chuk.instance();
			}
		}
		return null;
	}

	//--------------------------------//
	
	protected JFXListView<Stepper> recipe = new JFXListView<Stepper>();

	public Stepper find_next(final Stepper base) {
		final ObservableList<Stepper> lst = recipe.getItems();
		final int idx = lst.indexOf(base);
		if((idx+1)>=lst.size() || idx<0) {
			return null;
		}
		return lst.get(idx+1);	
	}
	
	public Stepper find_from(final Stepper base, final int offset) {
		final ObservableList<Stepper> lst = recipe.getItems();
		int idx = lst.indexOf(base);
		if(idx<0) {
			return null;
		}
		idx += offset;
		if((idx+1)>=lst.size() || idx<0) {
			return null;
		}
		return lst.get(idx);
	}
	
	private void find_first_tailcall(final Animation.Status sta) {
		final ObservableList<Stepper> lst = recipe.getItems();
		for(Stepper ss:lst) {
			if(ss.tailcall.isPresent()==true) {
				Timeline tc =  ss.tailcall.get();
				switch(sta) {
				case RUNNING: tc.play(); break;
				case PAUSED : tc.pause(); break;
				case STOPPED: tc.stop(); break;
				}
				return;
			}
		}
	}
	
	private void rest_all_beacon() {
		final ObservableList<Stepper> lst = recipe.getItems();
		for(Stepper ss:lst) {
			ss.imgBeacon.setVisible(false);
		}
	}
	
	/**
	 * status for ladder buttons
	 * 0: play (include other buttons)
	 * 1: pause, stop
	 * 2: play, stop
	 */
	private final IntegerProperty sta = new SimpleIntegerProperty(0);
	
	public String getStatusText(){
		String txt = "unknow";
		switch(sta.get()){
		case 0: txt="idle"; break;
		case 1: txt="run"; break;
		case 2: txt="pause"; break;
		}
		return txt;
	}

	public void start(){
		if(sta.get()==2) {
			//go into run-state again from pause-state
			sta.set(1);
			find_first_tailcall(Animation.Status.RUNNING);
		}else {
			//take the first running!!!			
			final ObservableList<Stepper> lst = recipe.getItems();
			if(lst.isEmpty()==true) {
				sta.set(0);
				final Alert dia = new Alert(AlertType.INFORMATION);
				dia.setHeaderText("沒有執行項目");
				dia.setContentText("清單內沒有指定執行步驟");
				dia.show();
			}else {
				Misc.logv("[Ladder] 執行階梯圖");
				sta.set(1);
				//initialize the first data!!!
				for(Stepper ss:lst) {
					ss.prepare(); 
				}
				lst.get(0).next_to_first();
			}
		}		
	}
	public void pause() {
		//find the first time and pause it!!!!
		sta.set(2);
		find_first_tailcall(Animation.Status.PAUSED);
	}
	public void abort(){
		Misc.logv("[Ladder] 停止階梯圖");
		sta.set(0);
		find_first_tailcall(Animation.Status.STOPPED);
		rest_all_beacon();
		if(user_abort!=null) { user_abort.run(); }
	}
	//--------------------------------//
	
	public Runnable prelogue = null;	
	public Runnable epilogue = null;
	public Runnable user_abort = null;
	private String uuid_text = "";//every running, we have a new UUID. 
	
	public String uuid() {
		return uuid_text;
	} 
	protected void prelogue() {
		uuid_text = UtilRandom.uuid(6,16);
		if(prelogue!=null) { prelogue.run(); }
	}
	protected void epilogue() {
		if(epilogue!=null) { epilogue.run(); }
		uuid_text = "";
	}
	protected void user_abort() {
		//watchdog.getStatus();
	}
	//--------------------------------//
	
	protected void import_step(){
		final PanBase pan = PanBase.self(this);
		final File fid = pan.loadFrom();
		if(fid==null){
			return;
		}
		if(recipe.getItems().size()!=0) {
			ButtonType btn = PanBase.notifyConfirm("", "清除舊步驟？");
			if(btn==ButtonType.OK) {
				recipe.getItems().clear();
			}
		}
		final Task<?> tsk = new Task<Integer>(){
			@Override
			protected Integer call() throws Exception {
				Scanner stm = new Scanner(fid);
				while(stm.hasNextLine()==true){
					final String txt = stm.nextLine().replace("\r\n","");
					final int pos = txt.indexOf("-->");
					//if(txt.matches(".*[-][-][>].*")==false){
					if(pos<=0){
						updateMessage("無法解析的內文:"+txt);
						continue;
					}					
					final String clzz_name = txt.substring(0, pos);
					final String args_text = txt.substring(pos+1).trim();
					Application.invokeLater(()->{
						Stepper stp = genStep(clzz_name);
						if(stp==null){
							updateMessage("無法產生:"+clzz_name);
							return;
						}
						stp.expand(args_text);
						recipe.getItems().add(stp);
						recipe.scrollTo(stp);
					});
					updateMessage(String.format("匯入 %s", clzz_name));
				}
				stm.close();
				return 3;
			}
		};
		pan.SpinnerTask("匯入階梯圖",tsk);
	}
	
	protected void export_step(){
		final PanBase pan = PanBase.self(this);
		final File fid = pan.saveAs("recipe.txt");
		if(fid==null){
			return;
		}
		final Task<?> tsk = new Task<Integer>(){
			@Override
			protected Integer call() throws Exception {
				ObservableList<Stepper> lst = recipe.getItems();
				FileWriter out = new FileWriter(fid);				
				for(int i=0; i<lst.size(); i++){
					updateMessage(String.format(
						"匯出中 %2d/%2d",
						i+1, lst.size()
					));
					final Stepper stp = lst.get(i);
					out.write(String.format(
						"%s-->%s",
						stp.getClass().getName(),
						stp.flatten()
					));
					out.write("\r\n");
				}				
				out.close();
				return 0;
			}
		};
		pan.SpinnerTask("匯出階梯圖",tsk);		
	}
	//--------------------------------//
}
