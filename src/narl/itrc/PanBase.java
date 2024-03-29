package narl.itrc;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXDecorator;
import com.jfoenix.controls.JFXDialog;
import com.jfoenix.controls.JFXSpinner;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public abstract class PanBase {
	
	private Scene scene;
	private Stage stage;
	
	public PanBase(){
		this(null);
	}	
	public PanBase(final Stage stg){
		if(stg==null) {
			stage = new Stage();
		}else {
			stage = stg;
		}
	}	
	//------------------------//
	
	public Parent root() {
		return scene.getRoot();
	}
	public Scene scene() {
		return scene;
	}
	public Stage stage(){ 
		return stage; 
	}
	
	public static PanBase self(final Node obj){
		return (PanBase)(obj.getScene().getUserData());
	}
	
	//default panel node
	//Label txt = new Label("===\n X \n===");
	//txt.setFont(Font.font("Arial", 60));
	//txt.setPrefSize(200, 200);
	//txt.setAlignment(Pos.CENTER);
	//face1 = txt;
	private static final KeyCombination kb_quit = KeyCombination.keyCombination("Ctrl+ESC");
	private static final KeyCombination kb_console1 = KeyCombination.keyCombination("Ctrl+W");
	private static final KeyCombination kb_console2 = KeyCombination.keyCombination("Ctrl+E");

	public void initLayout() {
		final Node face = eventLayout(this);
		//StackPane.setAlignment(face, Pos.BOTTOM_LEFT);
    //StackPane.setAlignment(face, Pos.TOP_RIGHT);
        
    final Pane root = new StackPane(face);
    root.setMinSize(135,137);
		root.getStyleClass().add("background");
		
		if(Gawain.propFlag("JFX_DECORATE")==true) {
			scene = new Scene(new JFXDecorator(stage,root));
		}else {
			scene = new Scene(root);
		}
		scene.setUserData(this);//keep self-pointer		
		scene.getStylesheets().add(Gawain.sheet);//load a default style...
		scene.setFill(Color.WHITE);
		scene.setOnKeyPressed(event->{
			if(kb_console1.match(event)==true) {
				new PanLogger(null).appear();
			}else if(
				kb_console2.match(event)==true &&
				PanBrownie.self.isPresent()==false
			) {
				new PanBrownie(null).appear();
			}else if(
				kb_quit.match(event)==true &&
				Gawain.mainPanel.equals(PanBase.this)==true
			) {
				if(notifyConfirm("！！注意！！","確認關閉主程式？")==ButtonType.OK){
					stage.close();
				}
			}
		});//capture some short-key
	}
	
	private void prepare(){
		if(scene==null) {
			initLayout();
		}		
		stage.setScene(scene);
		if(Gawain.mainPanel==this) {
			if(Gawain.propFlag("PANEL_FULL")==true) {
				stage.setFullScreen(true);
				return;
			}else if(Gawain.propFlag("PANEL_MAX")==true) {
				stage.setMaximized(true);
				return;
			}
		}
		stage.sizeToScene();
		stage.centerOnScreen();
	}
		
	/**
	 * present a new panel, but non-blocking
	 * @return self
	 */
	public PanBase appear(){
		prepare();
		stage.show();
		return this;
	}
	/**
	 * present a new panel, and blocking for dismissing.<p>
	 * Never return to caller.<p>
	 */
	public void standby(){
		prepare();
		stage.showAndWait();		
	}

	/**
	 * Create all objects on panel.<p>
	 * @param self - super class,
	 * @return
	 */
	public abstract Node eventLayout(PanBase self);
	
	public Object[] getOpenAPI(){
		final String txt = new BufferedReader(new InputStreamReader(
			Gawain.class.getResourceAsStream("/narl/itrc/res/openapi.json")
		)).lines().collect(Collectors.joining("\n"));
		return new Object[]{txt,null};
	}
	//----------------------------------------------//
		
	public static class Spinner extends JFXDialog {		
		JFXSpinner icon;
		Label mesg = new Label();
		Label prog = new Label();
		public Spinner(){			
			icon = new JFXSpinner();

			mesg.setMinWidth(100);
			mesg.getStyleClass().add("font-size3");
			mesg.setAlignment(Pos.BASELINE_LEFT);
			
			prog.setMinWidth(33);
			prog.getStyleClass().add("font-size3");
			//prog.setFont(Font.font("Arial", 26));
			prog.setAlignment(Pos.BASELINE_RIGHT);
			
			final HBox lay = new HBox(icon,mesg,prog);
			lay.getStyleClass().addAll("box-pad");
			lay.setAlignment(Pos.CENTER_LEFT);	
			setContent(lay);
		}
		public void setText(final String txt) {
			mesg.setText(txt);
		}
	};
	
	public static interface SpinnerEvent {
		void action(final Timeline ladder,final Spinner dialog);
	};
	
	private final int LADDER_STEP = 900;	
	protected void ladderJump(final Timeline ladder,final int step) {
		ladder.playFromStart();
		if(step==-1) {
			ladder.jumpTo("end");
			return;
		}else if(step<=-2) {
			ladder.jumpTo("start");
			return;
		}
		int stp = step*LADDER_STEP - LADDER_STEP/2;
		ladder.jumpTo(Duration.millis(stp));
	}
	
	/**
	 * use 'Timeline' as stepper-ladder.<p>
	 * For skipping backward visit keyframe, combine 'playFromStart' and 'jumpTo'.<p>
	 * @param event - extend KeyFrame Interface.
	 */
	public void SpinnerLadder(SpinnerEvent... event) {
		final Spinner dialog = new Spinner();
		final Timeline ladder = new Timeline();		
		ladder.setCycleCount(0);
		ladder.setOnFinished(e->dialog.close());
		for(int i=0; i<event.length; i++) {
			final int idx = i;
			ladder.getKeyFrames().add(new KeyFrame(
				Duration.millis((idx+1)*LADDER_STEP),
				e->event[idx].action(ladder,dialog)
			));
		}
		//ladder.playFromStart();
		//ladder.jumpTo(Duration.seconds(1.));
		dialog.setOnDialogOpened(e->ladder.playFromStart());
		dialog.setOnDialogClosed(e->ladder.stop());
		dialog.show((StackPane)root());
	}

	public DevBase SpinnerBreakIn(
		final String txt,
		final DevBase dev,
		final Runnable workStart,
		final Runnable eventShow
	){
		if(workStart==null){
			return dev;
		}
		final Spinner dlg = new Spinner();
		dlg.mesg.setText(txt);		
		dlg.setOnDialogOpened(e1->{
			final Timeline chk = new Timeline();
			chk.getKeyFrames().add(0, new KeyFrame(
				Duration.millis(100.),
				e2->{
					System.out.printf("[Spinner] break-in=%B\n", dev.isBreakOut());
					if(dev.isBreakOut()==true){
						chk.stop();
						dlg.close();
					}
				}
			));
			chk.setCycleCount(Timeline.INDEFINITE);
			chk.play();
			dev.asyncBreakIn(workStart);			
		});
		dlg.setOnDialogClosed(e1->{
			final Timeline chk = new Timeline();
			chk.getKeyFrames().add(0, new KeyFrame(
				Duration.millis(500.),
				e2->eventShow.run()
			));
			chk.setCycleCount(1);
			chk.play();
		});
		dlg.show((StackPane)root());
		return dev;
	}

	/**
	 * show a spinner, let user know we are working.<p>
	 * @param task - a working thread.<p>
	 */
	public Task<?> SpinnerTask(
		final String name,
		final Task<?> task
	) {
		final Spinner dlg = new Spinner();
		dlg.mesg.textProperty().bind(task.messageProperty());
		dlg.prog.textProperty().bind(task.progressProperty().multiply(100.f).asString("%.0f％"));		
		dlg.prog.visibleProperty().bind(task.progressProperty().greaterThan(0.f));
		//override old handler~~~
		final EventHandler<WorkerStateEvent> user_hook = task.getOnSucceeded();
		task.setOnSucceeded(e->{
			if(user_hook!=null) {
				user_hook.handle(e);
			}
			dlg.close();
		});
		task.setOnCancelled(e->dlg.close());
		dlg.setOnDialogOpened(e->new Thread(task,name).start());
		dlg.setOnDialogClosed(e->task.cancel());
		dlg.show((StackPane)root());
		return task;
	}
	//----------------------------------------------//
	
	/**
	 * macro-expansion for indicator.<p>
	 * Check box opacity is 1.<p>
	 * @param title
	 * @return
	 */
	public static CheckBox genIndicator(
		final String title,
		final ReadOnlyBooleanProperty prop
	) {
		JFXCheckBox obj = new JFXCheckBox(title);
		obj.setDisableVisualFocus(true);
		obj.setDisable(true);
		if(prop!=null) {
			obj.selectedProperty().bind(prop);
		}		
		obj.setStyle("-fx-opacity: 1.0;");
		obj.setUserData(prop);//keep it, for rebound
		return obj;
	}
	public static CheckBox genIndicator(final String title) {
		return genIndicator(title,null);
	}
	
	/**
	 * macro-expansion for information alter dialog
	 * @param title
	 * @param message
	 */
	public static ButtonType notifyInfo(
		final String title,
		final String message
	){
		return popup_alter(
			AlertType.INFORMATION,
			title, message,
			null
		);
	}
	/**
	 * macro-expansion for warning alter dialog
	 * @param title
	 * @param message
	 */
	public static ButtonType notifyWarning(
		final String title,
		final String message
	){
		return popup_alter(
			AlertType.WARNING,
			title, message,
			null
		);
	}
	/**
	 * macro-expansion for error alter dialog
	 * @param title 
	 * @param message
	 */
	public static ButtonType notifyError(
		final String title,
		final String message
	){
		return popup_alter(
			AlertType.ERROR,
			title,message,
			null
		);
	}
	public static ButtonType notifyConfirm(
		final String title,
		final String message
	){
		return popup_alter(
			AlertType.CONFIRMATION,
			title,message,
			null
		);
	}
	private static ButtonType popup_alter(
		final AlertType type,
		final String title,
		final String message,
		final Node expand
	){
		Alert dia = new Alert(type);
		dia.setTitle(title);
		dia.setHeaderText(message);
		dia.setContentText(null);
		if(expand!=null){
			dia.getDialogPane().setExpandableContent(expand);
		}
		return dia.showAndWait().get();
	}
	//----------------------------------------------//
	
	protected List<File> chooseFiles(final String title){
		final FileChooser dia = new FileChooser();
		dia.setTitle(title);
		dia.setInitialDirectory(Gawain.dirRoot);
		return dia.showOpenMultipleDialog(stage);
	}

	public File loadFrom(){
		final FileChooser dia = new FileChooser();
		dia.setTitle("讀取檔案...");
		dia.setInitialDirectory(Gawain.dirRoot);
		return dia.showOpenDialog(stage);
	}
	
	public File saveAs(final String default_name){
		final FileChooser dia = new FileChooser();
		dia.setTitle("儲存成為...");
		dia.setInitialFileName(default_name);
		dia.setInitialDirectory(Gawain.dirRoot);
		return dia.showSaveDialog(stage);
	}
	//----------------------------------------------//
	
	public static Node border(
		final String title,
		final Node obj
	){
		Label txt = new Label(title);
		txt.getStyleClass().add("font-size7");
		
		obj.getStyleClass().add("box-border");
		
		VBox lay = new VBox(txt,obj);
		lay.getStyleClass().add("box-pad-group");		
		return lay;
	}
}

