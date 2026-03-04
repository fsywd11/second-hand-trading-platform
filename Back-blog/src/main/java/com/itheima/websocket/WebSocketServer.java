package com.itheima.websocket;

import com.alibaba.fastjson.JSON;
import com.itheima.pojo.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket服务端（生产级：补充重连、心跳、并发安全）
 */
@ServerEndpoint("/ws/chat/{userId}")
@Component
@Slf4j
public class WebSocketServer {
    // 在线人数统计（原子类保证并发安全）
    private static final AtomicInteger ONLINE_COUNT = new AtomicInteger(0);
    // 保存所有在线连接：key=用户ID，value=会话对象
    private static final ConcurrentHashMap<String, Session> ONLINE_USERS = new ConcurrentHashMap<>();

    /**
     * 连接建立成功调用（三次握手完成后触发）
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        // 并发安全地添加会话
        ONLINE_USERS.putIfAbsent(userId, session);
        // 在线人数+1
        int count = ONLINE_COUNT.incrementAndGet();
        log.info("用户{}建立WebSocket连接，当前在线人数：{}", userId, count);

        // 可选：发送心跳检测（防止连接超时）
        sendHeartbeat(session, userId);
    }

    /**
     * 连接关闭调用（四次挥手完成后触发）
     */
    @OnClose
    public void onClose(@PathParam("userId") String userId) {
        // 移除会话
        ONLINE_USERS.remove(userId);
        // 在线人数-1
        int count = ONLINE_COUNT.decrementAndGet();
        log.info("用户{}关闭WebSocket连接，当前在线人数：{}", userId, count);
    }

    /**
     * 收到客户端消息调用（前端主动发消息时）
     * 扩展：支持前端直接通过WebSocket发消息，无需走HTTP接口
     */
    @OnMessage
    public void onMessage(String message, Session session, @PathParam("userId") String senderId) {
        log.info("收到用户{}的消息：{}", senderId, message);
        try {
            // 解析前端发送的消息（格式：{receiverId: 200, content: "你好"}）
            ChatMessage msg = JSON.parseObject(message, ChatMessage.class);
            // 转发给接收者
            sendToUser(msg.getReceiverId().toString(), msg);
        } catch (Exception e) {
            log.error("转发客户端消息失败：", e);
        }
    }

    /**
     * 发生错误调用
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket连接错误：", error);
        // 错误时主动关闭连接
        if (session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.error("关闭错误连接失败：", e);
            }
        }
    }

    /**
     * 发送消息给指定用户（核心：推送给接收者）
     */
    public void sendToUser(String userId, ChatMessage message) {
        Session session = ONLINE_USERS.get(userId);
        if (session == null) {
            log.warn("用户{}未在线，无法推送消息", userId);
            // 可选：离线消息存储（存入数据库，用户上线后推送）
            return;
        }
        if (!session.isOpen()) {
            log.warn("用户{}的WebSocket连接已关闭，移除会话", userId);
            ONLINE_USERS.remove(userId);
            ONLINE_COUNT.decrementAndGet();
            return;
        }
        try {
            // 线程安全地发送消息
            synchronized (session) {
                session.getBasicRemote().sendText(JSON.toJSONString(message));
            }
            log.info("成功推送消息给用户{}：{}", userId, message.getContent());
        } catch (Exception e) {
            log.error("推送消息给用户{}失败：", userId, e);
        }
    }

    /**
     * 心跳检测（防止Nginx/网关断开空闲连接）
     */
    private void sendHeartbeat(Session session, String userId) {
        new Thread(() -> {
            while (session.isOpen()) {
                try {
                    // 每30秒发送一次心跳包
                    Thread.sleep(30000);
                    synchronized (session) {
                        session.getBasicRemote().sendText(JSON.toJSONString(new ChatMessage() {{
                            setMsgType(0); // 0-心跳包
                            setContent("ping");
                        }}));
                    }
                } catch (Exception e) {
                    log.error("给用户{}发送心跳包失败：", userId, e);
                    break;
                }
            }
        }).start();
    }

    /**
     * 广播消息（可选：如系统通知）
     */
    public void broadcast(ChatMessage message) {
        String jsonMsg = JSON.toJSONString(message);
        ONLINE_USERS.forEach((userId, session) -> {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.getBasicRemote().sendText(jsonMsg);
                    }
                } catch (Exception e) {
                    log.error("广播消息给用户{}失败：", userId, e);
                }
            }
        });
    }
}