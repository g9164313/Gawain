package prj.daemon;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTabPane;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.Animation.Status;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import narl.itrc.Gawain;
import narl.itrc.Misc;
import narl.itrc.PanBase;
import narl.itrc.Stepper;

public class PanDataEye extends PanBase {

	final DevFYx00 sens1 = new DevFYx00();
	final DevFYx00 sens2 = new DevFYx00();
	final DevMASMx masm1 = new DevMASMx("MA-SM3:volt");
	final DevMASMx masm2 = new DevMASMx("MA-SM3:amp-");

	final Timeline recorder = new Timeline();
	final String TAG_REC = "[rec]";
	private String rec_tick = "";

	public PanDataEye(Stage stg) {
		super(stg);
		recorder.setCycleCount(-1);
		stg.setOnShown(e -> on_shown());
	}

	public void on_shown() {
		final String path = Gawain.prop().getProperty("BUS1","");
		sens1.open(path , 1);
		sens2.open(sens1, 2);
		masm1.open(sens1,11);
		masm2.open(sens1,12);
		//recorder.playFromStart();
	}

	@Override
	public Node eventLayout(PanBase self) {

		final LineChart<String,Number> cc1_2= gen_line_chart("溫度");
		final LineChart<String,Number> cc11 = gen_line_chart("電壓");
		final LineChart<String,Number> cc12 = gen_line_chart("電流");

		final EventHandler<ActionEvent> event_record = event->{
			final Timestamp tt = new Timestamp(System.currentTimeMillis());
			final String xx = tick_name.format(tt);
			plot_data_point(cc1_2, xx, sens1.pv_val.get(), sens2.pv_val.get());
			plot_data_point(cc11, xx, masm1.num_display.get());
			plot_data_point(cc12, xx, masm2.num_display.get());
			Misc.logv(String.format("%s %s, %s, %s, %s",
				TAG_REC,
				sens1.pv_txt.get(), sens2.pv_txt.get(),
				masm1.txt_display.get(), masm2.txt_display.get()
			));
		};

		final JFXComboBox<Duration> cmb_period = new JFXComboBox<Duration>();
		cmb_period.setConverter(combo2text);
		cmb_period.getItems().addAll(gen_list_combo());
		cmb_period.getSelectionModel().select(default_combo_item);
		cmb_period.setOnAction(e->{
			if(recorder.getStatus()!=Status.RUNNING){
				return;
			}
			recorder.stop();
			reset_keyframe(cmb_period,event_record);
			recorder.playFromStart();
		});

		final JFXButton btn_clear = new JFXButton("清除");
		btn_clear.getStyleClass().add("btn-raised-1");
		btn_clear.setMaxWidth(Double.MAX_VALUE);
		btn_clear.setOnAction(e->{
			cc1_2.getData().clear();
			cc11.getData().clear();
			cc12.getData().clear();
		});

		final JFXButton btn_sample = new JFXButton("紀錄");
		btn_sample.getStyleClass().add("btn-raised-1");
		btn_sample.setMaxWidth(Double.MAX_VALUE);
		btn_sample.disableProperty().bind(recorder.statusProperty().isEqualTo(Status.RUNNING));
		btn_sample.setOnAction(e->{
			rec_tick = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now().minusSeconds(3));			
			reset_keyframe(cmb_period,event_record);
			recorder.play();
		});

		final JFXButton btn_s_done = new JFXButton("停止");
		btn_s_done.getStyleClass().add("btn-raised-2");
		btn_s_done.setMaxWidth(Double.MAX_VALUE);
		btn_s_done.disableProperty().bind(recorder.statusProperty().isEqualTo(Status.RUNNING).not());
		btn_s_done.setOnAction(e->{
			recorder.stop();
		});
		
		final JFXButton btn_export = new JFXButton("匯出");
		btn_export.getStyleClass().add("btn-raised-1");
		btn_export.setMaxWidth(Double.MAX_VALUE);
		btn_export.setOnAction(e->{
			if(rec_tick.length()==0){
				notifyConfirm("", "還未開始紀錄");
				return;
			}
			final FileChooser dia = new FileChooser();
			dia.setTitle("匯出試算表");
			dia.setInitialDirectory(Gawain.dirRoot);
			final File fs = dia.showSaveDialog(scene().getWindow());
			if(fs==null) {
				return;
			}
			SpinnerTask("匯出",new TskExport(fs));
		});
		//--------------------------------------

