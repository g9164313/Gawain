package narl.itrc;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import com.sun.glass.ui.Application;
import com.sun.xml.internal.ws.client.sei.ResponseBuilder.Header;

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
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class PanBrownie extends PanBase {
	
	public static Optional<PanBrownie> self = Optional.empty();

	private Optional<TaskBootstrap> opt_stub = Optional.empty();
	
	final Label[] info = {
		new Label("[IP:address]"), 
		new Label("[TXT:status]")
	};

	public PanBrownie(Stage stg){
		super(stg);
		if(self.isPresent()==true){
			self.get().stage().close();
		}
		self = Optional.of(this);
		stage().setOnShown(e->event_link_http());
		stage().setOnCloseRequest(e->{
			event_shutdown();
			self = Optional.empty();
		});
	}

	private void event_link_http(){
		if(opt_stub.isPresent()==true){
			return;
		}
		//final EventHandler<WorkerStateEvent> end_of_task = hh->{
		//	opt_stub=Optional.empty();
		//	info.textProperty().unbind();
		//};
		final TaskBootstrap tsk = new TaskBootstrap();			
		info[1].textProperty().bind(tsk.messageProperty());
		tsk.setOnFailed(e->{
			info[1].textProperty().unbind();
			info[1].setText("Failed!!");
			opt_stub = Optional.empty();
		});
		//tsk.setOnCancelled(end_of_task);
		//tsk.setOnSucceeded(end_of_task);		
		Thread th = new Thread(tsk,"Http-MainServer");
		th.setDaemon(true);
		th.start();
		opt_stub = Optional.of(tsk);
	}

	private void event_shutdown(){
		if(opt_stub.isPresent()==false){
			return;
		}
		info[1].textProperty().unbind();
		opt_stub.get().shutdown();
		opt_stub = Optional.empty();
	}

	@Override
	public Node eventLayout(PanBase self) {
		//final JFXButton btn_link = new JFXButton();
		//btn_link.getStyleClass().add("btn-raised-1");
		for(Label txt:info){
			txt.setMinWidth(300);
			//txt.getStyleClass().add("font-size3");
		}
		final VBox lay1 = new VBox(info);
		lay1.getStyleClass().addAll("box-pad");
		return lay1;
	}
	//---------------------------------------

	private class TaskBootstrap extends Task<Integer> {
		
		EventLoopGroup bosses, worker;
		ServerBootstrap bs;
		Channel ch;

		private void gen_preflight_default(ChannelHandlerContext ctx){
			final DefaultFullHttpResponse resp = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1,	
				HttpResponseStatus.NO_CONTENT
			);
			resp.headers()
				.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
				.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS")
				.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type")
				.set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, 5);
			ctx.writeAndFlush(resp);
		}

		private void gen_response_forbidden(ChannelHandlerContext ctx){
			DefaultFullHttpResponse resp = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1,	
				HttpResponseStatus.FORBIDDEN
			);
			resp.headers()
				.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
				.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
			ctx.writeAndFlush(resp);
		}
		private void gen_response_okay(ChannelHandlerContext ctx){
			DefaultFullHttpResponse resp = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1,	
				HttpResponseStatus.OK
			);
			resp.headers()
				.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
				.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
			ctx.writeAndFlush(resp);
		}

		private void gen_response_text(ChannelHandlerContext ctx, String txt){
			DefaultFullHttpResponse resp = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1,	
				HttpResponseStatus.OK,
				Unpooled.wrappedBuffer(txt.getBytes(CharsetUtil.UTF_8))
			);
			resp.headers()
				.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
				.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
				.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
				.set(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
			ctx.writeAndFlush(resp);
		}

		private void gen_response_json(ChannelHandlerContext ctx,String text){			
			DefaultFullHttpResponse resp = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1,	
				HttpResponseStatus.OK,
				Unpooled.wrappedBuffer(text.getBytes(CharsetUtil.UTF_8))
			);
			resp.headers()
				.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
				.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
				.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
				.set(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
			ctx.writeAndFlush(resp);
		}

		String api_txt= "";
		PropBundle api_bnd = null;

		private class Hand1 extends SimpleChannelInboundHandler<FullHttpRequest>{
			@Override
			protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest obj) throws Exception {
				final String path = obj.uri();
				final HttpMethod method = obj.method();
				final String ctxt = obj.content().toString(CharsetUtil.UTF_8);

				System.out.printf("%s, path:%s, context:%s\n", method.toString(), path, ctxt);

				if(HttpMethod.GET.equals(method)){
					if(
						path.equals("/api")==true || 
						path.equals("/api.json")==true
					){
						gen_response_json(ctx,api_txt); return;
					}else if(path.equals("/api/status")==true){
						//list JavaFX property
						if(api_bnd!=null){
							gen_response_json(ctx, api_bnd.reflect_json("")); return;
						}
					}
					//else if(path.equals("/favicon.ico")==true){
						//瀏覽器會噴出這要求～
					//}
				}else if(HttpMethod.POST.equals(method)){
					//update JavaFX property
					if(path.equals("/api/touch")==true){
						//touch JavaFX property
						if(api_bnd!=null){
							gen_response_json(ctx, api_bnd.reflect_json(ctxt)); return;
						}
					}/*else if(path.equals("/api/hold_on")==true){
					}else if(path.equals("/api/ladder/run")==true){
					}else if(path.equals("/api/ladder/pause")==true){
					}else if(path.equals("/api/ladder/stop")==true){
					}*/
				}else if(HttpMethod.OPTIONS.equals(method)==true){
					//CORS preflight!!!
					gen_preflight_default(ctx); return;	
				}
				//gen_response_forbidden(ctx);
				gen_response_okay(ctx);
			}
		};

		void init_http_vars(){			
			bosses = new NioEventLoopGroup();
			worker = new NioEventLoopGroup();
			bs = new ServerBootstrap();
			bs.group(bosses, worker)
				.channel(NioServerSocketChannel.class)
				.childHandler(new ChannelInitializer<SocketChannel>(){
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(new HttpServerCodec());
						ch.pipeline().addLast(new HttpObjectAggregator(64*1024));
						ch.pipeline().addLast(new Hand1());
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
			final String txt_url, txt_ip;
			try{
				updateMessage("讀取本機IP位置...");				
				try(final DatagramSocket socket = new DatagramSocket()){
					socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
					txt_ip = socket.getLocalAddress().getHostAddress();
					txt_url= "http://"+txt_ip+":8080";
					Application.invokeLater(()->info[0].setText(txt_url));
				}
				updateMessage("準備應對模型...");
				Object[] obj = Gawain.mainPanel.getOpenAPI();
				api_txt = (String)obj[0];
				api_txt = api_txt.replace("$HOST_NAME", txt_url);
				api_bnd = (PropBundle)obj[1];
				
				updateMessage("初始化...");
				init_http_vars();
				updateMessage("工作中...");
				ch = bs.bind(8080).sync().channel();
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
