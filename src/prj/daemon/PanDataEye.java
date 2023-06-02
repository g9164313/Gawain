package prj.daemon;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTabPane;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.Animation.Status;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import narl.itrc.PanBase;

public class PanDataEye extends PanBase {

	final DevFYx00 fy300 = new DevFYx00("溫度");
	final DevMASMx masm1 = new DevMASMx("電壓");
	final DevMASMx masm2 = new DevMASMx("電流");

	final Timeline worker = new Timeline();

	public PanDataEye(Stage stg) {
		super(stg);
		worker.setCycleCount(-1);
		stg.setOnShown(e -> on_shown());
	}

	public void on_shown() {
		// fy300.open();
		// masm1.open();
		// masm2.open();
		worker.playFromStart();
	}

	@Override
	public Node eventLayout(PanBase self) {

		final LineChart<String,Number> cc1 = gen_line_chart("溫度");
		final LineChart<String,Number> cc2 = gen_line_chart("電壓");
		final LineChart<String,Number> cc3 = gen_line_chart("電流");

		final EventHandler<ActionEvent> event_record = event->{
			final Timestamp tt = new Timestamp(System.currentTimeMillis());
			final String xx = tick_name.format(tt);
			plot_data_point(cc1, xx, (float)Math.random());
			plot_data_point(cc2, xx, (float)Math.random());
			plot_data_point(cc3, xx, (float)Math.random());
		};

		final JFXComboBox<Duration> cmb = new JFXComboBox<Duration>();
		cmb.setConverter(combo2text);
		cmb.getItems().addAll(gen_list_combo());
		cmb.getSelectionModel().select(default_combo_item);
		cmb.setOnAction(e->{
			if(worker.getStatus()!=Status.RUNNING){
				return;
			}
			worker.stop();
			reset_keyframe(cmb,event_record);
			worker.playFromStart();
		});

		final JFXButton btn_export = new JFXButton("匯出");
		btn_export.getStyleClass().add("btn-raised-1");
		btn_export.setMaxWidth(Double.MAX_VALUE);
		btn_export.setOnAction(e->{
			
		});

		final JFXButton btn_sample = new JFXButton("取樣");
		btn_sample.getStyleClass().add("btn-raised-1");
		btn_sample.setMaxWidth(Double.MAX_VALUE);
		btn_sample.disableProperty().bind(worker.statusProperty().isEqualTo(Status.RUNNING));
		btn_sample.setOnAction(e->{
			reset_keyframe(cmb,event_record);
			worker.play();
		});
	
		final JFXButton btn_halt = new JFXButton("停止");
		btn_halt.getStyleClass().add("btn-raised-2");
		btn_halt.setMaxWidth(Double.MAX_VALUE);
		btn_halt.disableProperty().bind(worker.statusProperty().isEqualTo(Status.RUNNING).not());
		btn_halt.setOnAction(e->worker.stop());

		reset_keyframe(cmb,event_record);
		//--------------------------------------
		
		GridPane.setHgrow(cc1, Priority.ALWAYS);
		GridPane.setHgrow(cc2, Priority.ALWAYS);
		GridPane.setHgrow(cc3, Priority.ALWAYS);

		final GridPane lay4 = new GridPane();
		lay4.getStyleClass().addAll("box-pad");
		lay4.addRow(0, DevFYx00.genInfoPanel(fy300), cc1);
		lay4.add(new Separator(), 0, 1, 2, 1);
		lay4.addRow(2, DevMASMx.genInfoPanel(masm1), cc2);
		lay4.add(new Separator(), 0, 3, 2, 1);
		lay4.addRow(4, DevMASMx.genInfoPanel(masm2), cc3);

		final VBox lay3 = new VBox(cmb, btn_export, btn_sample, btn_halt);
		lay3.getStyleClass().addAll("box-pad");

		final BorderPane lay2 = new BorderPane();
		lay2.setLeft(lay3);
		lay2.setCenter(lay4);
		//--------------------------------------

		final JFXTabPane lay1 = new JFXTabPane();
		lay1.getTabs().addAll(
			new Tab("監測",lay2)
			//new Tab("調閱",lay2)
		);
		lay1.getSelectionModel().select(1);
		return lay1;		
	}
	// --------------------------------------

	private void reset_keyframe(
		final JFXComboBox<Duration> cmb,
		final EventHandler<ActionEvent> event
	){
		event.handle(null);
		worker.getKeyFrames().clear();
		worker.getKeyFrames().add(new KeyFrame(
			cmb.getSelectionModel().getSelectedItem(),
			event
		));	
	}

	private void plot_data_point(
			final LineChart<String, Number> chart,
			final String xx,
			final float yy
		) {
		XYChart.Series<String, Number> ss;
		if (chart.getData().size() == 0) {
			ss = new XYChart.Series<>();
			ss.setName("----");
			chart.getData().add(ss);
		} else {
			ss = chart.getData().get(0);
		}
		ss.getData().add(new XYChart.Data<String, Number>(xx, yy));
		if (ss.getData().size() >= 60) {
			ss.getData().remove(0);
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
		obj.setMinSize(300., 100.);
		obj.setMaxSize(Double.MAX_VALUE, 300.);
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
}