		final VBox lay3 = new VBox(
			new Label("取樣週期"), cmb_period, 
			btn_sample, btn_s_done, 
			btn_export, btn_clear
		);
		lay3.getStyleClass().addAll("box-pad");

		final GridPane lay4 = new GridPane();
		lay4.getStyleClass().addAll("box-pad");
		lay4.addRow(0, 
			Misc.addBorder(DevFYx00.genInfoPanel("溫度.1",sens1)), 
			Misc.addBorder(DevMASMx.genInfoPanel("電壓",masm1))
		);
		lay4.addRow(1, 
			Misc.addBorder(DevFYx00.genInfoPanel("溫度.2",sens2)), 
			Misc.addBorder(DevMASMx.genInfoPanel("電流",masm2))
		);

		final BorderPane lay2 = new BorderPane();
		lay2.setLeft(lay3);
		lay2.setCenter(lay4);
		//--------------------------------------

		final VBox lay5 = new VBox(cc1_2,cc11,cc12);
		lay5.prefHeightProperty().bind(lay4.heightProperty());

		//final Ladder lay6 = new Ladder();
		//lay6.prelogue = ()->btn_sample.getOnAction().handle(null);
		//lay6.epilogue = ()->btn_s_done.getOnAction().handle(null);
		//lay6.addStep("溫度控制.1", AdjustTemp.class, sens1);
		//lay6.addStep("溫度控制.2", AdjustTemp.class, sens2);
		//--------------------------------------

