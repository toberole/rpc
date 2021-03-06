package com.kangyonggan.rpc.handler;

import com.kangyonggan.rpc.constants.TelnetCommand;
import com.kangyonggan.rpc.core.RpcContext;
import com.kangyonggan.rpc.pojo.Refrence;
import com.kangyonggan.rpc.pojo.Service;
import com.kangyonggan.rpc.util.CacheUtil;
import com.kangyonggan.rpc.util.RefrenceUtil;
import com.kangyonggan.rpc.util.ServiceDegradeUtil;
import com.kangyonggan.rpc.util.SpringUtils;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.log4j.Logger;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * @author kangyonggan
 * @since 2019-02-19
 */
public class RpcTelnetHandler extends SimpleChannelInboundHandler<String> {

    private static final String NEW_LINE = "\r\n";

    /**
     * 等待输入
     */
    private String wait4input;

    private static String helpMsg = getHelpResponse();

    private int port;

    private Logger logger = Logger.getLogger(RpcTelnetHandler.class);

    public RpcTelnetHandler(int port) {
        this.port = port;
    }

    /**
     * 创建链接时触发
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        wait4input = RpcContext.getLocalIp() + ":" + port + ">";
        ctx.write(wait4input);
        ctx.flush();
    }

    /**
     * 异常捕获关闭连接
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("异常发生!", cause);
        ctx.close();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, String msg) {
        String response;
        try {
            msg = msg.trim();
            if (StringUtils.isEmpty(msg)) {
                // 空字符, 换行
                response = "";
            } else if (TelnetCommand.EXIT.equals(msg)) {
                // 退出
                ChannelFuture future = ctx.newSucceededFuture();
                future.addListener(ChannelFutureListener.CLOSE);
                return;
            } else if (TelnetCommand.HELP.equals(msg)) {
                // 帮助
                response = helpMsg;
            } else if (TelnetCommand.CLIENT.equals(msg)) {
                // 客户端信息
                response = "应用名称：" + RpcContext.getApplicationName() + NEW_LINE;
                response += "IP地址：" + RpcContext.getLocalIp() + NEW_LINE;
            } else if (TelnetCommand.REFS.equals(msg)) {
                // 引用列表
                List<Refrence> refrences = RefrenceUtil.getAll();
                StringBuilder sb = new StringBuilder();
                response = "Total " + refrences.size() + NEW_LINE;
                for (int i = 0; i < refrences.size(); i++) {
                    sb.append(i).append(") ").append(refrences.get(i).getName()).append(NEW_LINE);
                }
                response += sb.toString();
            } else if (msg.startsWith(TelnetCommand.REF)) {
                // 引用详情
                String name = msg.split("\\s")[1];
                if (!RefrenceUtil.exists(name)) {
                    response = "引用[" + name + "]不存在" + NEW_LINE;
                } else {
                    Refrence refrence = RefrenceUtil.get(name);
                    response = refrence.toString() + NEW_LINE;
                }
            } else if (TelnetCommand.SERVICES.equals(msg)) {
                // 服务列表
                Map<String, Service> services = SpringUtils.getApplicationContext().getBeansOfType(Service.class);
                StringBuilder sb = new StringBuilder();
                int i = 0;
                response = "Total " + services.size() + NEW_LINE;
                for (String key : services.keySet()) {
                    sb.append(i++).append(") ").append(services.get(key).getName()).append(NEW_LINE);
                }
                response += sb.toString();
            } else if (msg.startsWith(TelnetCommand.SERVICE)) {
                // 服务详情
                String name = msg.split("\\s")[1];
                if (!SpringUtils.getApplicationContext().containsBean(name)) {
                    response = "服务[" + name + "]不存在" + NEW_LINE;
                } else {
                    Service service = SpringUtils.getApplicationContext().getBean(name, Service.class);
                    response = service.toString() + NEW_LINE;
                }
            } else if (msg.startsWith(TelnetCommand.CACHE)) {
                // 缓存
                String arg = msg.split("\\s")[1];
                if ("-size".equals(arg)) {
                    // 缓存大小
                    response = CacheUtil.size() + NEW_LINE;
                } else if ("-clear".equals(arg)) {
                    // 清空缓存
                    CacheUtil.clear();
                    response = CacheUtil.size() + NEW_LINE;
                } else {
                    response = "Error argument '" + msg + "' with cache. See 'help'." + NEW_LINE;
                }
            } else if (TelnetCommand.DEGRADES.equals(msg)) {
                // 降级服务列表
                List<String> list = ServiceDegradeUtil.getDegradeList();
                StringBuilder sb = new StringBuilder();
                response = "Total " + list.size() + NEW_LINE;
                for (int i = 0; i < list.size(); i++) {
                    sb.append(i).append(") ").append(list.get(i)).append(NEW_LINE);
                }
                response += sb.toString();
            } else if (msg.startsWith(TelnetCommand.DEGRADE)) {
                String[] arr = msg.split("\\s");
                String type = arr[1];
                if ("-pull".equals(type)) {
                    // 拉取降级服务
                    ServiceDegradeUtil.getDegradeServices();
                    response = "Total " + ServiceDegradeUtil.getDegradeList().size() + NEW_LINE;
                } else if ("-add".equals(type)) {
                    // 添加降级服务
                    ServiceDegradeUtil.add(arr[2]);
                    response = "Total " + ServiceDegradeUtil.getDegradeList().size() + NEW_LINE;
                } else if ("-del".equals(type)) {
                    // 删除降级服务
                    ServiceDegradeUtil.del(arr[2]);
                    response = "Total " + ServiceDegradeUtil.getDegradeList().size() + NEW_LINE;
                } else {
                    response = "Error argument '" + msg + "' with degrade. See 'help'." + NEW_LINE;
                }
            } else {
                response = "Unknown command '" + msg + "'. See 'help'." + NEW_LINE;
            }
        } catch (Exception e) {
            logger.error("telnet命令解析错误", e);
            response = "Error command '" + msg + "'. See 'help'." + NEW_LINE;
        }

        response += wait4input;

        // 写响应
        ctx.write(response);
        ctx.flush();
    }

    private static String getHelpResponse() {
        String response = String.format("%4s%-40s%s", "1. ", "help", "帮助\r\n");
        response += String.format("%4s%-40s%s", "2. ", "exit", "退出\r\n");
        response += String.format("%4s%-40s%s", "3. ", "client", "客户端信息\r\n");
        response += String.format("%4s%-40s%s", "4. ", "refs", "引用列表\r\n");
        response += String.format("%4s%-40s%s", "5. ", "ref [name]", "引用详情\r\n");
        response += String.format("%4s%-40s%s", "6. ", "services", "服务列表\r\n");
        response += String.format("%4s%-40s%s", "7. ", "service [name]", "服务详情\r\n");
        response += String.format("%4s%-40s%s", "8. ", "cache -size", "缓存大小\r\n");
        response += String.format("%4s%-40s%s", "9. ", "cache -clear", "清空缓存\r\n");
        response += String.format("%4s%-40s%s", "10. ", "degrades", "降级服务列表\r\n");
        response += String.format("%4s%-40s%s", "11. ", "degrade -pull", "拉取最新降级服务\r\n");
        response += String.format("%4s%-40s%s", "12. ", "degrade -add [name]", "添加降级服务\r\n");
        response += String.format("%4s%-40s%s", "13. ", "degrade -del [name]", "删除降级服务\r\n");
        return response;
    }
}
