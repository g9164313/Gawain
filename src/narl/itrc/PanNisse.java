package narl.itrc;

import java.util.Optional;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class PanNisse extends PanBase {
	
	public static Optional<PanNisse> self = Optional.empty();

	public PanNisse(Stage stg){
		super(stg);
		if(self.isPresent()==true){
			self.get().stage().close();
		}
		self = Optional.of(this);
		//stage().setOnShown(null);
		stage().setOnCloseRequest(e->event_shutdown());
	}

	private Optional<TaskBootstrap> opt_stub = Optional.empty();
	
	private void event_shutdown(){
		if(opt_stub.isPresent()==false){
			return;
		}
		opt_stub.get().shutdown();
	}

	@Override
	public Node eventLayout(PanBase self) {
		
		final JFXTextField name = new JFXTextField("主機IP：");
		name.setText("http://localhost:80");
		name.setMaxWidth(Double.MAX_VALUE);

		final Label info = new Label();

		final JFXButton btn_link = new JFXButton("連線");
		btn_link.setMaxWidth(Double.MAX_VALUE);
		btn_link.setOnAction(e->{
			if(opt_stub.isPresent()==true){
				return;
			}
			final EventHandler<WorkerStateEvent> end_of_task = hh->{
				opt_stub=Optional.empty();
				info.textProperty().unbind();
			};

			final TaskBootstrap tsk = new TaskBootstrap();			
			tsk.setOnCancelled(end_of_task);
			tsk.setOnFailed   (end_of_task);
			tsk.setOnSucceeded(end_of_task);
			info.textProperty().bind(tsk.messageProperty());

			Thread th = new Thread(tsk,"Http-MainServer");
			th.setDaemon(true);
			th.start();
			opt_stub = Optional.of(tsk);
		});

		final JFXButton btn_break = new JFXButton("斷線");
		btn_break.setMaxWidth(Double.MAX_VALUE);
		btn_break.setOnAction(e->event_shutdown());

		final VBox lay1 = new VBox(name,info,btn_link,btn_break);
		lay1.setAlignment(Pos.BASELINE_LEFT);		
		lay1.getStyleClass().addAll("box-pad");
		return lay1;
	}
	//---------------------------------------

	private class QQHand extends SimpleChannelInboundHandler<HttpObject> {
		//@Override
    //public void channelReadComplete(ChannelHandlerContext ctx) {
    //    ctx.flush();
    //}		
		@Override
		protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
			
			if(msg instanceof HttpRequest){
				HttpRequest req = (HttpRequest)msg;

			}else if(msg instanceof HttpContent){
				DefaultLastHttpContent content = (DefaultLastHttpContent) msg;

				ByteBuf buf = content.content();
				System.out.printf("I got %s\n",buf.toString(CharsetUtil.UTF_8));

				DefaultFullHttpResponse ans = new DefaultFullHttpResponse(
					HttpVersion.HTTP_1_1,	
					HttpResponseStatus.OK,
					Unpooled.wrappedBuffer("i got it!!!".getBytes(CharsetUtil.UTF_8))
				);
				ans.headers()
					.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
					.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
					.set(HttpHeaderNames.CONTENT_LENGTH, ans.content().readableBytes());
				ctx.write(ans);
				ctx.flush();
			}else{
				System.out.printf(
					"---Unknow Object---\n"+
					"%s\n"+
					"-------------------\n",msg.toString()
				);
			}
		}
	};

	private class TaskBootstrap extends Task<Integer> {
		
		EventLoopGroup bosses, worker;
		ServerBootstrap bs;
		Channel ch;

		/*private SimpleChannelInboundHandler<?> handler1 = new SimpleChannelInboundHandler<FullHttpRequest>(){
			@Override
			protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse obj) throws Exception {
				if(msg instanceof HttpRequest){

				}
			}
		};

		private SimpleChannelInboundHandler<?> handler2 = new SimpleChannelInboundHandler<FullHttpMessage>(){
			@Override
			protected void channelRead0(ChannelHandlerContext ctx, FullHttpMessage obj) throws Exception {

			}
		};*/

		void init_vars(){			
			bosses = new NioEventLoopGroup();
			worker = new NioEventLoopGroup();
			bs = new ServerBootstrap();
			bs.group(bosses, worker)
				.channel(NioServerSocketChannel.class)
				.childHandler(new ChannelInitializer<SocketChannel>(){
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(new HttpServerCodec());
						ch.pipeline().addLast(new QQHand());
					}
			});
		}

		public void shutdown(){
			updateMessage("關閉網站!!!");
				ch.close();
				cancel();
		}

		@Override
		protected Integer call() throws Exception {
			int ans = 0;	
			try{
				updateMessage("初始化...");
				init_vars();
				updateMessage("工作中...");
				ch = bs.bind(8080).sync()
					.channel();
				ch.closeFuture().sync();
			}catch(InterruptedException e){
				updateMessage(e.getMessage());
				ans= -1;
			}finally{
				bosses.shutdownGracefully();
				worker.shutdownGracefully();
			}
			return ans;
		}
	};
}	