		final JFXTabPane lay1 = new JFXTabPane();
		lay1.getTabs().addAll(
			new Tab("監測",lay2),
			new Tab("歷史",lay5)
		);
		lay1.getSelectionModel().select(0);
		return lay1;		
	}
	//--------------------------------------

	private void reset_keyframe(
		final JFXComboBox<Duration> cmb,
		final EventHandler<ActionEvent> event
	){
		event.handle(null);
		recorder.getKeyFrames().clear();
		recorder.getKeyFrames().add(new KeyFrame(
			cmb.getSelectionModel().getSelectedItem(),
			event
		));	
	}

	private void plot_data_point(
			final LineChart<String, Number> chart,
			final String xx,
			final float... yy
	) {
		for(int i=0; i<yy.length; i++){
			XYChart.Series<String, Number> ss;
			if (chart.getData().size()<yy.length) {
				ss = new XYChart.Series<>();
				ss.setName(String.format("SIG%d",i));
				chart.getData().add(ss);
			} else {
				ss = chart.getData().get(i);
			}
			ss.getData().add(new XYChart.Data<String, Number>(xx, yy[i]));
			if(ss.getData().size()>=100) {
				ss.getData().remove(0);
			}
		}
	}
	
	private static final SimpleDateFormat tick_name = new SimpleDateFormat("HH:mm:ss");

	LineChart<String, Number> gen_line_chart(final String txt_y) {

		final CategoryAxis xx = new CategoryAxis();
		xx.setLabel("時間");

		final NumberAxis yy = new NumberAxis();
		yy.setLabel("數值");
		yy.setTickMarkVisible(true);
		yy.setTickUnit(0.5);

		LineChart<String, Number> obj = new LineChart<String, Number>(xx, yy);
		obj.setMinSize(200., 90.);
		//obj.setMaxSize(Double.MAX_VALUE, 200.);
		obj.setAnimated(false);
		obj.setLegendVisible(false);
		return obj;
	}
	//--------------------------------------

	private static final Object[][] lst_combo = {
		{	Duration.seconds( 1), " 1 秒", },
		{	Duration.seconds( 3), " 3 秒", },
		{	Duration.seconds( 5), " 5 秒", },
		{	Duration.seconds(10), "10 秒", },
		{	Duration.seconds(15), "15 秒", },
		{	Duration.minutes( 1), " 1分鐘", },
		{	Duration.minutes( 5), " 5分鐘", },
		{	Duration.minutes(10), "10分鐘", },
		{	Duration.minutes(15), "15分鐘", },
	};

	private static final Duration default_combo_item = (Duration)lst_combo[1][0];

	private static final StringConverter<Duration> combo2text = new StringConverter<Duration>(){
		@Override
		public String toString(Duration obj) {
			for(int i=0; i<lst_combo.length; i++){
				if(obj.equals(lst_combo[i][0])==true){
					return (String)(lst_combo[i][1]);
				}
			}
			return obj.toString();
		}
		@Override
		public Duration fromString(String string) {
			return null;
		}
	};

	private final Duration[] gen_list_combo(){
		Duration[] lst = new Duration[lst_combo.length];
		for(int i=0; i<lst_combo.length; i++){
			lst[i] = (Duration)lst_combo[i][0];
		}
		return lst;
	}
	//--------------------------------------

	public static class AdjustTemp extends Stepper {
		DevFYx00 dev;
		public AdjustTemp(DevFYx00 device){
			dev = device;
			chain(op0,op1,op2);
		}
		final Label txt_proc = new Label("----");
		final Label txt_left = new Label("--:--:--");
		final TextField box_setv = new TextField("100");
		final TextField box_time = new TextField("1:00");

		final Runnable op0 = ()->{
			final String txt = box_setv.getText();
			final float val = Float.valueOf(txt);
			txt_proc.setText("write");
			dev.asyncBreakIn(()->{
				dev.writeForce(0x0000,(short)(val*10.));	
			},()->{
				txt_proc.setText("done!");
				next_to(this.op1);
			});
		};
		final Runnable op1 = ()->{
			String pv_txt = dev.pv_txt.get();
			txt_proc.setText(pv_txt);			
			try{
				float pv = Float.valueOf(dev.pv_val.get());
				float sv = Float.valueOf(box_setv.getText());
				if(pv>=sv){
					box_time.setUserData(System.currentTimeMillis());
					next_to(this.op2);
					return;
				}
			}catch(NumberFormatException e){	
				System.err.println(e.getMessage());
			}
			next_to(this.op1);	
		};
		final Runnable op2 = ()->{
			final long dd = Misc.text2tick(box_time.getText());
			final long t0 = (long)box_time.getUserData();
			final long t1 = System.currentTimeMillis();
			final long xx = t1-t0;
			txt_left.setText(Misc.tick2text(xx,false,3));
			if(xx<dd){				
				next_to(this.op2);
			}else{
				jump();//finally, jump to the next stepper
			}
		};

		@Override
		public Node getContent() {
			txt_proc.setPrefWidth(80.);
			txt_left.setPrefWidth(80.);
			box_setv.setPrefWidth(80.);
			box_time.setPrefWidth(80.);
			final GridPane lay = new GridPane();
			lay.getStyleClass().addAll("box-pad");
			lay.addRow(0, 
				new Label("SV:"), txt_proc, box_setv,
				new Separator(Orientation.VERTICAL),
				new Label("HOLD:"), txt_left, box_time
			);
			return lay; 
		}
		@Override
		public void eventEdit() {
		}
		@Override
		public String flatten() {
			return control2text(box_setv,box_time);
		}
		@Override
		public void expand(String txt) {
			text2control(txt,box_setv,box_time);
		}
	}

	//--------------------------------------

	private class TskExport extends Task<Integer>{
		final File fs;
		TskExport(File output){
			fs = output;
		}
		@Override
		protected Integer call() throws Exception {
			if(Misc.LoggerCon==null){
				return -1;
			}
			Statement sta = Misc.LoggerCon.createStatement();
			ResultSet rs = sta.executeQuery("SELECT stmp,msg FROM logger WHERE stmp>='"+rec_tick+"'");
			final Workbook wb = WorkbookFactory.create(false);
			final Sheet sh = wb.createSheet("紀錄表");
			int yy = 0;
			while(rs.next()==true){
				final Timestamp stmp = rs.getTimestamp(1);
				final String txt = rs.getString(2);
				if(txt.indexOf(TAG_REC)<0){
					continue;
				}
				String[] cols = txt.replace(TAG_REC,"").trim().split(",");
				Row rr = sh.createRow(yy);
				rr.createCell(0).setCellValue(stmp.toString());//time-stamp
				rr.createCell(1).setCellValue(cols[0]);//溫度計.1
				rr.createCell(2).setCellValue(cols[1]);//溫度計.1
				rr.createCell(3).setCellValue(cols[2]);//電壓
				rr.createCell(4).setCellValue(cols[3]);//電流
				yy+=1;
				updateMessage("匯出紀錄：");
			}
			FileOutputStream stm = new FileOutputStream(fs);
			wb.write(stm);
			stm.close();
			return 0;
		}
	};
}
