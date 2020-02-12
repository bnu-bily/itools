package com.common.utils;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import com.common.bean.SerializeTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;

/**
 *  HttpUtil based httpcomponet 4.3.x with https support and pooled httpconnection.
 */
public class HttpUtil {
    private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);

    private static final int CON_TIMEOUT = 5000;
    private static final int SO_TIMEOUT = 600000;
    private static final int POOL_TIMEOUT = 3000;
    private static final int HTTP_RETRY_COUNT = 0;
    private static final String NOTICELINE = "--------------------------------------------";
    private static final ContentType MULTIPART_UTF8 = ContentType.create("multipart/form-data", Consts.UTF_8);
    private static CloseableHttpClient httpclient;
    private static Executor executor;

    static {
        init();
        executor = Executor.newInstance(httpclient);
    }


    public static void init() throws RuntimeException {
        try {
            logger.warn(NOTICELINE + " httpUtil init begin " + NOTICELINE);
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(
                    sslContextBuilder.build(), SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory> create()
                    .register("http", new PlainConnectionSocketFactory()).register("https", sslConnectionSocketFactory)
                    .build();

            logger.warn(NOTICELINE + " SSL context init done " + NOTICELINE);

            // init connectionManager , ThreadSafe pooled conMgr
            PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager(
                    registry);
            poolingHttpClientConnectionManager.setMaxTotal(800);
            poolingHttpClientConnectionManager.setDefaultMaxPerRoute(800);
            // init request config. pooltimeout,sotime,contimeout
            RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(POOL_TIMEOUT)
                    .setConnectTimeout(CON_TIMEOUT).setSocketTimeout(SO_TIMEOUT).build();
            // begin construct httpclient
            HttpClientBuilder httpClientBuilder = HttpClients.custom();
            httpClientBuilder.setConnectionManager(poolingHttpClientConnectionManager);
            httpClientBuilder.setDefaultRequestConfig(requestConfig);
            httpClientBuilder.setRetryHandler(new HttpRequestRetryHandler() {
                @Override
                public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                    if (executionCount >= HTTP_RETRY_COUNT) {
                        return false;
                    }
                    if (exception instanceof InterruptedIOException) {
                        // Timeout
                        logger.warn("httputil retry for InterruptIOException");
                        return true;
                    }
                    if (exception instanceof UnknownHostException) {
                        // Unknown host
                        return false;
                    }
                    if (exception instanceof ConnectTimeoutException) {
                        // Connection refused
                        logger.warn("httputil retry for ConnectTimeoutException");
                        return true;
                    }
                    if (exception instanceof SSLException) {
                        // SSL handshake exception
                        return false;
                    }
                    HttpClientContext clientContext = HttpClientContext.adapt(context);
                    HttpRequest request = clientContext.getRequest();
                    boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
                    if (idempotent) {
                        // Retry if the request is considered idempotent
                        logger.warn("httputil retry for idempotent");
                        return true;
                    }
                    return false;
                }
            });
            logger.warn(NOTICELINE + " poolManager , requestconfig init done " + NOTICELINE);

            httpclient = httpClientBuilder.build();
            logger.warn(NOTICELINE + " httpUtil4 init done " + NOTICELINE);
        } catch (Exception e) {
            logger.error(NOTICELINE + "httpclient init fail" + NOTICELINE, e);
            throw new RuntimeException(e);
        }
    }

    public static CloseableHttpClient getHttpclient() {
        if (null == httpclient) {
            init();
        }
        return httpclient;
    }

    public static String get(String url, List<Header> headerList) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        String resp = null;
        try {
            Request request = Request.Get(url);
            headerList.forEach(request::addHeader);
            resp = executor.execute(request).returnContent().asString(StandardCharsets.UTF_8);
            return resp;
        } catch (Exception e) {
            logger.error("HttpUtil get error", e);
            throw new RuntimeException("请求异常");
        } finally {
            logger.info("do http get, url: {}, param: {}, resp: {}, cost: {}", url, JacksonUtil.serialize(headerList), resp, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    public static String post(String url, List<Header> headerList) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        String resp = null;
        try {
            Request request = Request.Post(url);
            headerList.forEach(request::addHeader);
            resp = executor.execute(request).returnContent().asString(StandardCharsets.UTF_8);
            return resp;
        } catch (Exception e) {
            logger.error("HttpUtil get error", e);
            throw new RuntimeException("请求异常");
        } finally {
            logger.info("do http post, url: {}, param: {}, resp: {}, cost: {}", url, JacksonUtil.serialize(headerList), resp, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    public static String get(String url, Form form) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<NameValuePair> pairs = form.build();
        String resp = null;
        try {
            String param = Joiner.on("&").join(pairs.stream().map(i-> i.getName() + "=" + i.getValue()).collect(Collectors.toList()));
            Request request = Request.Get(url + "?" + param);
            resp = executor.execute(request).returnContent().asString(StandardCharsets.UTF_8);
            return resp;
        } catch (Exception e) {
            logger.error("HttpUtil get error", e);
            throw new RuntimeException("请求异常");
        } finally {
            logger.info("do http get, url: {}, param: {}, resp: {}, cost: {}", url, JacksonUtil.serialize(pairs), resp, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    public static String get(String url) {
        try {
            Request request = Request.Get(url);
            return executor.execute(request).returnContent().asString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("HttpUtil get error", e);
            throw new RuntimeException("请求异常");
        }
    }

    public static String post(String url, String json) {
        try {
            Request request = Request.Post(url).bodyString(json, ContentType.APPLICATION_JSON);
            return executor.execute(request).returnContent().asString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("HttpUtil post error", e);
            throw new RuntimeException("请求异常");
        }
    }

    /**
     *
     * @param url
     * @param form = Form.form().add("key1","val1").add("key2","val2");
     * @return
     */
    public static String post(String url, Form form, Boolean needRespLog) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<NameValuePair> pairs = form.build();
        String resp = null;
        try {
            Request request = Request.Post(url).bodyForm(pairs, Consts.UTF_8);
            resp = executor.execute(request).returnContent().asString(StandardCharsets.UTF_8);
            return resp;
        } catch (Exception e) {
            logger.error("HttpUtil post error", e);
            throw new RuntimeException("请求异常");
        } finally {
            if (needRespLog){
                logger.info("do http post, url: {}, param: {}, resp: {}, cost: {}", url, JacksonUtil.serialize(pairs), resp, stopwatch.elapsed(TimeUnit.MILLISECONDS));
            }else {
                if(logger.isDebugEnabled()){
                    logger.debug("do http post, url: {}, param: {}, resp: {}, cost: {}", url, JacksonUtil.serialize(pairs), resp, stopwatch.elapsed(TimeUnit.MILLISECONDS));
                }else {
                    logger.info("do http post, url: {}, param: {}, cost: {}", url, JacksonUtil.serialize(pairs), stopwatch.elapsed(TimeUnit.MILLISECONDS));
                }
            }
        }
    }

    /**
     *
     * @param url
     * @param form = Form.form().add("key1","val1").add("key2","val2");
     * @return
     */
    public static String post(String url, Form form, Form formSensitive) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<NameValuePair> pairs = form.build();
        List<NameValuePair> pairsSensitive = formSensitive.build();
        String resp = null;
        try {
            List<NameValuePair> all = Lists.newArrayList(pairs);
            all.addAll(pairsSensitive);
            Request request = Request.Post(url).bodyForm(all, Consts.UTF_8);
            resp = executor.execute(request).returnContent().asString(StandardCharsets.UTF_8);
            return resp;
        } catch (Exception e) {
            logger.error("HttpUtil post error", e);
            throw new RuntimeException("请求异常");
        } finally {
            logger.info("do http post, url: {}, param: {}, resp: {}, cost: {}", url, JacksonUtil.serialize(pairs), resp, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    public static String post(String url, File file) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        String path = null;
        String resp = null;
        try {
            path = file.getAbsolutePath();

            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                    .setCharset(Charset.forName("utf-8")).addBinaryBody("file", file).build();

            Request request = Request.Post(url).body(entity);
            resp = executor.execute(request).returnContent().asString(StandardCharsets.UTF_8);
            return resp;
        } catch (Exception e) {
            logger.error("HttpUtil post error", e);
            throw new RuntimeException("请求异常");
        } finally {
            logger.info("do http post, url: {}, file: {}, resp: {}, cost: {}", url, path, resp, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    public static String postMultiPart(String url, Form form) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<NameValuePair> pairs = form.build();
        String resp = null;
        try {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create().setMode(HttpMultipartMode.BROWSER_COMPATIBLE).setCharset(Charset.forName("utf-8"));

            for (NameValuePair pair : pairs) {
                builder = builder.addTextBody(pair.getName(), pair.getValue(), MULTIPART_UTF8);
            }

            HttpEntity entity = builder.build();

            Request request = Request.Post(url).body(entity);
            resp = executor.execute(request).returnContent().asString(StandardCharsets.UTF_8);
            return resp;
        } catch (Exception e) {
            logger.error("HttpUtil post multi part error", e);
            throw new RuntimeException("请求异常");
        } finally {
            logger.info("do http post multi part, url: {}, param: {}, resp: {}, cost: {}", url, JacksonUtil.serialize(pairs), resp, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    public static void main(String []args){

        String url = "httpTest";
        Form form = Form.form().add("data", JacksonUtil.serialize(new SerializeTest()));
        Form formSensitive = Form.form().add("sensitive", JacksonUtil.serialize(new SerializeTest()));
        String resp = HttpUtil.post(url, form, formSensitive);
        SerializeTest tcResult = JacksonUtil.deSerialize(resp, new TypeReference<SerializeTest>() {
        });
    }
}

