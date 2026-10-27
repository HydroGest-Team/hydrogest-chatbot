package com.hydrogest.chatbot.api;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.hydrogest.chatbot.config.ChatbotProperties;

@Service
public class RateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ChatbotProperties properties;
    private final ConcurrentMap<String, Deque<Long>> counters = new ConcurrentHashMap<>();

    public RateLimitService(ChatbotProperties properties) {
        this.properties = properties;
    }

    public synchronized boolean permitir(String sessionId, String ip) {
        long ahora = System.currentTimeMillis();
        boolean permitidoSesion = registrar("session:" + sessionId, ahora,
                properties.getRateLimitPerSession());
        if (!permitidoSesion) {
            return false;
        }
        if (registrar("ip:" + ip, ahora, properties.getRateLimitPerIp())) {
            return true;
        }
        retirarUltimaMarca("session:" + sessionId);
        return false;
    }

    private boolean registrar(String clave, long ahora, int limite) {
        Deque<Long> marcas = counters.computeIfAbsent(clave, ignored -> new ArrayDeque<>());
        synchronized (marcas) {
            long inicio = ahora - WINDOW.toMillis();
            while (!marcas.isEmpty() && marcas.peekFirst() <= inicio) {
                marcas.removeFirst();
            }
            if (marcas.size() >= limite) {
                return false;
            }
            marcas.addLast(ahora);
            return true;
        }
    }

    private void retirarUltimaMarca(String clave) {
        Deque<Long> marcas = counters.get(clave);
        if (marcas == null) {
            return;
        }
        synchronized (marcas) {
            if (!marcas.isEmpty()) {
                marcas.removeLast();
            }
            if (marcas.isEmpty()) {
                counters.remove(clave, marcas);
            }
        }
    }
}
