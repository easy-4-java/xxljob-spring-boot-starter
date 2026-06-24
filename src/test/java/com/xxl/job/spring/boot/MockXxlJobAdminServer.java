package com.xxl.job.spring.boot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模拟 xxl-job-admin v3 API 的嵌入式 HTTP 服务器，用于集成测试。
 * 有状态模拟：记录创建的执行器和任务，使自动注册流程能完整走通。
 */
public class MockXxlJobAdminServer implements AutoCloseable {

    private final HttpServer server;
    private final int port;
    private final AtomicInteger nextGroupId = new AtomicInteger(1);
    private final AtomicInteger nextJobId = new AtomicInteger(1);
    private final List<XxlJobRequestLog> requestLog = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> groups = new ConcurrentHashMap<>();
    private final Map<String, Integer> jobs = new ConcurrentHashMap<>();

    public MockXxlJobAdminServer() throws IOException { this(0); }

    public MockXxlJobAdminServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.port = server.getAddress().getPort();
        server.createContext("/xxl-job-admin/auth/doLogin", new LoginHandler());
        server.createContext("/xxl-job-admin/auth/logout", new JsonHandler("{\"code\":200,\"msg\":\"ok\"}"));
        server.createContext("/xxl-job-admin/jobgroup/pageList", new JobGroupPageListHandler());
        server.createContext("/xxl-job-admin/jobgroup/insert", new JobGroupInsertHandler());
        server.createContext("/xxl-job-admin/jobgroup/loadById", new JsonHandler("{\"code\":200,\"msg\":\"ok\",\"data\":{\"id\":1,\"appname\":\"xxl-job-test-executor\",\"title\":\"test\",\"addressType\":0}}"));
        server.createContext("/xxl-job-admin/jobgroup/update", new JsonHandler("{\"code\":200,\"msg\":\"ok\"}"));
        server.createContext("/xxl-job-admin/jobgroup/delete", new JsonHandler("{\"code\":200,\"msg\":\"ok\"}"));
        server.createContext("/xxl-job-admin/jobinfo/pageList", new JobInfoPageListHandler());
        server.createContext("/xxl-job-admin/jobinfo/insert", new JobInfoInsertHandler());
        server.createContext("/xxl-job-admin/jobinfo/update", new JsonHandler("{\"code\":200,\"msg\":\"ok\"}"));
        server.createContext("/xxl-job-admin/jobinfo/delete", new JsonHandler("{\"code\":200,\"msg\":\"ok\"}"));
        server.createContext("/xxl-job-admin/jobinfo/start", new JobInfoStartStopHandler());
        server.createContext("/xxl-job-admin/jobinfo/stop", new JobInfoStartStopHandler());
        server.createContext("/xxl-job-admin/jobinfo/trigger", new JsonHandler("{\"code\":200,\"msg\":\"ok\"}"));
        server.setExecutor(null);
    }

    public void start() { server.start(); }
    public int getPort() { return port; }
    public String getBaseUrl() { return "http://localhost:" + port + "/xxl-job-admin"; }
    public List<XxlJobRequestLog> getRequestLog() { return new ArrayList<>(requestLog); }

    @Override
    public void close() { server.stop(0); }

    // ---- 请求日志 ----

    public static class XxlJobRequestLog {
        public final String path;
        public final Map<String, String> params;
        XxlJobRequestLog(String path, Map<String, String> params) { this.path = path; this.params = params; }
        public boolean hasParam(String key) { return params.containsKey(key); }
        public String param(String key) { return params.get(key); }
        @Override public String toString() { return "POST " + path + " " + params; }
    }

    // ---- 工具 ----

    private Map<String, String> parseFormBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> map = new LinkedHashMap<>();
            if (body.isEmpty()) return map;
            for (String pair : body.split("&")) {
                int idx = pair.indexOf("=");
                if (idx > 0) map.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
            return map;
        }
    }

    private void sendJson(HttpExchange exchange, int httpCode, String jsonBody) throws IOException {
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(httpCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void sendHtml400(HttpExchange exchange, String msg) throws IOException {
        String html = "<!DOCTYPE html><html><head><title>Error</title></head><body>" + msg + "</body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html;charset=UTF-8");
        exchange.sendResponseHeaders(400, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    // 生成 V3 pageList JSON（PageModel: total + data）
    private String v3PageListJson(List<String> itemJsons) {
        String items = String.join(",", itemJsons);
        return "{\"code\":200,\"msg\":null,\"data\":{\"total\":" + itemJsons.size()
                + ",\"data\":[" + items + "]}}";
    }

    // 生成 V2 pageList JSON（DataTables: recordsTotal/recordsFiltered/data）
    private String v2PageListJson(List<String> itemJsons) {
        String items = String.join(",", itemJsons);
        return "{\"code\":200,\"msg\":null,\"data\":{\"recordsTotal\":" + itemJsons.size()
                + ",\"recordsFiltered\":" + itemJsons.size()
                + ",\"pages\":" + (itemJsons.isEmpty() ? 0 : 1)
                + ",\"data\":[" + items + "]}}";
    }

    // ---- Handlers ----

    class JsonHandler implements HttpHandler {
        private final String json;
        JsonHandler(String json) { this.json = json; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseFormBody(exchange);
            requestLog.add(new XxlJobRequestLog(exchange.getRequestURI().getPath(), params));
            sendJson(exchange, 200, json);
        }
    }

    class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseFormBody(exchange);
            requestLog.add(new XxlJobRequestLog(exchange.getRequestURI().getPath(), params));
            exchange.getResponseHeaders().add("Set-Cookie", "XXL_JOB_LOGIN_IDENTITY=mock-identity");
            exchange.getResponseHeaders().add("Set-Cookie", "xxl_job_login_token=mock-token-" + UUID.randomUUID().toString().substring(0, 8));
            sendJson(exchange, 200, "{\"code\":200,\"msg\":\"ok\"}");
        }
    }

    class JobGroupPageListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseFormBody(exchange);
            requestLog.add(new XxlJobRequestLog(exchange.getRequestURI().getPath(), params));

            String appname = params.get("appname");
            List<String> items = new ArrayList<>();
            for (Map.Entry<String, Integer> e : groups.entrySet()) {
                if (appname == null || appname.isEmpty() || e.getKey().equals(appname)) {
                    items.add("{\"id\":" + e.getValue()
                            + ",\"appname\":\"" + e.getKey() + "\""
                            + ",\"title\":\"" + params.getOrDefault("title", "") + "\""
                            + ",\"addressType\":0}");
                }
            }
            sendJson(exchange, 200, v3PageListJson(items));
        }
    }

    class JobGroupInsertHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseFormBody(exchange);
            requestLog.add(new XxlJobRequestLog(exchange.getRequestURI().getPath(), params));
            int id = nextGroupId.getAndIncrement();
            String appname = params.get("appname");
            if (appname != null && !appname.isEmpty()) groups.put(appname, id);
            sendJson(exchange, 200, "{\"code\":200,\"msg\":\"ok\",\"content\":\"" + id + "\"}");
        }
    }

    class JobInfoPageListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseFormBody(exchange);
            requestLog.add(new XxlJobRequestLog(exchange.getRequestURI().getPath(), params));

            List<String> items = new ArrayList<>();
            for (Map.Entry<String, Integer> e : jobs.entrySet()) {
                items.add("{\"id\":" + e.getValue()
                        + ",\"executorHandler\":\"" + e.getKey() + "\""
                        + ",\"jobGroup\":0,\"jobDesc\":\"\"}");
            }
            sendJson(exchange, 200, v3PageListJson(items));
        }
    }

    class JobInfoInsertHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseFormBody(exchange);
            requestLog.add(new XxlJobRequestLog(exchange.getRequestURI().getPath(), params));
            int id = nextJobId.getAndIncrement();
            String handler = params.get("executorHandler");
            if (handler != null && !handler.isEmpty()) jobs.put(handler, id);
            sendJson(exchange, 200, "{\"code\":200,\"msg\":\"ok\",\"content\":\"" + id + "\"}");
        }
    }

    class JobInfoStartStopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseFormBody(exchange);
            requestLog.add(new XxlJobRequestLog(exchange.getRequestURI().getPath(), params));
            // v3 admin 的 start/stop 接受 ids[] 参数
            if (!params.containsKey("ids[]")) {
                sendHtml400(exchange, "Missing 'ids[]' parameter");
                return;
            }
            sendJson(exchange, 200, "{\"code\":200,\"msg\":\"ok\"}");
        }
    }
}
